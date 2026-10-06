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
import com.garfiec.librechat.feature.chat.util.isImageType
import com.garfiec.librechat.feature.chat.viewmodel.ChatRequestBuilder
import com.garfiec.librechat.feature.chat.viewmodel.ComposerSnapshot
import com.garfiec.librechat.feature.chat.viewmodel.QueueOrchestrationHandle
import com.garfiec.librechat.feature.chat.viewmodel.QueuedEditSession
import com.garfiec.librechat.feature.chat.viewmodel.QueuedMessage
import com.garfiec.librechat.feature.chat.viewmodel.QueuedTurnServerState
import com.garfiec.librechat.feature.chat.viewmodel.toComposerSnapshot
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
) {

    fun enqueueNow(text: String) {
        val spec = sendDispatchDelegate.buildSendSpec(text) ?: return
        // Composer-origin queue takes the staged quotes with it (web takeComposerContext): they
        // pair with THIS queued message instead of gluing onto whatever the user sends next.
        val withQuotes = spec.copy(quotes = sendDispatchDelegate.takePendingQuotes(spec.endpoint))
        sendDispatchDelegate.clearComposer()
        enqueueSpec(withQuotes)
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
     * the anchor (the same identity the completion-render keying relies on).
     *
     * `clientRequestId` is minted here and not taken from [QueuedMessage.localId]: localId
     * survives an edit, and reusing an id for different text is a 409.
     */
    private fun serverOwnedSpec(spec: QueuedMessage): QueuedMessage {
        val state = handle.state
        if (!state.gates.serverQueueSupported) return spec
        if (state.selectedEndpoint != EndpointConstants.AGENTS) return spec
        if (!state.isStreaming) return spec
        val parentMessageId = state.displayMessages.lastOrNull()?.message?.messageId ?: return spec
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
            endpoint = state.selectedEndpoint,
            model = state.selectedModel,
            agentId = state.selectedModel.takeIf {
                state.selectedEndpoint == EndpointConstants.AGENTS
            },
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
     * Tap a queued ghost bubble: enter queued-edit mode. Stashes the current new-message draft,
     * pulls the item OUT of the queue, and loads its text + attachments + model/tools/params into
     * the composer for editing. Commit ([commitQueuedEdit]) or cancel ([cancelQueuedEdit]) puts the
     * item back in its slot and restores the stashed draft. Ignored if already editing one.
     */
    fun editQueued(localId: String) {
        if (handle.state.isEditingQueued) return
        // A server-owned edit opens its session only after the withdrawal DELETE returns, so
        // `isEditingQueued` is still false for the whole round trip. A second tap in that window
        // — including a legacy one, which opens its session synchronously and would then be
        // overwritten by the withdrawal landing on top of it — leaves a row out of the queue with
        // nothing that will put it back.
        if (withdrawingForEdit != null) {
            reportQueueBusy()
            return
        }
        // A pick that has not settled yet belongs to the new-message draft. Swapping the composer
        // out from under it re-homes it onto the queued item instead — attaching it to a message
        // the user did not pick it for, and losing it from the one they did, since `captureComposer`
        // cannot stash a file that is not in the tray yet.
        if (uploadIntakeDelegate.hasUnsettledPicks()) {
            Logger.d { "editQueued: refusing — picked files are not settled yet" }
            return
        }
        val index = handle.state.messageQueue.indexOfFirst { it.localId == localId }
        val serverOwned = handle.state.messageQueue.getOrNull(index)?.takeIf { it.server != null }
        if (serverOwned != null) {
            withdrawingForEdit = localId
            // The server holds these words and will run them, so editing in place would leave the
            // original queued behind the edit. Withdraw it first, and only edit if that succeeded.
            handle.scope.launch {
                try {
                    if (!queuedTurnDelegate.cancel(serverOwned)) {
                        // Reported for the same reason the × reports it: the tap looks like it
                        // did nothing, and the row it was aimed at is one the server will still
                        // run. Silence here reads as a dead bubble.
                        reportWithdrawRefused()
                        return@launch
                    }
                    // A real DELETE's receipt already retired the row; a refused one never had an
                    // id to delete and is still sitting there. Take it out either way — leaving it
                    // would put the edit BESIDE the original and block the drain on a row nothing
                    // retires. Its server identity is dropped here and a fresh one minted when the
                    // edit is offered back (see [placeInQueue]). Its slot is re-read here because a
                    // drain may have shifted it meanwhile.
                    val taken = queueDelegate.takeForEdit(localId)
                    beginQueuedEdit(serverOwned.asLegacyRow(), taken?.index ?: index, withdrawnFromServer = true)
                } finally {
                    withdrawingForEdit = null
                }
            }
            return
        }
        val taken = queueDelegate.takeForEdit(localId) ?: return
        beginQueuedEdit(taken.value, taken.index)
    }

    /** Drops every trace of server ownership, leaving a row the local drain may send. */
    private fun QueuedMessage.asLegacyRow(): QueuedMessage = copy(
        server = null,
        clientRequestId = null,
        parentMessageId = null,
        expectedPredecessorCreatedAt = null,
    )

    private fun beginQueuedEdit(item: QueuedMessage, index: Int, withdrawnFromServer: Boolean = false) {
        val stashed = captureComposer()
        applyComposer(item.toComposerSnapshot())
        handle.update {
            editingQueuedItem = QueuedEditSession(
                original = item,
                originalIndex = index,
                stashed = stashed,
                withdrawnFromServer = withdrawnFromServer,
            )
        }
    }

    /** "Update" in queued-edit mode: re-queue the edited item at its original slot (or drop it if
     *  emptied), then restore the stashed new-message draft. Waits for any attachment added during
     *  the edit to finish uploading (same gate as send/queue) so it isn't silently dropped. */
    fun commitQueuedEdit() {
        val session = handle.state.editingQueuedItem ?: return
        uploadIntakeDelegate.withUploadGate(handle.state.inputText.trim()) { text ->
            // The upload wait is async — bail if the edit was cancelled (or replaced) meanwhile,
            // so we don't reinsert a duplicate after cancelQueuedEdit already restored the item.
            if (handle.state.editingQueuedItem != session) return@withUploadGate
            val edited = sendDispatchDelegate.buildSendSpec(text)
                ?.copy(localId = session.original.localId, quotes = session.original.quotes)
            if (edited != null) {
                restoreQueued(session, edited)
            } else {
                // Composer emptied → treat as delete; the item is simply not put back.
                queueDelegate.clearPauseIfEmpty()
            }
            finishQueuedEdit(session)
        }
    }

    /** "Cancel edit": discard composer changes, restore the original item to its slot unchanged,
     *  and bring back the stashed new-message draft. */
    fun cancelQueuedEdit() {
        val session = handle.state.editingQueuedItem ?: return
        restoreQueued(session, session.original)
        finishQueuedEdit(session)
    }

    /**
     * Puts an edited (or un-edited) item back into its slot.
     *
     * One withdrawn from the server goes back to it while the run is still live. Left local, it
     * would be overtaken: every follow-up queued after it is server-owned, the server admits those
     * at the run end, and the drain refuses to send this one until they have all run.
     */
    private fun restoreQueued(session: QueuedEditSession, item: QueuedMessage) {
        if (session.withdrawnFromServer) {
            placeInQueue(item) { queueDelegate.reinsert(session.originalIndex, it) }
        } else {
            queueDelegate.reinsert(session.originalIndex, item)
        }
    }

    private fun finishQueuedEdit(session: QueuedEditSession) {
        applyComposer(session.stashed)
        handle.update { editingQueuedItem = null }
        // Draining was frozen during the edit; resume it now if the queue is idle (a reply may
        // have finished while editing).
        tryResumeDrain()
    }

    /** The queue refused a tap because [withdrawingForEdit] holds it; say so. */
    private fun reportQueueBusy() {
        handle.setError(WITHDRAW_BUSY_MESSAGE)
    }

    /** A withdrawal the server declined. Shared, so the × and the tap-to-edit read the same. */
    private fun reportWithdrawRefused() {
        handle.setError(WITHDRAW_REFUSED_MESSAGE)
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
            endpoint = state.selectedEndpoint,
            model = state.selectedModel,
            enabledTools = state.enabledTools,
            mcpServerNames = state.selectedMcpServerNames,
            modelParameters = state.modelParameters,
        )
    }

    /** Writes a [ComposerSnapshot] back onto the composer (text, attachments, model, tools, params).
     *  Sets [ChatUiState.inputText] directly rather than via `ChatViewModel.onInputChanged` so swapping composer
     *  contents for an edit never overwrites the persisted on-disk new-message draft. */
    private fun applyComposer(snapshot: ComposerSnapshot) {
        fileHandler.restoreAttachedFiles(snapshot.attachments)
        handle.update {
            inputText = snapshot.text
            // The snapshot replaces the whole tray, so an undecided batch from before the edit
            // no longer belongs to anything on screen.
            pendingUploadRouting = null
            selectedEndpoint = snapshot.endpoint
            selectedModel = snapshot.model
            enabledTools = snapshot.enabledTools
            selectedMcpServerNames = snapshot.mcpServerNames
            modelParameters = snapshot.modelParameters
        }
    }

    /** Puts a drained item back at the head after the send gate refused it. */
    fun requeueRefusedDrain(spec: QueuedMessage): Unit = queueDelegate.reinsert(0, spec)

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

    private companion object {
        // Plain strings, like every other message on this `error` channel. `getString(Res.string…)`
        // is not usable from this layer: compose-resources resolves through
        // `Resources.getSystem()`, which is null under this module's plain-JVM unit tests.
        const val WITHDRAW_REFUSED_MESSAGE =
            "Could not withdraw this message from the server."
        const val WITHDRAW_BUSY_MESSAGE =
            "Still finishing the previous withdrawal. Try again in a moment."
    }
}
