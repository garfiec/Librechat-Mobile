package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.data.repository.QueuedTurnRepository
import com.garfiec.librechat.core.model.queuedturn.AgentQueuedTurnReceipt
import com.garfiec.librechat.core.model.queuedturn.EnqueueQueuedTurnRequest
import com.garfiec.librechat.core.model.queuedturn.MAX_QUEUED_TURN_LIST_IDS
import com.garfiec.librechat.core.model.queuedturn.QUEUED_TURN_RECONCILIATION_MS
import com.garfiec.librechat.core.model.queuedturn.QueuedTurnFileRef
import com.garfiec.librechat.core.model.queuedturn.QueuedTurnOutcome
import com.garfiec.librechat.core.model.queuedturn.QueuedTurnReceiptSource
import com.garfiec.librechat.core.model.queuedturn.isQueuedTurnSuccessorOwed
import com.garfiec.librechat.core.model.queuedturn.shouldPollQueuedTurns
import com.garfiec.librechat.feature.chat.util.reconcileServerQueuedTurns
import com.garfiec.librechat.feature.chat.viewmodel.QueueHandle
import com.garfiec.librechat.feature.chat.viewmodel.QueuedMessage
import com.garfiec.librechat.feature.chat.viewmodel.QueuedTurnServerState
import com.garfiec.librechat.feature.chat.viewmodel.SettledQueuedTurn
import com.garfiec.librechat.feature.chat.viewmodel.mergeQueuedTurnEvidence
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * Owns the server side of the follow-up queue (v0.8.8-rc2 queued turns): handing a turn to the
 * backend, reconciling what it says back onto the local rows, and withdrawing one.
 *
 * Split from [MessageQueueDelegate], which keeps the list and the drain. The two share
 * [QueueHandle]; this one never sends anything, and the drain never talks to the server.
 *
 * **Everything here exists to stop one failure.** A turn the server has accepted will be run by
 * the server, and there is no server-side guard against this client also sending it. So an
 * outcome that was merely *not observed* — a dropped connection, a 5xx, a 408 — must never be
 * treated as "not committed"; it reconciles by `clientRequestId` instead, which is free because
 * the unique index resolves a re-POST to the same row and the list route mutates nothing.
 */
class QueuedTurnDelegate(
    private val handle: QueueHandle,
    private val repository: QueuedTurnRepository,
    /**
     * Called when the projection proves the server owes this conversation a run it will start
     * itself. The client is not attached to that run, so something has to go and look.
     */
    private val onSuccessorOwed: () -> Unit,
    /**
     * Builds a display row for a receipt this client has never seen — queued on another device,
     * or by a process that has since been killed. Its send config is a placeholder and is never
     * read: the server runs the turn, and the drain refuses server-owned rows.
     */
    private val projectOrphan: (AgentQueuedTurnReceipt) -> QueuedMessage?,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {

    private var pollJob: Job? = null
    private var pollConversationId: String? = null

    /**
     * Turns this client has already gone looking for a run for, as `(clientRequestId, status)`.
     *
     * The status is part of the key because the owed predicate is true from a turn's FIRST
     * sighting, which is `queued` — while this client is still streaming, so the look-up it
     * triggers is a no-op. Keyed on the id alone, the `admitted` receipt that finally needs it is
     * deduped away and the follow-up completes server-side against an idle screen.
     */
    private var announcedOwed: Set<Pair<String, String?>> = emptySet()

    /**
     * Hands [spec] to the server and folds the answer back onto its row.
     *
     * The row is already in the queue, marked `Sending` — server-owned before the POST is even
     * issued, because from the moment the request leaves this client it may have landed.
     */
    fun enqueue(spec: QueuedMessage, conversationId: String) {
        val clientRequestId = spec.clientRequestId ?: return
        val parentMessageId = spec.parentMessageId ?: return
        handle.update {
            queue = queue.copy(
                pendingQueuedTurnEnqueueIds =
                    (queue.pendingQueuedTurnEnqueueIds + clientRequestId).distinct(),
            )
        }
        handle.scope.launch {
            val outcome = repository.enqueue(
                EnqueueQueuedTurnRequest(
                    conversationId = conversationId,
                    parentMessageId = parentMessageId,
                    clientRequestId = clientRequestId,
                    text = spec.text,
                    files = spec.attachments.mapNotNull { file ->
                        file.fileId?.let { QueuedTurnFileRef(fileId = it) }
                    }.ifEmpty { null },
                    quotes = spec.quotes.ifEmpty { null },
                    expectedPredecessorCreatedAt = spec.expectedPredecessorCreatedAt,
                ),
            )
            // The poll can resolve this id before the POST's own answer arrives. Its evidence is
            // the authoritative one, so a late failure must not overwrite it — this is the
            // ordinary case for a request whose response was simply lost.
            val alreadySettled = finishEnqueue(clientRequestId)
            when {
                outcome is QueuedTurnOutcome.Committed ->
                    applyReceipts(listOf(outcome.value), QueuedTurnReceiptSource.Enqueue)
                alreadySettled -> Unit
                // The route is not there. The words were never handed over, so the row goes back
                // to the legacy drain exactly as it would have behaved before this feature.
                outcome is QueuedTurnOutcome.Unsupported -> releaseToLegacy(clientRequestId)
                outcome is QueuedTurnOutcome.Rejected -> markServer(clientRequestId) {
                    it.copy(
                        status = QueuedTurnServerState.Status.Rejected,
                        errorCode = outcome.code,
                        errorMessage = outcome.message,
                    )
                }
                // Unknown, and it must stay that way until exact evidence arrives. The poll is
                // already running; `uncertainSince` is what bounds how long it keeps looking.
                outcome is QueuedTurnOutcome.Indeterminate -> markServer(clientRequestId) {
                    it.copy(
                        status = QueuedTurnServerState.Status.Uncertain,
                        uncertainSince = it.uncertainSince ?: nowMillis(),
                        errorCode = outcome.code,
                        errorMessage = outcome.message,
                    )
                }
            }
            ensurePolling(conversationId)
        }
        ensurePolling(conversationId)
    }

    /**
     * Folds a server projection onto the local rows and the settled-evidence store, in ONE state
     * write — the queue and the evidence that fences the drain must never be observable apart.
     */
    fun applyReceipts(receipts: List<AgentQueuedTurnReceipt>, source: QueuedTurnReceiptSource) {
        handle.update {
            val bySettled = queue.settledQueuedTurns.associateBy { it.clientRequestId }.toMutableMap()
            for (receipt in receipts) {
                val merged = mergeQueuedTurnEvidence(bySettled[receipt.clientRequestId], receipt, source)
                if (merged != null) bySettled[receipt.clientRequestId] = merged
            }
            val pending = queue.pendingQueuedTurnEnqueueIds
            // Evidence is kept only while it can still do work: an admission that has not yet
            // fenced its boundary, or a row whose POST is still out. Everything else would grow
            // without bound for the ViewModel's life.
            val retainedEvidence = bySettled.values.filter {
                // Keyed on the boundary itself, not on a root flag: a receipt can report a
                // consumed boundary AND `rootPredecessor`, and dropping that one leaves the drain
                // with nothing to match against the run end the admission really did consume.
                (it.evidence == SettledQueuedTurn.Evidence.Admitted &&
                    it.effectivePredecessorCreatedAt != null &&
                    !it.boundaryConsumed) ||
                    it.clientRequestId in pending
            }
            queue = queue.copy(
                settledQueuedTurns = retainedEvidence,
                messageQueue = reconcileServerQueuedTurns(
                    previous = queue.messageQueue,
                    receipts = receipts,
                    settledByRequestId = bySettled,
                    authoritativeSnapshot = source == QueuedTurnReceiptSource.Snapshot,
                    nowMillis = nowMillis(),
                    projectOrphan = projectOrphan,
                ),
            )
        }
        // On the TURNS newly owed, not on "anything is owed". The owed predicate is a keep-alive
        // test — it stays true for every tick a row is queued — so firing on it would spend a
        // `/chat/status` GET every two seconds on an idle client for no new information.
        val owed = receipts.filter { isQueuedTurnSuccessorOwed(listOf(it)) }
            .map { it.clientRequestId to it.status }
            .toSet()
        val fresh = owed - announcedOwed
        // Only a SNAPSHOT speaks for every live row, so only a snapshot may retire ids from the
        // memory. A single-receipt enqueue/cancel answer that replaced it wholesale would forget
        // the turns already announced, and the very next poll would re-announce them — spending
        // the `/chat/status` GET this dedupe exists to avoid.
        announcedOwed = if (source == QueuedTurnReceiptSource.Snapshot) owed else announcedOwed + owed
        if (fresh.isNotEmpty()) onSuccessorOwed()
    }

    /**
     * Withdraws a queued turn. Returns false when it could not be, leaving the row in place.
     *
     * A row with no [QueuedTurnServerState.id] has no address to withdraw, and there is exactly
     * ONE state where that means it is safe to drop: [QueuedTurnServerState.Status.Rejected], whose
     * bounded refusal proves nothing was committed. `Sending`, `Uncertain` and `Indeterminate` have
     * no id either and mean the opposite — the POST may well have landed — so removing one locally
     * is how the server ends up running a turn nothing on screen knows about.
     */
    suspend fun cancel(item: QueuedMessage): Boolean {
        val server = item.server ?: return true
        val queuedTurnId = server.id
            ?: return server.status == QueuedTurnServerState.Status.Rejected
        return when (val outcome = repository.cancel(queuedTurnId)) {
            is QueuedTurnOutcome.Committed -> {
                // Applied as an ENQUEUE-source observation, not a snapshot: this receipt speaks
                // for one row. Treated as authoritative it would retire every other server-owned
                // row it does not mention, and until the next poll the drain would see an
                // unowned queue and send into a boundary the server still holds.
                applyReceipts(listOf(outcome.value), QueuedTurnReceiptSource.Enqueue)
                true
            }
            // The row is gone from a server that never had it; nothing is owed either way.
            QueuedTurnOutcome.Unsupported -> true
            // A bounded refusal is the server saying the turn is past withdrawing. Reporting
            // success would drop the row locally while the server still runs it.
            is QueuedTurnOutcome.Rejected -> false
            is QueuedTurnOutcome.Indeterminate -> false
        }
    }

    /**
     * Starts or stops the reconcile poll for [conversationId].
     *
     * Also the reconnect path: there is no push channel for queued turns, so a foreground, an
     * open, or a process that was killed mid-enqueue all recover through the same read. The list
     * route returns every live row for the conversation, not only the ids asked for, so a queue
     * that outlived this process is discovered rather than lost.
     */
    fun ensurePolling(conversationId: String?) {
        if (conversationId == null) {
            stopPolling()
            return
        }
        if (pollJob?.isActive == true && pollConversationId == conversationId) return
        stopPolling()
        pollConversationId = conversationId
        pollJob = handle.scope.launch {
            if (repository.isUnsupported()) return@launch
            // The FIRST read is unconditional, and it is the whole reconnect story: this client's
            // queue is memory-only while the server's is not, so a restart, a second device, or a
            // conversation opened fresh has nothing local to decide from. The list route answers
            // with every live row for the conversation, not only the ids asked for.
            var probe = true
            while (isActive) {
                expireStaleUncertainty()
                if (!probe && !shouldKeepPolling()) return@launch
                probe = false
                when (val outcome = repository.list(conversationId, knownRequestIds())) {
                    is QueuedTurnOutcome.Committed ->
                        applyReceipts(outcome.value, QueuedTurnReceiptSource.Snapshot)
                    // Nothing local can be concluded from a failed read: a row stays exactly as
                    // it was and the next tick asks again.
                    QueuedTurnOutcome.Unsupported -> return@launch
                    is QueuedTurnOutcome.Rejected -> Unit
                    is QueuedTurnOutcome.Indeterminate -> Unit
                }
                delay(POLL_INTERVAL)
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
        pollConversationId = null
    }

    private fun shouldKeepPolling(): Boolean {
        val state = handle.state
        val owned = state.messageQueue.mapNotNull { it.server }
        val reconcileUntil = owned.mapNotNull { server ->
            server.uncertainSince?.takeIf { !server.reconciliationExpired }
                ?.plus(QUEUED_TURN_RECONCILIATION_MS)
        }.maxOrNull()
        // `expectsReceipts`: a snapshot taken while an enqueue is still committing comes back
        // empty, and stopping on that answer would leave a fast successor unnoticed until the
        // next foreground.
        val expectsReceipts = state.pendingQueuedTurnEnqueueIds.isNotEmpty() ||
            owned.any {
                it.status == QueuedTurnServerState.Status.Sending ||
                    it.status == QueuedTurnServerState.Status.Queued ||
                    it.status == QueuedTurnServerState.Status.Claimed ||
                    (it.status == QueuedTurnServerState.Status.Uncertain && !it.reconciliationExpired)
            }
        return shouldPollQueuedTurns(
            receipts = null,
            reconcileUntilMillis = reconcileUntil,
            nowMillis = nowMillis(),
            expectsReceipts = expectsReceipts,
        )
    }

    /**
     * Marks an ambiguous row as past the reconciliation window.
     *
     * It does NOT become resendable — the outcome is still unknown. It stops being polled for,
     * and is held for the user to deal with.
     */
    private fun expireStaleUncertainty() {
        val now = nowMillis()
        val expired = handle.state.messageQueue.any { item ->
            val server = item.server ?: return@any false
            server.status == QueuedTurnServerState.Status.Uncertain &&
                !server.reconciliationExpired &&
                server.uncertainSince != null &&
                server.uncertainSince + QUEUED_TURN_RECONCILIATION_MS <= now
        }
        if (!expired) return
        handle.update {
            queue = queue.copy(
                messageQueue = queue.messageQueue.map { item ->
                    val server = item.server ?: return@map item
                    if (server.status != QueuedTurnServerState.Status.Uncertain ||
                        server.reconciliationExpired ||
                        server.uncertainSince == null ||
                        server.uncertainSince + QUEUED_TURN_RECONCILIATION_MS > now
                    ) {
                        item
                    } else {
                        item.copy(server = server.copy(reconciliationExpired = true))
                    }
                },
            )
        }
    }

    private fun knownRequestIds(): List<String> =
        handle.state.messageQueue
            .mapNotNull { item -> item.clientRequestId?.takeIf { item.server != null } }
            .distinct()
            .take(MAX_QUEUED_TURN_LIST_IDS)

    /** Drops the id from the in-flight set, reporting whether the poll had already settled it. */
    private fun finishEnqueue(clientRequestId: String): Boolean {
        val settled = handle.state.settledQueuedTurns.any { it.clientRequestId == clientRequestId }
        handle.update {
            queue = queue.copy(
                pendingQueuedTurnEnqueueIds =
                    queue.pendingQueuedTurnEnqueueIds.filterNot { it == clientRequestId },
            )
        }
        return settled
    }

    private fun releaseToLegacy(clientRequestId: String) {
        handle.update {
            queue = queue.copy(
                messageQueue = queue.messageQueue.map { item ->
                    if (item.clientRequestId != clientRequestId) {
                        item
                    } else {
                        item.copy(
                            server = null,
                            clientRequestId = null,
                            parentMessageId = null,
                            expectedPredecessorCreatedAt = null,
                        )
                    }
                },
            )
        }
    }

    private fun markServer(
        clientRequestId: String,
        transform: (QueuedTurnServerState) -> QueuedTurnServerState,
    ) {
        handle.update {
            queue = queue.copy(
                messageQueue = queue.messageQueue.map { item ->
                    val server = item.server
                    if (item.clientRequestId != clientRequestId || server == null) {
                        item
                    } else {
                        item.copy(server = transform(server))
                    }
                },
            )
        }
    }

    private companion object {
        /** Upstream's `refetchInterval`. */
        val POLL_INTERVAL = 2.seconds
    }
}
