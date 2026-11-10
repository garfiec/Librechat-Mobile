package com.garfiec.librechat.feature.chat.viewmodel.delegate

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.common.identity.ActiveAccountProvider
import com.garfiec.librechat.core.common.identity.currentAccountId
import com.garfiec.librechat.core.model.FileReference
import com.garfiec.librechat.core.model.media.resolveFileReferenceUrl
import com.garfiec.librechat.core.model.queuedturn.AgentQueuedTurnReceipt
import com.garfiec.librechat.core.model.queuedturn.QueuedTurnFileRef
import com.garfiec.librechat.feature.chat.components.AttachedFile
import com.garfiec.librechat.feature.chat.util.acrossSpecChange
import com.garfiec.librechat.feature.chat.util.isImageType
import com.garfiec.librechat.feature.chat.util.requiresSpec
import com.garfiec.librechat.feature.chat.util.storedAgainst
import com.garfiec.librechat.feature.chat.viewmodel.ChatNotice
import com.garfiec.librechat.feature.chat.viewmodel.ChatRequestBuilder
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.ComposerSnapshot
import com.garfiec.librechat.feature.chat.viewmodel.DraftRestore
import com.garfiec.librechat.feature.chat.viewmodel.QueueOrchestrationHandle
import com.garfiec.librechat.feature.chat.viewmodel.QueuedEditSession
import com.garfiec.librechat.feature.chat.viewmodel.QueuedMessage
import com.garfiec.librechat.feature.chat.viewmodel.QueuedTurnServerState
import com.garfiec.librechat.feature.chat.viewmodel.quotesSupportedOn
import com.garfiec.librechat.feature.chat.viewmodel.toComposerSnapshot
import com.garfiec.librechat.feature.chat.viewmodel.withRestore
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

/**
 * Everything between the composer and the follow-up queue that [MessageQueueDelegate] (the local
 * FIFO) and [QueuedTurnDelegate] (the server's copy of it) do not decide themselves: minting a
 * queued row from the composer, deciding whether the server may own it, the tap-to-edit session
 * that swaps a queued item into the composer and back, withdrawal under one global fence, the
 * reorder and "send queued" controls, the drain kick, and where the reconcile poll is aimed.
 *
 * The queue slice is never written here; every row change goes through the two queue delegates.
 * What this delegate writes is the composer swap ([captureComposer] / [applyComposer]), which
 * carries the selection with it in one emission, and the edit session that swap belongs to.
 */
class QueueOrchestrationDelegate(
    private val handle: QueueOrchestrationHandle,
    private val queueDelegate: MessageQueueDelegate,
    private val queuedTurnDelegate: QueuedTurnDelegate,
    private val uploadIntakeDelegate: UploadIntakeDelegate,
    private val sendDispatchDelegate: SendDispatchDelegate,
    private val requestBuilder: ChatRequestBuilder,
    private val activeAccountProvider: ActiveAccountProvider,
    private val fileHandler: PlatformFileHandler,
    /** `PendingActionDelegate.generationEpoch`: the running turn's epoch, or null when unknown. */
    private val generationEpoch: () -> Long?,
    /** `StreamingManagerDelegate.queueAnchorFor`: the server's id for a branch tail, or null. */
    private val queueAnchor: (String) -> String?,
) {

    fun enqueueNow(text: String) {
        val spec = sendDispatchDelegate.buildSendSpec(text) ?: return
        // Composer-origin queue takes the staged quotes with it (web takeComposerContext): they
        // pair with THIS queued message instead of gluing onto whatever the user sends next.
        // The skill chips go with it the same way. A server-owned row sends them on the enqueue
        // claim: the server queues turns only for persisted agents, whose run honours them.
        val withContext = spec.copy(
            quotes = sendDispatchDelegate.takePendingQuotes(spec.endpoint),
            manualSkills = sendDispatchDelegate.takePendingManualSkills(spec.endpoint),
            reasoningOverride = sendDispatchDelegate.takePendingReasoningOverride(),
        )
        sendDispatchDelegate.clearComposer()
        enqueueSpec(withContext)
        sendDispatchDelegate.noticeHeldBackManualSkills()
    }

    /**
     * Queues an already-built send spec. Split from [enqueueNow] because a steer that degrades
     * arrives with its spec minted at send time and its composer long since cleared — clearing
     * again there would wipe whatever the user has typed in the meantime.
     */
    fun enqueueSpec(spec: QueuedMessage) {
        placeInQueue(spec, queueDelegate::enqueue)
        // If the in-flight reply already finished, no Final will arrive to drain this — kick it now.
        tryResumeDrain()
    }

    /** Puts [spec] in the queue through [insert], handing it to the server when it can own it. */
    private fun placeInQueue(spec: QueuedMessage, insert: (QueuedMessage) -> Unit) {
        val conversationId = handle.state.conversationId
        val owned = if (conversationId == null) spec else serverOwnedSpec(spec)
        insert(owned)
        // Strictly after the row is in the queue: everything the enqueue answer does — marking it
        // rejected, handing it back to the legacy drain — addresses a row that has to exist.
        if (owned.server != null && conversationId != null) {
            queuedTurnDelegate.enqueue(owned, conversationId)
        }
    }

    private fun ChatUiState.enforcedSpecRestored(): Boolean {
        if (!gates.serverQueueEnforcedAgentSpecSupported) return false
        val active = activeSpec ?: return false
        val preset = active.preset ?: return false
        return preset.endpoint == EndpointConstants.AGENTS &&
            preset.agentId == selectedModel &&
            modelSpecs?.list.orEmpty().count { it.name == active.name } == 1
    }

    /**
     * Marks [spec] as one the SERVER will admit and run, or returns it unchanged for the legacy
     * local drain.
     *
     * Three things have to be true, and all three are properties of a live agent run:
     * the endpoint takes queued turns at all, there is a visible branch leaf to anchor to, and
     * the run's generation epoch is known. Without an authoritative pair the follow-up stays
     * local — a guess here would have the server admit behind the wrong boundary.
     *
     * The anchor is the *user* message of the running turn, not the reply: the reply has no
     * server id until it is persisted. That is what upstream sends too, and the server treats it
     * as a branch anchor rather than a literal parent — it walks forward to the newest assistant
     * message descending from it, which is how a queue of several chains correctly.
     * `displayMessages` is truncated at that leaf for the duration of a stream, so its tail IS
     * the anchor (the same identity the completion-render keying relies on), named by the server's
     * id for it: in a run this client started, the tail is the optimistic user message, whose id
     * the server never stored ([queueAnchor]).
     *
     * `clientRequestId` is minted here and not taken from [QueuedMessage.localId]: localId
     * survives an edit, and reusing an id for different text is a 409.
     *
     * Under `enforce` ([requiresSpec]) only for a served agents spec on v0.8.9-rc1+
     * ([enforcedSpecRestored]). The queued turn carries no spec (the enqueue schema has no such field,
     * and the worker re-posts it to the chat route without one, `agents/triggers/host.ts`); rc1's
     * `restoreAgentTriggerModelSpec` (`packages/api/src/modelSpecs/continuation.ts`) puts the
     * conversation's spec back only when its preset names this agent and no other spec shares its
     * name. Anything else is accepted, then refused on the re-post with "No model spec selected" (an
     * error frame the worker retries), and the row is left claimed, blocking the chat's server queue.
     * So it stays in the local drain, which sends the spec.
     */
    private fun serverOwnedSpec(spec: QueuedMessage): QueuedMessage {
        val state = handle.state
        if (!state.gates.serverQueueSupported) return spec
        if (state.modelSpecs.requiresSpec && !state.enforcedSpecRestored()) return spec
        // Without v2 the server would run the turn under the machine's own permissions.
        if (spec.codeWorkspace?.approvalMode != null && !state.gates.serverQueueApprovalModeSupported) return spec
        if (state.selectedEndpoint != EndpointConstants.AGENTS) return spec
        if (!state.isStreaming) return spec
        val parentMessageId = state.displayMessages.lastOrNull()?.message?.messageId
            ?.let(queueAnchor) ?: return spec
        val predecessorCreatedAt = generationEpoch() ?: return spec
        return spec.copy(
            server = QueuedTurnServerState(status = QueuedTurnServerState.Status.Sending),
            clientRequestId = Uuid.random().toString(),
            parentMessageId = parentMessageId,
            expectedPredecessorCreatedAt = predecessorCreatedAt,
        )
    }

    /**
     * A display row for a queued turn this client has no record of — one queued on another
     * device, or by a process that has since been killed (the queue is memory-only, the server's
     * is not).
     *
     * The server runs the turn with the conversation's own config, so the model, tools and
     * parameters here are the composer's current ones. They are read only if the user edits the
     * row, which loads them into the composer exactly as upstream's edit does. The files are the
     * receipt's own: an edit that dropped them would send the turn without them.
     */
    fun projectOrphanQueuedTurn(receipt: AgentQueuedTurnReceipt): QueuedMessage {
        val state = handle.state
        return QueuedMessage(
            localId = receipt.clientRequestId,
            text = receipt.text,
            attachments = receipt.files.orEmpty().map { it.toAttachedFile(state.serverUrl) },
            choice = state.selection.choice,
            dispatch = requestBuilder.currentDispatch(),
            accountId = activeAccountProvider.currentAccountId()?.value,
        )
    }

    private fun QueuedTurnFileRef.toAttachedFile(baseUrl: String): AttachedFile {
        val isImage = type?.let(::isImageType) == true
        val previewUrl = if (isImage) {
            resolveFileReferenceUrl(FileReference(fileId = fileId, filepath = filepath, type = type), baseUrl)
        } else {
            null
        }
        return AttachedFile(
            uri = previewUrl ?: fileId,
            name = filename ?: fileId,
            isImage = isImage,
            uploadProgress = 1f,
            fileId = fileId,
            filepath = filepath,
            type = type,
            width = width,
            height = height,
        )
    }

    /** Resumes FIFO draining when the queue is idle (not mid-stream, not paused). No-op otherwise;
     *  [MessageQueueDelegate.drainNext] additionally guards the paused / editing / empty cases. */
    fun tryResumeDrain() {
        if (!handle.state.isStreaming && !handle.state.isQueuePaused) {
            queueDelegate.drainNext(awaitSettle = false)
        }
    }

    /**
     * The row whose withdrawal DELETE is in flight, or null.
     *
     * A **global** fence, not a per-row one: one withdrawal at a time across the whole queue. The
     * row it names is for diagnostics; every consumer tests it for null. That is deliberately
     * stricter than the race each consumer can describe on its own — [reorderQueue] in particular
     * needs it, because any in-flight withdrawal is about to shift the indices it operates on —
     * and it is why the two consumers that refuse a user's tap say so rather than returning
     * silently. A per-row `Set` is recorded as a follow-up; it changes the concurrency model.
     */
    private var withdrawingForEdit: String? = null

    /**
     * Tap a queued ghost bubble: enter queued-edit mode. Stashes the current new-message draft and
     * loads the item's text + attachments + model/tools/params into the composer for editing.
     * Ignored if already editing one.
     *
     * The item STAYS in the queue, and on the server, until [commitQueuedEdit] replaces it. Leaving
     * the chat mid-edit (the ViewModel cleared, the process killed, an account switch) therefore
     * leaves it queued and unchanged and drops only the unsaved edit, as a cancel would. Withdrawn
     * here, its only copy would be this ViewModel's composer state. The session names the row by
     * [QueuedMessage.localId], never by a flag on the row: the reconcile poll rewrites server-owned
     * rows. The drain is frozen while the session is open ([MessageQueueDelegate.drainNext]), so
     * this client never sends it mid-edit; the server can still admit a server-owned one at the
     * run end, which [commitQueuedEdit] detects.
     */
    fun editQueued(localId: String) {
        if (handle.state.isEditingQueued) return
        // A withdrawal in flight is about to remove a row, possibly this one, out from under the
        // session.
        if (withdrawingForEdit != null) {
            reportQueueBusy()
            return
        }
        // A pick that has not settled yet belongs to the new-message draft. Swapping the composer
        // out from under it re-homes it onto the queued item instead — attaching it to a message
        // the user did not pick it for, and losing it from the one they did, since `captureComposer`
        // cannot stash a file that is not in the tray yet.
        //
        // An upload still out (or a PDF waiting for its password) is the same: its outcome lands on
        // the tray entry the swap takes away, so the stashed copy would read uploading for good.
        if (uploadIntakeDelegate.hasUnsettledPicks() || fileHandler.hasPendingUploads()) {
            Logger.d { "editQueued: refusing — the draft's files are not settled yet" }
            handle.setError(ChatNotice.QUEUE_EDIT_WAITS_FOR_UPLOADS.marker())
            return
        }
        val index = handle.state.messageQueue.indexOfFirst { it.localId == localId }
        val item = handle.state.messageQueue.getOrNull(index) ?: return
        // An edit that changes anything withdraws the server's copy at commit. A row with no
        // address to withdraw (its POST unanswered or unconfirmed) could never be committed, so it
        // is refused now rather than after the user has typed. Same refusal the × gives.
        if (!item.isWithdrawable()) {
            reportWithdrawRefused()
            return
        }
        beginQueuedEdit(item, index)
    }

    /** Whether [QueuedTurnDelegate.cancel] can succeed for this row at all: local, addressed, or
     *  refused with nothing committed. */
    private fun QueuedMessage.isWithdrawable(): Boolean =
        server == null || server.id != null || server.status == QueuedTurnServerState.Status.Rejected

    /** Drops every trace of server ownership, leaving a row the local drain may send. */
    private fun QueuedMessage.asLegacyRow(): QueuedMessage = copy(
        server = null,
        clientRequestId = null,
        parentMessageId = null,
        expectedPredecessorCreatedAt = null,
    )

    /**
     * Swaps the composer over to [item]. The skill and quote chips go with it: the draft's are
     * stashed and the item's own shown, so a `$` pick or an "Add to chat" during the edit lands on
     * this message and the draft's chips come back untouched when the edit ends.
     */
    private fun beginQueuedEdit(item: QueuedMessage, index: Int) {
        val stashed = captureComposer()
        applyComposer(
            item.toComposerSnapshot(),
            editSession = QueuedEditSession(original = item, originalIndex = index, stashed = stashed),
        )
    }

    /** "Update" in queued-edit mode: replace the queued item with the edit (or drop it if emptied),
     *  then restore the stashed new-message draft. Waits for any attachment added during the edit
     *  to finish uploading (same gate as send/queue) so it isn't silently dropped. */
    fun commitQueuedEdit() {
        val session = handle.state.editingQueuedItem ?: return
        if (withdrawingForEdit != null) {
            reportQueueBusy()
            return
        }
        uploadIntakeDelegate.withUploadGate(handle.state.inputText.trim()) { text ->
            // The upload wait is async — bail if the edit was cancelled (or replaced) meanwhile.
            if (handle.state.editingQueuedItem != session) return@withUploadGate
            if (withdrawingForEdit != null) {
                reportQueueBusy()
                return@withUploadGate
            }
            val edited = sendDispatchDelegate.buildSendSpec(text)?.let { spec ->
                spec.copy(
                    localId = session.original.localId,
                    quotes = handle.state.pendingQuotes.takeIf { quotesSupportedOn(spec.endpoint) }.orEmpty(),
                    // The chips on screen during the edit are this item's own (see
                    // [beginQueuedEdit]), so what the user added or removed there is what it sends.
                    manualSkills = sendDispatchDelegate.sendablePendingManualSkills(spec.endpoint),
                    // On screen during the edit was this item's own choice, like the skill chips.
                    reasoningOverride = handle.state.composer.pendingReasoningOverride
                        ?.takeIf { handle.state.acceptsReasoning(it) },
                )
            }
            // The composer goes back to the draft when the edit ends, so a chip the chat can't run
            // is gone from the item for good: say so.
            val removedSkills = edited != null && edited.manualSkills.size < handle.state.pendingManualSkills.size
            val current = handle.state.messageQueue.firstOrNull { it.localId == session.original.localId }
            when {
                // It left the queue during the edit. Only the server removes a row then: it admitted
                // the original at the run end, or the row was withdrawn elsewhere. Offering the edit
                // back now would send these words a second time.
                current == null -> {
                    finishQueuedEdit(session)
                    handle.setError(ChatNotice.QUEUE_EDIT_OVERTAKEN.marker())
                }
                current.server == null -> {
                    if (edited != null) {
                        queueDelegate.replace(session.original.localId, session.originalIndex, edited)
                    } else {
                        // Composer emptied → treat as delete.
                        queueDelegate.cancel(session.original.localId)
                    }
                    finishQueuedEdit(session)
                    if (removedSkills) handle.setError(ChatNotice.SKILLS_REMOVED_FROM_QUEUED.marker())
                }
                // Nothing the server holds would change: keep its copy and its place.
                edited != null && edited.isSameServerTurnAs(current) -> {
                    finishQueuedEdit(session)
                    if (removedSkills) handle.setError(ChatNotice.SKILLS_REMOVED_FROM_QUEUED.marker())
                }
                else -> commitServerOwnedEdit(session, current, edited, removedSkills)
            }
        }
    }

    /**
     * Withdraws the server's copy of [current] and only then offers [edited] in its place, so the
     * server never holds both. A refusal (the server is admitting it, or the DELETE's outcome is
     * unknown) keeps the session open with the edit still in the composer: the user can retry or
     * cancel, and a row the server then admits leaves the queue, which the next Update reports.
     */
    private fun commitServerOwnedEdit(
        session: QueuedEditSession,
        current: QueuedMessage,
        edited: QueuedMessage?,
        removedSkills: Boolean,
    ) {
        val localId = session.original.localId
        val index = handle.state.messageQueue.indexOfFirst { it.localId == localId }
        withdrawingForEdit = localId
        handle.scope.launch {
            try {
                if (!queuedTurnDelegate.cancel(current)) {
                    reportWithdrawRefused()
                    return@launch
                }
                // A withdrawal that landed applied a receipt, which retires the row. One still
                // listed with a server id was answered "not found": the server already ran it and
                // let it go, so offering the edit back would send these words twice.
                if (handle.state.messageQueue.any { it.localId == localId && it.server?.id != null }) {
                    queueDelegate.cancel(localId)
                    finishQueuedEdit(session)
                    handle.setError(ChatNotice.QUEUE_EDIT_OVERTAKEN.marker())
                    return@launch
                }
                if (edited != null) {
                    // The cancel receipt has usually retired the row already. The edit goes back in
                    // its slot, with a fresh server identity minted by [placeInQueue].
                    placeInQueue(edited.asLegacyRow()) { queueDelegate.replace(localId, index, it) }
                } else {
                    queueDelegate.cancel(localId)
                }
                finishQueuedEdit(session)
                if (removedSkills) handle.setError(ChatNotice.SKILLS_REMOVED_FROM_QUEUED.marker())
            } finally {
                withdrawingForEdit = null
            }
        }
    }

    /** Whether this edit would hand the server exactly what it already holds for [original]. */
    private fun QueuedMessage.isSameServerTurnAs(original: QueuedMessage): Boolean =
        text == original.text &&
            attachments.map { it.fileId } == original.attachments.map { it.fileId } &&
            quotes == original.quotes &&
            manualSkills == original.manualSkills &&
            reasoningOverride == original.reasoningOverride

    /** "Cancel edit": discard composer changes and bring back the stashed new-message draft. The
     *  item never left the queue, so nothing is put back. */
    fun cancelQueuedEdit() {
        val session = handle.state.editingQueuedItem ?: return
        // An Update's withdrawal is in flight; it ends the session itself when it lands.
        if (withdrawingForEdit != null) {
            reportQueueBusy()
            return
        }
        finishQueuedEdit(session)
    }

    /**
     * After the server's answer is folded into the queue: if the row being edited has left it, the
     * server ran (or let go of) the original, so the edit has nothing left to replace. The session
     * ends at once rather than at the next Update, so the editing bar does not stay up over a
     * message that already went. A withdrawal of our own is in flight when [withdrawingForEdit] is
     * set; that path ends the session itself.
     */
    fun onQueueReconciled() {
        val session = handle.state.editingQueuedItem ?: return
        if (withdrawingForEdit != null) return
        if (handle.state.messageQueue.any { it.localId == session.original.localId }) return
        finishQueuedEdit(session)
        handle.setError(ChatNotice.QUEUE_EDIT_OVERTAKEN.marker())
    }

    private fun finishQueuedEdit(session: QueuedEditSession) {
        if (handle.state.editingQueuedItem != session) return
        applyComposer(session.stashed, editSession = null)
        // Draining was frozen during the edit; resume it now if the queue is idle (a reply may
        // have finished while editing).
        tryResumeDrain()
    }

    /** The queue refused a tap because [withdrawingForEdit] holds it; say so. */
    private fun reportQueueBusy() {
        handle.setError(ChatNotice.QUEUE_WITHDRAW_BUSY.marker())
    }

    /** A withdrawal the server declined. Shared, so the × and the tap-to-edit read the same. */
    private fun reportWithdrawRefused() {
        handle.setError(ChatNotice.QUEUE_WITHDRAW_REFUSED.marker())
    }

    fun cancelQueued(localId: String) {
        // Ignore ghost ×/reorder while an edit is in flight, so the queue can't shift under the
        // session's captured originalIndex.
        if (handle.state.isEditingQueued) return
        val item = handle.state.messageQueue.firstOrNull { it.localId == localId }
        if (item?.server == null) {
            // A purely local row is removed by id and talks to nothing, so the withdrawal fence
            // does not apply to it — and must not be consulted BEFORE this branch, or every × on
            // an ordinary queued message during any withdrawal is refused with a message about an
            // operation that row has no part in.
            queueDelegate.cancel(localId)
            return
        }
        // An unconfirmed delivery: its window expired with no id, so nothing can withdraw it and
        // it refuses every drain. Upstream's × dismisses it locally, leaving the server to run it
        // if it did land. Only the × — an edit would resend words the server may already hold.
        if (item.server.status == QueuedTurnServerState.Status.Uncertain &&
            item.server.reconciliationExpired
        ) {
            queueDelegate.cancel(localId)
            tryResumeDrain()
            return
        }
        // A withdrawal is already in flight, which is the same window with `isEditingQueued` not
        // yet set: the rows are still on screen, so a tap here would start a second concurrent
        // DELETE, and whichever lands first leaves the other holding a row the server no longer
        // has. Reported rather than swallowed — the fence is global, so this also refuses a tap on
        // a DIFFERENT server-owned row, and a × that dies silently reads as a broken button.
        if (withdrawingForEdit != null) {
            reportQueueBusy()
            return
        }
        // The server holds this one. Withdraw it there FIRST and drop the local row only on a
        // confirmed cancel — removing it locally on a refused one would hide a turn the server
        // still intends to run.
        //
        // Claimed BEFORE the launch, not only read: this path issues a withdrawal of its own, and
        // the fence it consults is worthless to it unless it also raises it. Without this a second
        // × tap, or an × followed by a tap-to-edit, starts a second DELETE of the same row — and
        // whichever lands second reports a refusal over a withdrawal that actually succeeded.
        withdrawingForEdit = localId
        handle.scope.launch {
            try {
                if (!queuedTurnDelegate.cancel(item)) {
                    // Reported, not swallowed. A refusal is either "the server is past withdrawing
                    // this" or "this row has no id to withdraw" — and the latter is reachable and
                    // sticky: an `Uncertain` row whose reconciliation window has expired will never
                    // be handed one, so its × is a permanent no-op while the row itself refuses
                    // every drain. Silence made that read as a dead button on a row the UI has
                    // already labelled as needing attention.
                    reportWithdrawRefused()
                    return@launch
                }
                // Re-checked after the round trip, not only before it: an edit session opened while
                // the DELETE was out, and dropping a row into it now shifts the slots its captured
                // originalIndex points at — the exact thing the guard above exists to prevent.
                if (handle.state.isEditingQueued) return@launch
                queueDelegate.cancel(localId)
                // The run end that found this row refused to drain; if it was the last server
                // row, the local ones behind it have no other trigger.
                tryResumeDrain()
            } finally {
                withdrawingForEdit = null
            }
        }
    }

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        if (handle.state.isEditingQueued) return
        // Global on purpose, unlike the ×: a withdrawal that lands removes a row and renumbers
        // every index behind it, so a drag started now commits against slots that have moved.
        if (withdrawingForEdit != null) {
            reportQueueBusy()
            return
        }
        // Server-owned rows run in the server's sequence; dragging one would show an order the
        // backend will not honour.
        val queue = handle.state.messageQueue
        if (queue.getOrNull(fromIndex)?.server != null || queue.getOrNull(toIndex)?.server != null) {
            return
        }
        queueDelegate.reorder(fromIndex, toIndex)
    }

    /**
     * "Send queued" control after a Stop/error pause: lift the pause and resume draining.
     *
     * A refused row the server still lists is withdrawn there first, under the same fence as the
     * × — see [QueuedTurnDelegate.withdrawRefused].
     */
    fun sendQueuedNow() {
        val refused = handle.state.messageQueue.firstOrNull {
            it.server?.status == QueuedTurnServerState.Status.Rejected && it.server.id != null
        }
        if (refused == null) {
            queueDelegate.resume()
            return
        }
        if (withdrawingForEdit != null) {
            reportQueueBusy()
            return
        }
        withdrawingForEdit = refused.localId
        handle.scope.launch {
            try {
                // A row that could not be withdrawn stays refused, and resume() says the server
                // still holds one.
                queuedTurnDelegate.withdrawRefused()
                queueDelegate.resume()
            } finally {
                withdrawingForEdit = null
            }
        }
    }

    /** Snapshots the editable composer surface (the new-message draft) for stashing during an edit. */
    private fun captureComposer(): ComposerSnapshot {
        val state = handle.state
        return ComposerSnapshot(
            text = state.inputText,
            attachments = fileHandler.attachedFiles.value,
            choice = state.selection.choice,
            enabledTools = state.enabledTools,
            mcpServerNames = state.selectedMcpServerNames,
            specTools = state.selection.specTools,
            modelParameters = state.modelParameters,
            manualSkills = state.pendingManualSkills,
            skillsToggle = state.skillsToggle,
            reasoningOverride = state.composer.pendingReasoningOverride,
            quotes = state.pendingQuotes,
        )
    }

    /** Writes a [ComposerSnapshot] back onto the composer (text, attachments, model, tools, params,
     *  skill and quote chips, Skills toggle) and starts or ends the queued edit ([editSession]) in the same
     *  emission. The toggle is set in memory only. Because the edit flag flips in the emission that
     *  swaps the spec, `SkillsToggleDelegate` reads it as a swap rather than a spec pick and keeps
     *  the value. The stashed value is still the conversation's own: nothing toggled or picked
     *  during the edit is written to the conversation's slot.
     *  Sets [ChatUiState.inputText] directly rather than via `ChatViewModel.onInputChanged` so swapping composer
     *  contents for an edit never overwrites the persisted on-disk new-message draft. */
    private fun applyComposer(target: ComposerSnapshot, editSession: QueuedEditSession?) {
        fileHandler.restoreAttachedFiles(target.attachments)
        // The config may have changed since capture. An enforced spec that moved to another model
        // takes that spec's preset, as the server sends it.
        val specs = handle.state.modelSpecs
        val restored = target.choice.storedAgainst(specs)
        val parameters = if (restored == target.choice) {
            target.modelParameters
        } else {
            target.modelParameters.acrossSpecChange(target.choice, restored, specs)
        }
        handle.update {
            // Ending an edit: what a recovery put back meanwhile belongs to this draft.
            val snapshot = if (editSession == null) target.withRestore(draftRestore) else target
            if (editSession == null) draftRestore = DraftRestore.NONE
            editingQueuedItem = editSession
            inputText = snapshot.text
            // The snapshot replaces the whole tray, so an undecided batch from before the edit
            // no longer belongs to anything on screen.
            pendingUploadRouting = null
            choice = restored
            enabledTools = snapshot.enabledTools
            selectedMcpServerNames = snapshot.mcpServerNames
            specTools = snapshot.specTools
            modelParameters = parameters
            pendingManualSkills = snapshot.manualSkills
            pendingQuotes = snapshot.quotes
            // An Undo pending from before the swap would restore into the other draft.
            pendingDraftUndo = null
            pendingReasoningOverride = snapshot.reasoningOverride
            skillsToggle = snapshot.skillsToggle
        }
    }

    /** Puts a drained item back at the head after the send gate refused it. */
    fun requeueRefusedDrain(spec: QueuedMessage): Unit = queueDelegate.requeueRefusedDrain(spec)

    fun releaseCompareSetupHold(): Boolean = queueDelegate.releaseCompareSetupHold()

    fun clearCompareSetupHold(): Unit = queueDelegate.clearCompareSetupHold()

    /**
     * Restarts the queued-turn reconcile poll, whose first read is unconditional.
     *
     * Deliberately not gated on the local queue being non-empty: the rows this exists to
     * rediscover are exactly the ones this process does not have.
     */
    fun refreshQueuedTurns() {
        val state = handle.state
        val eligible = state.selectedEndpoint == EndpointConstants.AGENTS &&
            state.gates.serverQueueSupported
        queuedTurnDelegate.ensurePolling(state.conversationId.takeIf { eligible })
    }
}
