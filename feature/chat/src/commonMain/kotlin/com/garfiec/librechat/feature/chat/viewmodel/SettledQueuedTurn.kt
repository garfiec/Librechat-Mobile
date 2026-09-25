package com.garfiec.librechat.feature.chat.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.queuedturn.AgentQueuedTurnReceipt
import com.garfiec.librechat.core.model.queuedturn.QueuedTurnReceiptSource
import com.garfiec.librechat.core.model.queuedturn.QueuedTurnStatus

/**
 * What this client knows about a queued turn that is no longer an ordinary queue row.
 *
 * Kept apart from [QueuedMessage] because the two have different lifetimes: the row goes as soon
 * as the server admits the turn, while the evidence has to stay long enough to fence the local
 * drain against the boundary that admission consumed.
 *
 * The knowledge is a **monotonic lattice** — see [mergeQueuedTurnEvidence]. Nothing here may be
 * weakened by a later, weaker observation, because every weakening is a step back toward treating
 * a committed turn as resendable.
 */
@Immutable
data class SettledQueuedTurn(
    val clientRequestId: String,
    val evidence: Evidence,
    /** The boundary the admission actually consumed; absent until the server reports one. */
    val effectivePredecessorCreatedAt: Long? = null,
    /** The admission consumed no predecessor boundary, so there is none for it to fence. */
    val rootPredecessor: Boolean = false,
    /**
     * This client has already let the admission fence one terminal boundary. Purely local —
     * nothing on the wire says it — and it is what stops a single admission from swallowing
     * every later run end as well.
     */
    val boundaryConsumed: Boolean = false,
) {
    enum class Evidence {
        /** Admitted, with the boundary it consumed known. The only arm that can fence a drain. */
        Admitted,

        /** Admitted, but the server has not yet said which boundary. Still owed an answer. */
        AdmittedPendingBoundary,

        /** The server cannot say whether admission started a run. Never resendable. */
        Indeterminate,

        Cancelled,
        Dead,
    }
}

/** The evidence a receipt carries, or null when it is still an ordinary live queue row. */
fun evidenceFor(receipt: AgentQueuedTurnReceipt): SettledQueuedTurn? = when {
    receipt.status == QueuedTurnStatus.ADMITTED &&
        (receipt.effectivePredecessorCreatedAt != null || receipt.rootPredecessor == true) ->
        SettledQueuedTurn(
            clientRequestId = receipt.clientRequestId,
            evidence = SettledQueuedTurn.Evidence.Admitted,
            effectivePredecessorCreatedAt = receipt.effectivePredecessorCreatedAt,
            rootPredecessor = receipt.rootPredecessor == true,
        )

    receipt.status == QueuedTurnStatus.ADMITTED ->
        SettledQueuedTurn(receipt.clientRequestId, SettledQueuedTurn.Evidence.AdmittedPendingBoundary)

    receipt.isAdmissionIndeterminate ->
        SettledQueuedTurn(receipt.clientRequestId, SettledQueuedTurn.Evidence.Indeterminate)

    receipt.status == QueuedTurnStatus.CANCELLED ->
        SettledQueuedTurn(receipt.clientRequestId, SettledQueuedTurn.Evidence.Cancelled)

    receipt.status == QueuedTurnStatus.DEAD ->
        SettledQueuedTurn(receipt.clientRequestId, SettledQueuedTurn.Evidence.Dead)

    else -> null
}

/**
 * Folds a new observation into what is already known, keeping the stronger of the two.
 *
 * Only two transitions are allowed:
 * - a pending boundary learns which boundary it was;
 * - an indeterminate row is resolved by an AUTHORITATIVE later observation.
 *
 * [QueuedTurnReceiptSource.Enqueue] is excluded from the second because an enqueue response can
 * carry a pre-scheduling snapshot — a replay answering 200 from a row the poll has already
 * observed more recently — so letting it overwrite is how evidence goes backwards.
 */
fun mergeQueuedTurnEvidence(
    existing: SettledQueuedTurn?,
    receipt: AgentQueuedTurnReceipt,
    source: QueuedTurnReceiptSource,
): SettledQueuedTurn? {
    val incoming = evidenceFor(receipt)
    if (existing == null) return incoming
    if (existing.evidence == SettledQueuedTurn.Evidence.AdmittedPendingBoundary &&
        incoming?.evidence == SettledQueuedTurn.Evidence.Admitted
    ) {
        return incoming
    }
    if (existing.evidence == SettledQueuedTurn.Evidence.Indeterminate &&
        source != QueuedTurnReceiptSource.Enqueue &&
        incoming != null &&
        incoming.evidence != SettledQueuedTurn.Evidence.Indeterminate
    ) {
        return incoming
    }
    return existing
}
