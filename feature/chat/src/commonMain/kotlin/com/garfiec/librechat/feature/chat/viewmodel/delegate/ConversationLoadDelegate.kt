package com.garfiec.librechat.feature.chat.viewmodel.delegate

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.common.result.getOrNull
import com.garfiec.librechat.core.data.repository.ConversationRepository
import com.garfiec.librechat.core.data.repository.DraftRepository
import com.garfiec.librechat.core.data.repository.MessageRepository
import com.garfiec.librechat.core.logging.Diag
import com.garfiec.librechat.core.logging.LogOrigin
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.feature.chat.util.MessageNode
import com.garfiec.librechat.feature.chat.util.NEW_CHAT_DRAFT_KEY
import com.garfiec.librechat.feature.chat.util.buildActiveMessagePath
import com.garfiec.librechat.feature.chat.util.hasParallelParts
import com.garfiec.librechat.feature.chat.util.stabilizeMessageInstances
import com.garfiec.librechat.feature.chat.viewmodel.ChatScreenState
import com.garfiec.librechat.feature.chat.viewmodel.ConversationLoadHandle
import com.garfiec.librechat.feature.chat.viewmodel.isServerCopyOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Brings a conversation onto the screen: subscribes the Room read-through, revalidates it from
 * the server, keeps a handed-off chat's seeded user message visible until the server's copy
 * lands, and reconciles a failed turn's text back into the composer. Also the conversation
 * model load and the draft restore that an open performs, and the explicit pull-to-refresh.
 *
 * `ChatViewModel.init` seeds the handoff and decides `cacheFirst`; the send, streaming and
 * completion paths reload through [loadConversation] when the server holds a turn the cache
 * does not (see feature/chat/CLAUDE.md, "Cache-first open").
 */
class ConversationLoadDelegate(
    private val handle: ConversationLoadHandle,
    private val messageRepository: MessageRepository,
    private val conversationRepository: ConversationRepository,
    private val draftRepository: DraftRepository,
    private val defaultDispatcher: CoroutineDispatcher,
    /** ModelSelectionDelegate.applyConversationModel: seeds the selection from a fetched conversation. */
    private val applyConversationModel: (Conversation) -> Boolean,
    /**
     * Marks the conversation-model load attempted (never "resolved") and refilters the model list;
     * see ModelSelectionDelegate.conversationModelLoaded. Runs on every path out of
     * [loadConversationModel], including the temp-chat short-circuit and the not-readable 404.
     */
    private val onModelLoadSettled: () -> Unit,
    /** ComparisonModeDelegate.rehydrateFromMessage, for a reopened Compare Models conversation. */
    private val rehydrateComparison: (Message) -> Unit,
) {
    private var roomObserverJob: Job? = null

    fun stopObserving() {
        roomObserverJob?.cancel()
        roomObserverJob = null
    }

    /**
     * Fetches the conversation's messages and records the outcome.
     *
     * `getMessages` is `safeApiCall`-wrapped: it reports failure by RETURNING [Result.Error] and
     * lets only `CancellationException` propagate, so the result must be consumed — a `try/catch`
     * around it can never see a network failure. An `Error` also means the cache was empty: the
     * repository falls back to cached rows and returns those as `Success`.
     */
    private suspend fun revalidateMessages(conversationId: String) {
        when (val result = messageRepository.getMessages(conversationId)) {
            is Result.Error -> {
                Logger.e(result.exception) { "Failed to fetch messages for $conversationId" }
                handle.update {
                    // Only report when the failure actually leaves the screen empty. A revalidate
                    // that fails over cached rows is the ordinary offline case, and a handed-off
                    // new chat streams with just its seeded user message while the server persists
                    // the request only on completion — this fetch is *expected* to fail there.
                    if (content.isStreaming || content.displayMessages.isNotEmpty()) return@update
                    // App-authored copy only — Ktor builds exception messages out of
                    // the request URL, so result.message can leak an access gateway's
                    // redirect JWT on screen (#287).
                    error = "Could not load messages"
                    content = content.copy(
                        screenState = ChatScreenState.ACTIVE,
                        messagesLoadFailed = true,
                    )
                }
            }
            else -> handle.update {
                content = content.copy(messagesLoadFailed = false)
            }
        }
    }

    /**
     * Subscribes the Room read-through for [conversationId] and revalidates it from the server.
     *
     * [cacheFirst] picks the ordering. `false` (the default) awaits the fetch before subscribing,
     * so the first emission is the authoritative one. Every other caller reloads precisely because
     * the server holds something the cache does not — a just-finalized turn, a just-created branch,
     * a stream that ended server-side, or an explicit refresh — so a cache emission there serves a
     * snapshot that predates the thing being fetched. After a Final that is also the completion
     * flash: the finalized turn is in memory and Room stays stale until `cacheMessages` lands, so
     * painting the cache would re-render the pre-Final tree. `true` subscribes first and revalidates
     * in the background; `init` is the only opt-in (#300).
     *
     * [unsavedTurn] is a failed turn's optimistic user message whose persistence is unknown: once
     * the fetch settles, its text goes back into the composer if the server kept no copy of it.
     */
    fun loadConversation(
        conversationId: String,
        cacheFirst: Boolean = false,
        unsavedTurn: Message? = null,
    ) {
        // SECURITY: do not remove — temp-chat data-at-rest guard.
        // Defense-in-depth for temporary chats: never route a temp conversation
        // through the Room read-through, which would upsert its message rows to disk (the
        // convo is hidden from history but the text would persist). The temp chat's
        // display is finalized in memory by finalizeChatDisplay; any stray
        // loadConversation call (safety-net, error/abort paths) must not touch the DB.
        if (handle.state.isTemporaryChat) return
        // Cancel any previous Room observer to avoid duplicate collectors
        roomObserverJob?.cancel()
        // Latch so comparison auto-rehydration runs at most once per load — on the first
        // non-empty emission (the authoritative tail) — so a later Room re-emit can't
        // re-enable comparison after the user has toggled it off for the session.
        var autoRehydrateHandled = false
        var pendingUnsavedTurn = unsavedTurn
        roomObserverJob = handle.scope.launch {
            // A flow, not a plain flag: it is a `combine` input below, so settling re-runs the
            // transform even when Room never emits again — a conversation with genuinely zero
            // messages upserts nothing, and an empty cache offline emits `[]` once. Read as a
            // flag inside `collect`, both spin forever.
            val revalidated = MutableStateFlow(false)
            if (cacheFirst) {
                // A child of roomObserverJob, so re-entering loadConversation cancels it with the
                // observer.
                launch {
                    handle.update { content = content.copy(isRefreshingMessages = true) }
                    try {
                        revalidateMessages(conversationId)
                    } finally {
                        handle.update { content = content.copy(isRefreshingMessages = false) }
                    }
                    revalidated.value = true
                }
            } else {
                revalidateMessages(conversationId)
                revalidated.value = true
            }
            // buildActiveMessagePath is pure/synchronous CPU work; computing it on the Default
            // dispatcher keeps the tree walk off Main. Combining the active-branch selection in
            // (rather than peeking handle.state inside the map) keeps the branch snapshot
            // consistent with the emission — no torn read — and means switchBranch only has to
            // mutate activeBranches: the heavy recompute happens here off Main, not on the click
            // thread. The result feeds a StateFlow (not a Compose snapshot), so it's safe off-Main.
            combine(
                messageRepository.observeMessages(conversationId),
                handle.stateFlow.map { it.activeBranches }.distinctUntilChanged(),
                revalidated,
            ) { messages, branches, settled ->
                // Reuse on-screen Message instances that changed only in volatile fields, so
                // the rebuilt path stays value-equal and the cosmetic Room reconcile conflates
                // instead of re-rendering the list (see [stabilizeMessageInstances]). The
                // baseline is read straight from handle.state, not a combine input: the
                // completion path writes finalized messages into the state *outside* this flow
                // (finalizeChatDisplay) and happens-before the cacheMessages write that
                // triggers this emission, so handle.state reflects the true on-screen state.
                val baseline = handle.state
                val stabilized = stabilizeMessageInstances(messages, baseline.messages)
                // A handed-off new chat seeds the just-sent user message (pendingResumeUserMessage):
                // the server persists the request only when the reply completes, so the Room read is
                // empty mid-stream and the user's message would otherwise vanish for the whole stream.
                // Keep that seed appended until the server's own copy arrives, then drop it. The copy
                // is matched by content as well as id (isServerCopyOf): rc3+ re-mints the id, so an
                // id-only match never fires, and a run ending with no Final (a resume that 404s) would
                // leave the seed as a newer root sibling that hides the persisted turn. finalizeChatDisplay
                // also clears the seed at Final. Done here, off Main, so the path build stays on the
                // Default dispatcher. The takeIf guarantees no copy of the seed is in stabilized, so
                // this is a plain append — no by-id reconcile needed.
                val pending = baseline.pendingResumeUserMessage
                val retainedPending = pending?.takeIf { seed ->
                    stabilized.none { it.messageId == seed.messageId || it.isServerCopyOf(seed) }
                }
                val merged = retainedPending?.let { stabilized + it } ?: stabilized
                MessagePathEmission(
                    messages = merged,
                    displayMessages = buildActiveMessagePath(merged, branches),
                    retainedPending = retainedPending,
                    revalidated = settled,
                )
            }
                .flowOn(defaultDispatcher)
                .collect { emission ->
                    val displayMessages = emission.displayMessages
                    val settled = emission.revalidated
                    handle.update {
                        content = content.copy(
                            messages = emission.messages,
                            displayMessages = displayMessages,
                            // Cached rows go straight to ACTIVE; an empty cache keeps
                            // spinning, so an uncached online open never flashes a blank
                            // thread first. Settling releases it either way.
                            screenState = if (displayMessages.isNotEmpty() || settled) {
                                ChatScreenState.ACTIVE
                            } else {
                                content.screenState
                            },
                            // Null once the server's copy arrives (or there was never a seed) →
                            // a later server-side delete can then still remove the row.
                            pendingResumeUserMessage = emission.retainedPending,
                        )
                    }
                    // Judged on the settled read only: a cached emission predates the failed turn
                    // and would always look like the server dropped it. A fetch that failed also
                    // settles, and then restores — duplicating text the server did keep is the
                    // recoverable mistake; losing text it did not keep is not.
                    pendingUnsavedTurn?.takeIf { settled }?.let { unsent ->
                        pendingUnsavedTurn = null
                        val kept = emission.messages.any {
                            it.messageId == unsent.messageId || it.isServerCopyOf(unsent)
                        }
                        if (!kept && unsent.text.isNotBlank()) {
                            restoreUnsentInput(unsent.text, unsent.quotes.orEmpty())
                        }
                    }
                    // Restore comparison mode when reopening a Compare Models conversation: the
                    // last assistant message carries both agents' attributed parts but nothing
                    // else records it was a comparison. Only when not streaming and not already
                    // comparing (respects a session toggle-off); the branched-away case has a
                    // single-agent tail, so it naturally shows the normal view.
                    // Gated on `settled` so the latch burns on the AUTHORITATIVE tail, not a stale
                    // cached one: a comparison tail that exists only server-side would otherwise
                    // never rehydrate.
                    if (!autoRehydrateHandled && settled && displayMessages.isNotEmpty()) {
                        autoRehydrateHandled = true
                        val state = handle.state
                        val tail = displayMessages.lastOrNull()?.message
                        if (!state.isStreaming && !state.comparisonState.isEnabled &&
                            tail != null && hasParallelParts(tail)
                        ) {
                            rehydrateComparison(tail)
                        }
                    }
                }
        }
    }

    /**
     * Puts an early-aborted turn's text back into the composer (the un-send flow: the Stop
     * landed before the server persisted anything, so the optimistic bubble was removed).
     * Yields to anything the user has since typed — same rule as [restoreDraft] — and persists
     * as a draft so the restored text survives process death, same as `ChatViewModel.onInputChanged`.
     */
    fun restoreUnsentInput(text: String, quotes: List<String> = emptyList()) {
        handle.update {
            if (inputText.isBlank()) {
                inputText = text
                // The chips were taken (and cleared) when the spec was minted, so an
                // un-send has to put them back or the retry silently loses the excerpts.
                // Anything staged since wins — same yield-to-the-user rule as the text.
                pendingQuotes = pendingQuotes.ifEmpty { quotes }
            }
        }
        if (handle.state.inputText != text) return
        val draftKey = handle.state.conversationId ?: NEW_CHAT_DRAFT_KEY
        handle.scope.launch {
            draftRepository.saveDraft(draftKey, text)
        }
    }

    /**
     * Restores a previously saved draft for the given key (conversation ID or [NEW_CHAT_DRAFT_KEY]).
     */
    fun restoreDraft(draftKey: String) {
        handle.scope.launch {
            // awaitDraft (not getDraft) so a first launch that opens the chat screen while identity is
            // still warming — e.g. straight after a cold start or the pre-tenancy DB migration — waits
            // for the account to resolve instead of reading null and leaving a saved draft hidden until
            // the next launch. The blank-check below still yields to anything the user has since typed.
            val draft = draftRepository.awaitDraft(draftKey)
            if (!draft.isNullOrBlank()) {
                handle.update { if (inputText.isBlank()) inputText = draft }
            }
        }
    }

    fun loadConversationModel(conversationId: String) {
        // SECURITY: do not remove — temp-chat data-at-rest guard. getConversation below
        // round-trips through refreshConversation, which upserts the conversation row to Room.
        // Temp chats must never persist, and their model/endpoint was already seeded from the
        // NewChatSelectionHandoff in init — so there is nothing to load and nothing to write.
        if (handle.state.isTemporaryChat) {
            onModelLoadSettled()
            return
        }
        handle.scope.launch {
            val result = conversationRepository.getConversation(conversationId, originAccount = null)
            val conversation = result.getOrNull()
            if (conversation != null) {
                handle.update { conversationTitle = conversation.title }
                val applied = applyConversationModel(conversation)
                Diag.d(
                    tag = "ModelSel",
                    attrs = mapOf(
                        "found" to "true",
                        "applied" to applied.toString(),
                        "endpoint" to (conversation.endpoint ?: "null"),
                    ),
                ) { "loadConversationModel resolved for $conversationId" }
            } else {
                // The just-created conversation isn't readable yet: the server emits the
                // `created` SSE event before the unawaited save persists it, so this GET can
                // race that save and 404. The in-process handoff already seeded the correct
                // selection in init, so we deliberately leave it untouched here. Only mark
                // "load attempted" — never "resolved" — so handleFinal can re-derive later.
                Diag.w(
                    tag = "ModelSel",
                    origin = LogOrigin.SERVER,
                    attrs = mapOf("found" to "false"),
                ) { "loadConversationModel: conversation not readable for $conversationId" }
            }
            onModelLoadSettled()
        }
    }

    fun refreshMessages() {
        val conversationId = handle.state.conversationId ?: return
        // SECURITY: do not remove — temp-chat data-at-rest guard.
        // Temp chats aren't persisted server- or client-side; a pull-to-refresh would
        // call refreshMessages → replaceAllForConversation, writing the temp message rows
        // to Room. Skip — there's nothing to refresh for a temporary chat.
        if (handle.state.isTemporaryChat) return
        if (handle.state.isRefreshingMessages) return
        handle.update { content = content.copy(isRefreshingMessages = true) }
        handle.scope.launch {
            // Foreground pull-to-refresh: the user is looking at this conversation now, so entry is
            // land time and the live account is the right one to attribute to.
            messageRepository.refreshMessages(conversationId, originAccount = null)
            loadConversation(conversationId)
            handle.update { content = content.copy(isRefreshingMessages = false) }
        }
    }
}

private data class MessagePathEmission(
    val messages: List<Message>,
    val displayMessages: List<MessageNode>,
    val retainedPending: Message?,
    val revalidated: Boolean,
)
