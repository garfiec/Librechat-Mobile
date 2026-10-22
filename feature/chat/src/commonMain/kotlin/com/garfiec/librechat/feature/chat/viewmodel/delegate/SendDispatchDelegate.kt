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
import com.garfiec.librechat.core.model.request.MAX_MANUAL_SKILLS
import com.garfiec.librechat.core.model.request.manualSkillsSupportedOn
import com.garfiec.librechat.core.model.steer.mergeRestagedQuotes
import com.garfiec.librechat.feature.chat.util.NEW_CHAT_DRAFT_KEY
import com.garfiec.librechat.feature.chat.util.buildActiveMessagePath
import com.garfiec.librechat.feature.chat.util.sendableManualSkills
import com.garfiec.librechat.feature.chat.viewmodel.ChatNotice
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
 * Also owns the quote chips (`ComposerState.pendingQuotes`) and the skill chips
 * (`ComposerState.pendingManualSkills`) end to end, and the reply-settled wait
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
    /** The project a new chat from this landing is filed into; sent on its first turn only. */
    private val chatProjectId: String? = null,
    private val newChatDraftKey: String = NEW_CHAT_DRAFT_KEY,
    /**
     * `SkillsToggleDelegate.turnOn`: a turn that carries skill picks turns the conversation's
     * Skills toggle on, as picking one does, so the following turns keep skills available.
     */
    private val turnSkillsOn: () -> Unit = {},
    /**
     * `SkillPickerDelegate.ensureLoaded(newTrigger = true)`: a send found chips that wait on the
     * skill catalog ([stageHeldBackManualSkills]) with none loaded, so it asks for one again.
     */
    private val loadSkillCatalog: () -> Unit = {},
) {
    /**
     * Staged chips a new chat's first send already held back, handed to this chat
     * ([stageHeldBackManualSkills]). Some were held back for what only the catalog shows, so
     * until a catalog is loaded no send takes them.
     */
    private var heldBackForCatalog: Set<String> = emptySet()

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
        return QueuedMessage(
            localId = Uuid.random().toString(),
            text = text,
            attachments = files,
            choice = state.selection.choice,
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
        if (doSendWithSpec(spec, fromComposer = true)) noticeHeldBackManualSkills()
    }

    /**
     * Says so when a send or queue that just took the chips left some staged: whatever
     * [takePendingManualSkills] did not take is a chip this chat can't run. Called after the send,
     * which clears the error channel.
     */
    fun noticeHeldBackManualSkills() {
        if (handle.state.pendingManualSkills.isNotEmpty()) handle.setError(ChatNotice.SKILLS_NOT_SENT.marker())
    }

    /**
     * Atomically takes (and clears) the pending skill chips for a send on [endpoint] — web's
     * `drainPendingManualSkills`. Like [takePendingQuotes], assistants endpoints take nothing and
     * leave the chips staged. Regenerate, edit and continue never call this: they replay the user
     * turn's persisted picks (or none). Taking a non-empty list turns the conversation's Skills
     * toggle on.
     *
     * Only the chips this chat can still run are taken ([sendableManualSkills]): a spec, agent or
     * endpoint chosen after a chip was staged may refuse it. The rest stay staged and visible, as
     * they do on assistants, rather than riding out as a pill for a skill that never runs; the
     * caller then shows [noticeHeldBackManualSkills].
     */
    fun takePendingManualSkills(endpoint: String): List<String> {
        val state = handle.state
        if (state.skillCatalog == null && state.pendingManualSkills.any { it in heldBackForCatalog }) {
            // The load that would check them failed or hasn't landed: try again, so the next
            // send can take whichever of them this chat can run.
            loadSkillCatalog()
        }
        val sendable = sendablePendingManualSkills(endpoint).toSet()
        if (sendable.isEmpty()) return emptyList()
        var taken: List<String> = emptyList()
        handle.update {
            taken = pendingManualSkills.filter { it in sendable }
            if (taken.isNotEmpty()) pendingManualSkills = pendingManualSkills - taken.toSet()
        }
        heldBackForCatalog = heldBackForCatalog - taken.toSet()
        if (taken.isNotEmpty()) turnSkillsOn()
        return taken
    }

    /**
     * Stages the chips a new chat's first send held back, handed to this chat with the
     * conversation, and the notice that said so when [notice]. Until a skill catalog is loaded
     * they stay staged: with only their names to check, a chip held back for a skill turned off
     * or outside the agent's scope would go out on the next send as a pill for a skill that
     * never runs.
     */
    fun stageHeldBackManualSkills(names: List<String>, notice: Boolean) {
        if (names.isEmpty()) return
        heldBackForCatalog = heldBackForCatalog + names
        handle.update {
            pendingManualSkills = (pendingManualSkills + names).distinct().take(MAX_MANUAL_SKILLS)
            if (notice) error = ChatNotice.SKILLS_NOT_SENT.marker()
        }
    }

    /**
     * The staged chips a send on [endpoint] would take, without taking them: the filter
     * [takePendingManualSkills] applies. A queued edit reads its chips through this, because its
     * composer is swapped back to the stashed draft afterwards rather than cleared.
     */
    fun sendablePendingManualSkills(endpoint: String): List<String> {
        if (!manualSkillsSupportedOn(endpoint)) return emptyList()
        val state = handle.state
        return sendableManualSkills(
            pending = state.pendingManualSkills,
            catalog = state.skillCatalog,
            canPick = state.skillsAllowedHere,
            endpoint = endpoint,
            selectedModel = state.selectedModel,
            agents = state.agents,
            specSkills = state.activeSpecSkills,
            needsCatalog = heldBackForCatalog,
        )
    }

    /**
     * Stages a picked skill as a pending chip. Deduped by name (two same-name skills resolve to
     * one server-side) and capped at [MAX_MANUAL_SKILLS]. Refused where the picker's rules
     * (`ChatUiState.skillRules`) refuse it: skills unavailable here, an agent that offers none,
     * or an active model spec that turns them off or does not list this one. Returns whether a
     * chip was added.
     */
    fun addPendingManualSkill(name: String): Boolean {
        if (handle.state.skillRules?.allowsName(name) != true) return false
        var added = false
        handle.update {
            if (name !in pendingManualSkills && pendingManualSkills.size < MAX_MANUAL_SKILLS) {
                pendingManualSkills = pendingManualSkills + name
                added = true
            }
        }
        return added
    }

    /** Removes one pending skill chip (its ×). */
    fun removePendingManualSkill(name: String) {
        handle.update { pendingManualSkills = pendingManualSkills - name }
        heldBackForCatalog = heldBackForCatalog - name
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
        val draftKey = handle.state.conversationId ?: newChatDraftKey
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
     * [fromComposer] is the live send: only once the streaming guard has passed does it take the
     * staged quote and skill chips onto the spec (which also turns Skills on) and clear the
     * composer, so a lost readiness race can't wipe an unsent message or its chips. False for
     * drains, which carry their own chips and must leave the user's composer untouched.
     *
     * Returns whether the turn was sent.
     */
    @OptIn(ExperimentalUuidApi::class)
    fun doSendWithSpec(baseSpec: QueuedMessage, fromComposer: Boolean = false): Boolean {
        val fileRefs = baseSpec.attachments.map { it.toFileReference() }
        val hasFiles = fileRefs.isNotEmpty()
        val messageText = baseSpec.text
        if ((messageText.isBlank() && !hasFiles) || handle.state.isStreaming) return false
        // Guard passed: safe to take the chips and clear the composer without risking their loss.
        val spec = if (fromComposer) {
            baseSpec.copy(
                quotes = takePendingQuotes(baseSpec.endpoint),
                manualSkills = takePendingManualSkills(baseSpec.endpoint),
            )
        } else {
            baseSpec
        }
        if (fromComposer) clearComposer()

        // Count one "used" tick for the picked model — the real usage signal for the most-used
        // ranking behind home-screen shortcuts. Fires on every dispatched send (live or a drained
        // queue item, since both land here). Agents are excluded: their selection is an opaque
        // agentId, which would surface as an unreadable shortcut label.
        val usedModel = spec.model
        if (spec.endpoint != EndpointConstants.AGENTS && !usedModel.isNullOrBlank()) {
            handle.scope.launch { settingsDataStore.incrementModelUsage(spec.endpoint, usedModel) }
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
            // Painted now for the same reason: the user bubble's skill pills.
            manualSkills = spec.manualSkills.takeIf { it.isNotEmpty() },
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
                sandboxStartingToolCallIds = emptySet(),
                streamingAttachments = emptyList(),
                screenState = if (isNewChat) ChatScreenState.LANDING else ChatScreenState.ACTIVE,
                messages = updatedMessages,
                displayMessages = updatedDisplay,
            )
            error = null
        }
        beginStreaming(optimisticMessage.messageId, spec)

        Logger.d {
            "sendMessage: webSearch=${spec.modelParameters.webSearch}, " +
                "endpoint=${spec.endpoint}, " +
                "model=${spec.model}, spec=${spec.modelSpec}, " +
                "files=${fileRefs.size}, " +
                "ephemeralAgent=${spec.ephemeralAgent}"
        }

        // Resolve effective endpoint/agentId for comparison mode.
        // All requests go through api/agents/chat/{endpoint} — the server's
        // middleware creates ephemeral agents for non-agent endpoints, so no
        // swapping is needed. Just keep the primary's original endpoint.
        val effectiveEndpoint = spec.endpoint
        val effectiveAgentId = spec.agentId
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
            spec = spec.modelSpec,
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
            manualSkills = spec.manualSkills.takeIf { it.isNotEmpty() },
            // First turn only: the server saves it on every turn that carries it, so a later one
            // would move the chat. A temporary chat is never filed.
            chatProjectId = if (isNewChat && !spec.isTemporary) chatProjectId else null,
        )
        launchStream(stream)
        return true
    }

    private companion object {
        /** Upper bound on waiting for a finished reply to land in the tree before draining the
         *  next queued message. Generous so a slow post-Final reload still chains correctly. */
        const val REPLY_SETTLE_TIMEOUT_MS = 8_000L
    }
}
