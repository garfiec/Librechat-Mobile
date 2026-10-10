package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.model.queuedturn.AgentQueuedTurnReceipt
import com.garfiec.librechat.core.model.queuedturn.QueuedTurnStatus
import com.garfiec.librechat.feature.chat.viewmodel.QueuedMessage
import com.garfiec.librechat.feature.chat.viewmodel.QueuedTurnServerState
import com.garfiec.librechat.feature.chat.viewmodel.SettledQueuedTurn

/**
 * Rebuilds the follow-up queue from a server projection.
 *
 * The server's answer is authoritative about the rows it names, and — for a full snapshot — about
 * every live row in the conversation, because the list route returns all `queued`/`claimed` and
 * `dead` rows regardless of which ids were asked for. So a snapshot can both *remove* rows this
 * client thinks exist and *introduce* ones it has never seen: a turn queued on another device, or
 * one this process enqueued before it was killed (the queue is memory-only).
 *
 * Two deliberate departures from upstream, both forced by mobile's queue holding more than
 * upstream's:
 *
 * - **A projected row keeps its local counterpart's send config**, and an orphan gets a
 *   placeholder from [projectOrphan]. `endpoint`/`model`/`dispatch`/tools are required here but
 *   are *dead* for a server-owned row: the server runs the turn with the conversation's own
 *   config, and the drain refuses to send it. Making them nullable would be a wide change for a
 *   value nothing reads.
 * - **Ordering is a stable partition, not a full sort.** Server-owned rows come first, ordered by
 *   the server's `revision`; legacy rows keep the order the user put them in. Upstream sorts the
 *   whole list by `createdAt` because web has no manual reorder to preserve.
 */
fun reconcileServerQueuedTurns(
    previous: List<QueuedMessage>,
    receipts: List<AgentQueuedTurnReceipt>,
    settledByRequestId: Map<String, SettledQueuedTurn>,
    authoritativeSnapshot: Boolean,
    nowMillis: Long,
    projectOrphan: (AgentQueuedTurnReceipt) -> QueuedMessage?,
): List<QueuedMessage> {
    val previousByRequestId = previous.mapNotNull { item ->
        item.clientRequestId?.let { it to item }
    }.toMap()
    val observedRequestIds = receipts.map { it.clientRequestId }.toSet()

    val projected = receipts.mapNotNull { receipt ->
        projectReceipt(receipt, previousByRequestId, settledByRequestId, nowMillis, projectOrphan)
    }
    val retained = previous.filter { item ->
        retains(item, settledByRequestId, observedRequestIds, authoritativeSnapshot)
    }
    return order(retained + projected)
}

private fun projectReceipt(
    receipt: AgentQueuedTurnReceipt,
    previousByRequestId: Map<String, QueuedMessage>,
    settledByRequestId: Map<String, SettledQueuedTurn>,
    nowMillis: Long,
    projectOrphan: (AgentQueuedTurnReceipt) -> QueuedMessage?,
): QueuedMessage? {
    val settled = settledByRequestId[receipt.clientRequestId]
    // The turn is gone from the queue's point of view: admitted turns are the reveal's business,
    // cancelled ones are simply withdrawn.
    if (settled?.evidence == SettledQueuedTurn.Evidence.Admitted ||
        settled?.evidence == SettledQueuedTurn.Evidence.Cancelled
    ) {
        return null
    }
    val local = previousByRequestId[receipt.clientRequestId]
    // Stronger evidence already recorded outranks a weaker live-looking receipt, so the row is
    // left exactly as it is rather than being rebuilt back down the lattice.
    if (holdsStrongerEvidence(settled, receipt)) return local

    val boundaryPending = receipt.status == QueuedTurnStatus.ADMITTED
    val status = when {
        receipt.isAdmissionIndeterminate -> QueuedTurnServerState.Status.Indeterminate
        // Admitted with no boundary reported yet: the client cannot tell whether the successor
        // it is owed has started, which is the same uncertainty an unanswered POST leaves.
        boundaryPending -> QueuedTurnServerState.Status.Uncertain
        receipt.status == QueuedTurnStatus.QUEUED -> QueuedTurnServerState.Status.Queued
        receipt.status == QueuedTurnStatus.CLAIMED -> QueuedTurnServerState.Status.Claimed
        else -> QueuedTurnServerState.Status.Rejected
    }
    val base = local ?: projectOrphan(receipt) ?: return null
    return base.copy(
        text = receipt.text,
        clientRequestId = receipt.clientRequestId,
        parentMessageId = receipt.parentMessageId,
        expectedPredecessorCreatedAt = receipt.expectedPredecessorCreatedAt,
        quotes = receipt.quotes ?: base.quotes,
        server = QueuedTurnServerState(
            status = status,
            id = receipt.queuedTurnId,
            revision = receipt.revision,
            position = receipt.position,
            errorCode = receipt.failure?.code,
            errorMessage = receipt.failure?.message,
            uncertainSince = when {
                !boundaryPending -> local?.server?.uncertainSince
                else -> local?.server?.uncertainSince ?: nowMillis
            },
            reconciliationExpired = local?.server?.reconciliationExpired == true,
        ),
    )
}

/**
 * Whether what is already known about this turn outranks the receipt in hand.
 *
 * A `dead` or pending-boundary verdict, or an indeterminate one the receipt does not repeat, is
 * only superseded by a receipt that is itself terminal in the same direction. Anything else would
 * walk a settled turn back to looking live.
 */
private fun holdsStrongerEvidence(
    settled: SettledQueuedTurn?,
    receipt: AgentQueuedTurnReceipt,
): Boolean {
    val blocking = settled?.evidence == SettledQueuedTurn.Evidence.Dead ||
        settled?.evidence == SettledQueuedTurn.Evidence.AdmittedPendingBoundary ||
        (settled?.evidence == SettledQueuedTurn.Evidence.Indeterminate &&
            !receipt.isAdmissionIndeterminate)
    return blocking &&
        receipt.status != QueuedTurnStatus.DEAD &&
        receipt.status != QueuedTurnStatus.ADMITTED
}

/**
 * Whether a row this client holds survives the projection.
 *
 * A legacy row always does — the server has nothing to say about it. A server-owned row the
 * projection did not mention is dropped on an authoritative snapshot **unless** its state is one
 * the server could not have reported: a POST still in flight, an outcome that was never revealed,
 * or a refusal. Those exist only on this client, so a snapshot's silence about them means nothing.
 */
private fun retains(
    item: QueuedMessage,
    settledByRequestId: Map<String, SettledQueuedTurn>,
    observedRequestIds: Set<String>,
    authoritativeSnapshot: Boolean,
): Boolean {
    val server = item.server ?: return true
    val settled = item.clientRequestId?.let { settledByRequestId[it] }
    if (settled?.evidence == SettledQueuedTurn.Evidence.Admitted ||
        settled?.evidence == SettledQueuedTurn.Evidence.Cancelled
    ) {
        return false
    }
    if (settled?.evidence == SettledQueuedTurn.Evidence.Dead ||
        settled?.evidence == SettledQueuedTurn.Evidence.AdmittedPendingBoundary
    ) {
        return item.clientRequestId == null || item.clientRequestId !in observedRequestIds
    }
    // Mentioned by the projection: whatever it said has already been folded in above.
    if (item.clientRequestId == null || item.clientRequestId in observedRequestIds) return false
    if (!authoritativeSnapshot) return true
    return server.status == QueuedTurnServerState.Status.Sending ||
        server.status == QueuedTurnServerState.Status.Uncertain ||
        server.status == QueuedTurnServerState.Status.Indeterminate ||
        server.status == QueuedTurnServerState.Status.Rejected
}

/**
 * Server-owned rows first in the server's own sequence, then the legacy rows untouched.
 *
 * `revision` rather than `position`: position renumbers as predecessors settle, so two rows read
 * in different polls can claim the same one, while the sequence a row was admitted under never
 * moves.
 */
private fun order(items: List<QueuedMessage>): List<QueuedMessage> {
    val (owned, legacy) = items.partition { it.server != null }
    // A row whose POST has not answered has no sequence yet, so it sorts last among the owned —
    // which is also where the user just put it. The sort is stable, so several keep their order.
    return owned.sortedBy { it.server?.revision ?: Long.MAX_VALUE } + legacy
}
