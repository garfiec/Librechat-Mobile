package com.garfiec.librechat.feature.chat.viewmodel.delegate

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.common.identity.ActiveAccountProvider
import com.garfiec.librechat.core.common.identity.currentAccountId
import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.data.repository.ChatRepository
import com.garfiec.librechat.core.data.repository.DraftRepository
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.StreamEvent
import com.garfiec.librechat.core.model.request.AddedConversation
import com.garfiec.librechat.core.model.steer.mergeRestagedQuotes
import com.garfiec.librechat.feature.chat.util.NEW_CHAT_DRAFT_KEY
import com.garfiec.librechat.feature.chat.util.buildActiveMessagePath
import com.garfiec.librechat.feature.chat.viewmodel.ChatRequestBuilder
import com.garfiec.librechat.feature.chat.viewmodel.ChatScreenState
import com.garfiec.librechat.feature.chat.viewmodel.QueuedMessage
import com.garfiec.librechat.feature.chat.viewmodel.SendDispatchHandle
import com.garfiec.librechat.feature.chat.viewmodel.quotesSupportedOn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Turns the composer into a request: snapshots the live selection into a [QueuedMessage] spec,
 * takes the staged quote chips, clears the composer, inserts the optimistic user message and hands
 * the stream to the streaming manager. One [doSendWithSpec] serves a live send and a queue drain,
 * which is why the config rides the spec while the lineage (conversation, parent, minted id) is
 * recomputed from the current tree.
 *
 * Also owns the quote chips (`ComposerState.pendingQuotes`) end to end, and the reply-settled wait
 * a drain makes before chaining onto the finished turn. Which path a tap takes is decided by the
 * entry points (`ChatViewModel.sendMessage` / `queueMessage` / `sendDuringRun` / `steerMessage`);
 * what happens once the server answers belongs to the streaming, queue and completion delegates.
 */
class SendDispatchDelegate(
    private val handle: SendDispatchHandle,
    private val chatRepository: ChatRepository,
    private val draftRepository: DraftRepository,
    private val settingsDataStore: SettingsDataStore,
    private val fileHandler: PlatformFileHandler,
    private val requestBuilder: ChatRequestBuilder,
    private val activeAccountProvider: ActiveAccountProvider,
    /**
     * `StreamingManagerDelegate.beginStreaming` for a new turn: the minted optimistic user-message
     * id and the spec this turn actually dispatches, not the composer's current state, because a
     * human-review pause is resumed against the config the run was started with.
     */
    private val beginStreaming: (optimisticUserMessageId: String, turnSpec: QueuedMessage) -> Unit,
    /** `StreamingManagerDelegate.launchStream`. */
    private val launchStream: (Flow<StreamEvent>) -> Unit,
    /** `ComparisonModeDelegate.onSendStart`, before the added conversation is built. */
    private val onComparisonSendStart: () -> Unit,
    /** `ComparisonModeDelegate.buildAddedConvo` for the parent this turn attaches to. */
    private val buildAddedConvo: (parentMessageId: String?) -> AddedConversation?,
) {

    /**
     * Snapshots the current send config into a [QueuedMessage]. Used both for a normal send
     * (fired immediately) and for queueing (fired later, unchanged by intervening config edits).
     * Returns null when there is nothing to send (blank text and no uploaded files).
     */
    @OptIn(ExperimentalUuidApi::class)
    fun buildSendSpec(text: String): QueuedMessage? {
        // Snapshot the uploaded AttachedFiles (not just FileReferences) so a queued item can
        // round-trip losslessly back into the composer on edit — keeping its local-uri thumbnail.
        val allFiles = fileHandler.attachedFiles.value
        val files = allFiles.filter { it.fileId != null }
        // Surface attachments excluded from the send (still uploading or failed) so a dropped
        // file leaves a diagnostic trail rather than vanishing silently.
        val dropped = allFiles.filter { it.fileId == null }
        if (dropped.isNotEmpty()) {
            Logger.w {
                "buildSendSpec: ${dropped.size} attachment(s) not yet uploaded, excluded from send: " +
                    dropped.joinToString { it.name }
            }
        }
        if (text.isBlank() && files.isEmpty()) return null
        val state = handle.state
        val isAgent = state.selectedEndpoint == EndpointConstants.AGENTS
        return QueuedMessage(
            localId = Uuid.random().toString(),
            text = text,
            attachments = files,
            endpoint = state.selectedEndpoint,
            model = state.selectedModel,
            agentId = if (isAgent) state.selectedModel else null,
            enabledTools = state.enabledTools,
            mcpServerNames = state.selectedMcpServerNames,
            modelParameters = state.modelParameters,
            modelParamsPayload = requestBuilder.buildModelParams(),
            ephemeralAgent = requestBuilder.buildEphemeralAgent(),
            dispatch = requestBuilder.currentDispatch(),
            isTemporary = state.isTemporaryChat,
            // Capture the composing account so a drain after an account switch drops this item rather
            // than POSTing it to the newly-active account's server.
            accountId = activeAccountProvider.currentAccountId()?.value,
        )
    }

    fun sendNow(text: String) {
        val spec = buildSendSpec(text) ?: return
        doSendWithSpec(
            spec.copy(quotes = takePendingQuotes(spec.endpoint)),
            clearComposerOnSend = true,
        )
    }

    /**
     * Atomically takes (and clears) the staged quote chips for a send on [endpoint] — the
     * fresh-submit / composer-queue drain of web's `pendingQuotesByConvoId` atom. Assistants
     * endpoints take nothing and leave the chips staged: they bypass the server-side merge, and
     * a selection staged elsewhere must not silently ride along (web's `quotesSupported` guard).
     * Regenerate/continue/edit never call this — those flows replay a prior turn.
     */
    fun takePendingQuotes(endpoint: String): List<String> {
        if (!quotesSupportedOn(endpoint)) return emptyList()
        var taken: List<String> = emptyList()
        handle.update {
            taken = pendingQuotes
            if (taken.isNotEmpty()) pendingQuotes = emptyList()
        }
        return taken
    }

    /** Stages a selected excerpt as a pending quote chip (selection toolbar "Add to chat"). */
    fun addPendingQuote(text: String) {
        val excerpt = text.trim()
        if (excerpt.isEmpty()) return
        handle.update { pendingQuotes = pendingQuotes + excerpt }
    }

    /**
     * Puts excerpts a steer lost back on the composer's chips, deduped and capped.
     *
     * Unlike [addPendingQuote] this is a RESTORE, not a new selection, so it goes through
     * [mergeRestagedQuotes]: the same excerpts can arrive from more than one recovery trigger for
     * one steer, and appending blindly would multiply the user's chips.
     */
    fun restagePendingQuotes(quotes: List<String>) {
        if (quotes.isEmpty()) return
        handle.update { pendingQuotes = mergeRestagedQuotes(pendingQuotes, quotes) }
    }

    /** Removes one staged quote chip (its ×). */
    fun removePendingQuote(index: Int) {
        handle.update {
            if (index !in pendingQuotes.indices) return@update
            pendingQuotes = pendingQuotes.filterIndexed { i, _ -> i != index }
        }
    }

    /**
     * Suspends until the previous reply has settled into the message tree: streaming is over and
     * the active path ends in an assistant message (the post-Final Room reload has landed). Used
     * before draining a queued follow-up so its optimistic insert chains onto that reply. Bounded
     * by [REPLY_SETTLE_TIMEOUT_MS]; on timeout (e.g. a failed reload) we proceed best-effort.
     */
    suspend fun awaitReplySettled() {
        withTimeoutOrNull(REPLY_SETTLE_TIMEOUT_MS) {
            handle.stateFlow.first { state ->
                !state.isStreaming &&
                    state.displayMessages.lastOrNull()?.message?.isCreatedByUser == false
            }
        }
    }

    /** Clears the input, its persisted draft, and any attached files. */
    fun clearComposer() {
        val draftKey = handle.state.conversationId ?: NEW_CHAT_DRAFT_KEY
        // Drop any staged batch too: it belongs to the message just sent, and surviving here would
        // attach it to the next one.
        handle.update {
            inputText = ""
            pendingUploadRouting = null
        }
        handle.scope.launch { draftRepository.deleteDraft(draftKey) }
        fileHandler.clearAttachedFiles()
    }

    /**
     * Sends one message from a [QueuedMessage] config snapshot. The config (endpoint/model/
     * tools/webSearch/attachments/dispatch/ephemeralAgent) comes from the spec, but the
     * lineage — conversationId, parentMessageId, and the minted optimistic user-message id —
     * is recomputed from the *current* tree, so a drained item chains onto the freshly-
     * finalized turn.
     *
     * [clearComposerOnSend] clears the composer only once the streaming guard has passed — set
     * true on the live-send path (so a lost readiness race can't wipe an unsent message) and
     * false for drains (which must leave the user's in-progress composer untouched).
     */
    @OptIn(ExperimentalUuidApi::class)
    fun doSendWithSpec(spec: QueuedMessage, clearComposerOnSend: Boolean = false) {
        val fileRefs = spec.attachments.map { it.toFileReference() }
        val hasFiles = fileRefs.isNotEmpty()
        val messageText = spec.text
        if ((messageText.isBlank() && !hasFiles) || handle.state.isStreaming) return
        // Guard passed: safe to clear the composer for a live send without risking message loss.
        if (clearComposerOnSend) clearComposer()

        // Count one "used" tick for the picked model — the real usage signal for the most-used
        // ranking behind home-screen shortcuts. Fires on every dispatched send (live or a drained
        // queue item, since both land here). Agents are excluded: their selection is an opaque
        // agentId, which would surface as an unreadable shortcut label.
        if (spec.endpoint != EndpointConstants.AGENTS && !spec.model.isNullOrBlank()) {
            handle.scope.launch { settingsDataStore.incrementModelUsage(spec.endpoint, spec.model) }
        }

        val conversationId = handle.state.conversationId
        val lastMessageId = handle.state.displayMessages.lastOrNull()?.message?.messageId

        // Add optimistic user message to display immediately
        val optimisticMessage = Message(
            messageId = Uuid.random().toString(),
            conversationId = conversationId ?: "",
            parentMessageId = lastMessageId,
            text = messageText,
            isCreatedByUser = true,
            sender = "User",
            createdAt = Clock.System.now().toString(),
            files = fileRefs.takeIf { it.isNotEmpty() },
            // The server persists and echoes them; painting them optimistically keeps the user
            // bubble's quote blocks from popping in a turn later.
            quotes = spec.quotes.takeIf { it.isNotEmpty() },
        )
        val isNewChat = conversationId == null
        handle.update {
            val updatedMessages = content.messages + optimisticMessage
            val updatedDisplay =
                buildActiveMessagePath(updatedMessages, content.activeBranches, optimisticMessage.messageId)
            content = content.copy(
                isStreaming = true,
                streamingContent = "",
                streamingThinking = "",
                activeToolCalls = emptyList(),
                streamingAttachments = emptyList(),
                screenState = if (isNewChat) ChatScreenState.LANDING else ChatScreenState.ACTIVE,
                messages = updatedMessages,
                displayMessages = updatedDisplay,
            )
            error = null
        }
        beginStreaming(optimisticMessage.messageId, spec)

        val isAgent = spec.endpoint == EndpointConstants.AGENTS
        Logger.d {
            "sendMessage: webSearch=${spec.modelParameters.webSearch}, " +
                "endpoint=${spec.endpoint}, " +
                "model=${spec.model}, " +
                "files=${fileRefs.size}, " +
                "ephemeralAgent=${spec.ephemeralAgent}"
        }

        // Resolve effective endpoint/agentId for comparison mode.
        // All requests go through api/agents/chat/{endpoint} — the server's
        // middleware creates ephemeral agents for non-agent endpoints, so no
        // swapping is needed. Just keep the primary's original endpoint.
        val effectiveEndpoint = spec.endpoint
        val effectiveAgentId = if (isAgent) spec.agentId else null
        onComparisonSendStart()

        val effectiveAddedConvo = buildAddedConvo(lastMessageId)
        val stream = chatRepository.startChat(
            text = messageText,
            conversationId = conversationId,
            endpoint = effectiveEndpoint,
            endpointType = spec.dispatch.endpointType,
            key = spec.dispatch.key,
            modelDisplayLabel = spec.dispatch.modelDisplayLabel,
            model = spec.model,
            userMessageId = optimisticMessage.messageId,
            parentMessageId = lastMessageId,
            agentId = effectiveAgentId,
            webSearch = spec.modelParameters.webSearch,
            files = fileRefs.takeIf { it.isNotEmpty() },
            addedConvo = effectiveAddedConvo,
            ephemeralAgent = spec.ephemeralAgent,
            isTemporary = spec.isTemporary,
            modelParams = spec.modelParamsPayload,
            quotes = spec.quotes.takeIf { it.isNotEmpty() },
        )
        launchStream(stream)
    }

    private companion object {
        /** Upper bound on waiting for a finished reply to land in the tree before draining the
         *  next queued message. Generous so a slow post-Final reload still chains correctly. */
        const val REPLY_SETTLE_TIMEOUT_MS = 8_000L
    }
}
