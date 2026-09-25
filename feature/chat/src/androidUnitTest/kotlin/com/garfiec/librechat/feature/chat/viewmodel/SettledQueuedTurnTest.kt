package com.garfiec.librechat.feature.chat.viewmodel

import com.garfiec.librechat.core.model.queuedturn.AgentQueuedTurnReceipt
import com.garfiec.librechat.core.model.queuedturn.QueuedTurnFailure
import com.garfiec.librechat.core.model.queuedturn.QueuedTurnReceiptSource
import com.garfiec.librechat.core.model.queuedturn.QueuedTurnStatus
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The evidence lattice. Every allowed transition makes what this client knows about a queued turn
 * stronger; a weakening would take a committed turn back toward being treated as resendable.
 */
class SettledQueuedTurnTest {

    private fun receipt(
        status: String,
        effectivePredecessorCreatedAt: Long? = null,
        rootPredecessor: Boolean? = null,
        failureCode: String? = null,
    ) = AgentQueuedTurnReceipt(
        queuedTurnId = "qt-1",
        clientRequestId = "req-1",
        status = status,
        effectivePredecessorCreatedAt = effectivePredecessorCreatedAt,
        rootPredecessor = rootPredecessor,
        failure = failureCode?.let { QueuedTurnFailure(code = it) },
    )

    @Test
    fun `a live row carries no evidence`() {
        assertThat(evidenceFor(receipt(QueuedTurnStatus.QUEUED))).isNull()
        assertThat(evidenceFor(receipt(QueuedTurnStatus.CLAIMED))).isNull()
    }

    @Test
    fun `an admission without a reported boundary is only pending`() {
        val evidence = evidenceFor(receipt(QueuedTurnStatus.ADMITTED))

        assertThat(evidence?.evidence)
            .isEqualTo(SettledQueuedTurn.Evidence.AdmittedPendingBoundary)
    }

    @Test
    fun `a root admission is complete evidence despite having no boundary`() {
        // It consumed none, so there is nothing left to learn — and nothing for it to fence.
        val evidence = evidenceFor(receipt(QueuedTurnStatus.ADMITTED, rootPredecessor = true))

        assertThat(evidence?.evidence).isEqualTo(SettledQueuedTurn.Evidence.Admitted)
        // No boundary to fence, which is also what keeps it out of the retained evidence.
        assertThat(evidence?.effectivePredecessorCreatedAt).isNull()
    }

    @Test
    fun `a pending boundary is completed by a later report`() {
        val pending = evidenceFor(receipt(QueuedTurnStatus.ADMITTED))

        val merged = mergeQueuedTurnEvidence(
            pending,
            receipt(QueuedTurnStatus.ADMITTED, effectivePredecessorCreatedAt = 100L),
            QueuedTurnReceiptSource.Snapshot,
        )

        assertThat(merged?.evidence).isEqualTo(SettledQueuedTurn.Evidence.Admitted)
        assertThat(merged?.effectivePredecessorCreatedAt).isEqualTo(100L)
    }

    @Test
    fun `an authoritative snapshot resolves an indeterminate admission`() {
        val indeterminate = evidenceFor(
            receipt(
                QueuedTurnStatus.CLAIMED,
                failureCode = QueuedTurnFailure.ADMISSION_INDETERMINATE,
            ),
        )

        val merged = mergeQueuedTurnEvidence(
            indeterminate,
            receipt(QueuedTurnStatus.CANCELLED),
            QueuedTurnReceiptSource.Snapshot,
        )

        assertThat(merged?.evidence).isEqualTo(SettledQueuedTurn.Evidence.Cancelled)
    }

    @Test
    fun `an enqueue reply may not resolve an indeterminate admission`() {
        // A replay POST can answer from a pre-scheduling snapshot — older than what the poll has
        // already seen — so letting it overwrite is exactly how the evidence goes backwards.
        val indeterminate = evidenceFor(
            receipt(
                QueuedTurnStatus.CLAIMED,
                failureCode = QueuedTurnFailure.ADMISSION_INDETERMINATE,
            ),
        )

        val merged = mergeQueuedTurnEvidence(
            indeterminate,
            receipt(QueuedTurnStatus.CANCELLED),
            QueuedTurnReceiptSource.Enqueue,
        )

        assertThat(merged?.evidence).isEqualTo(SettledQueuedTurn.Evidence.Indeterminate)
    }

    @Test
    fun `settled evidence is never replaced by a live row`() {
        val admitted = evidenceFor(
            receipt(QueuedTurnStatus.ADMITTED, effectivePredecessorCreatedAt = 100L),
        )

        val merged = mergeQueuedTurnEvidence(
            admitted,
            receipt(QueuedTurnStatus.QUEUED),
            QueuedTurnReceiptSource.Snapshot,
        )

        assertThat(merged?.evidence).isEqualTo(SettledQueuedTurn.Evidence.Admitted)
        assertThat(merged?.effectivePredecessorCreatedAt).isEqualTo(100L)
    }

    @Test
    fun `terminal evidence is recorded for cancelled and dead`() {
        assertThat(evidenceFor(receipt(QueuedTurnStatus.CANCELLED))?.evidence)
            .isEqualTo(SettledQueuedTurn.Evidence.Cancelled)
        assertThat(evidenceFor(receipt(QueuedTurnStatus.DEAD))?.evidence)
            .isEqualTo(SettledQueuedTurn.Evidence.Dead)
    }
}
