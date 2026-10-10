package com.garfiec.librechat.core.model.queuedturn

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QueuedTurnPolicyTest {

    private fun receipt(status: String, failureCode: String? = null) = AgentQueuedTurnReceipt(
        queuedTurnId = "qt-$status",
        clientRequestId = "req-$status",
        status = status,
        failure = failureCode?.let { QueuedTurnFailure(code = it) },
    )

    @Test
    fun an_unsettled_row_keeps_the_poll_running() {
        assertTrue(shouldPollQueuedTurns(listOf(receipt(QueuedTurnStatus.QUEUED))))
        assertTrue(shouldPollQueuedTurns(listOf(receipt(QueuedTurnStatus.CLAIMED))))
    }

    @Test
    fun a_settled_row_stops_it() {
        assertFalse(shouldPollQueuedTurns(listOf(receipt(QueuedTurnStatus.ADMITTED))))
        assertFalse(shouldPollQueuedTurns(listOf(receipt(QueuedTurnStatus.CANCELLED))))
        assertFalse(shouldPollQueuedTurns(listOf(receipt(QueuedTurnStatus.DEAD))))
        assertFalse(shouldPollQueuedTurns(emptyList()))
        assertFalse(shouldPollQueuedTurns(null))
    }

    @Test
    fun an_indeterminate_admission_stops_the_poll_though_it_is_still_claimed() {
        // A two-second loop cannot resolve this row. Polling it would dress permanent quarantine
        // up as ordinary progress; it is refreshed on foreground instead.
        val quarantined = receipt(
            QueuedTurnStatus.CLAIMED,
            QueuedTurnFailure.ADMISSION_INDETERMINATE,
        )

        assertFalse(shouldPollQueuedTurns(listOf(quarantined)))
    }

    @Test
    fun an_expecting_caller_keeps_the_poll_alive_on_an_empty_snapshot() {
        // A snapshot taken while the enqueue is still committing comes back empty. Stopping on
        // that answer leaves a fast successor unnoticed until the next foreground.
        assertTrue(shouldPollQueuedTurns(emptyList(), expectsReceipts = true))
    }

    @Test
    fun an_ambiguous_enqueue_keeps_the_poll_alive_for_its_window() {
        assertTrue(shouldPollQueuedTurns(emptyList(), reconcileUntilMillis = 100, nowMillis = 99))
        assertFalse(shouldPollQueuedTurns(emptyList(), reconcileUntilMillis = 100, nowMillis = 100))
    }

    @Test
    fun an_admitted_row_still_owes_a_run_though_the_poll_has_stopped() {
        // The one way the owed test is wider than the poll test: for a client that is not
        // attached, `admitted` is the strongest evidence a run exists, and the receipt leaves the
        // projection almost immediately afterwards.
        val admitted = listOf(receipt(QueuedTurnStatus.ADMITTED))

        assertFalse(shouldPollQueuedTurns(admitted))
        assertTrue(isQueuedTurnSuccessorOwed(admitted))
    }

    @Test
    fun a_terminal_row_owes_nothing() {
        assertFalse(isQueuedTurnSuccessorOwed(listOf(receipt(QueuedTurnStatus.CANCELLED))))
        assertFalse(isQueuedTurnSuccessorOwed(listOf(receipt(QueuedTurnStatus.DEAD))))
        assertFalse(
            isQueuedTurnSuccessorOwed(
                listOf(receipt(QueuedTurnStatus.CLAIMED, QueuedTurnFailure.ADMISSION_INDETERMINATE)),
            ),
        )
    }

    @Test
    fun the_unsupported_test_reads_an_uncoded_404_only() {
        assertTrue(isDefiniteQueuedTurnsUnsupported(404, null))
        assertFalse(isDefiniteQueuedTurnsUnsupported(404, "CONVERSATION_NOT_FOUND"))
    }

    @Test
    fun a_501_is_unsupported_with_or_without_the_named_codes() {
        assertTrue(isDefiniteQueuedTurnsUnsupported(501, null))
        assertTrue(isDefiniteQueuedTurnsUnsupported(501, QueuedTurnErrorCode.UNSUPPORTED))
        assertTrue(isDefiniteQueuedTurnsUnsupported(501, QueuedTurnErrorCode.PRIORITY_UNSUPPORTED))
        assertFalse(isDefiniteQueuedTurnsUnsupported(501, "SOMETHING_ELSE"))
    }

    @Test
    fun only_a_bounded_4xx_proves_the_row_was_not_committed() {
        assertTrue(isDefiniteQueuedTurnRejection(400, null))
        assertTrue(isDefiniteQueuedTurnRejection(409, QueuedTurnErrorCode.IDEMPOTENCY_CONFLICT))
        assertTrue(isDefiniteQueuedTurnRejection(429, QueuedTurnErrorCode.QUEUE_FULL))

        assertFalse(isDefiniteQueuedTurnRejection(408, null))
        assertFalse(isDefiniteQueuedTurnRejection(425, null))
        assertFalse(isDefiniteQueuedTurnRejection(500, null))
        assertFalse(isDefiniteQueuedTurnRejection(503, QueuedTurnErrorCode.SCHEDULING_PENDING))
        assertFalse(isDefiniteQueuedTurnRejection(null, null))
    }

    @Test
    fun an_unsupported_answer_is_never_also_a_rejection() {
        // The two are read in order and the fallbacks differ: unsupported latches the feature off
        // for the whole server, a rejection hands one row back.
        assertFalse(isDefiniteQueuedTurnRejection(404, null))
        assertFalse(isDefiniteQueuedTurnRejection(501, QueuedTurnErrorCode.UNSUPPORTED))
    }
}
