package com.garfiec.librechat.core.model.trace

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Turn grouping, which is the one piece of real view logic here.
 *
 * The server returns newest turns first, a turn's records may continue on the NEXT page, and their
 * order within a turn is undefined. Each of those three is a way to render a trace wrong, and all
 * three are invisible until a conversation is long enough to page.
 */
class TraceGroupingTest {

    private fun record(
        id: String,
        messageId: String,
        startTime: String,
        status: String = TraceStatus.OK,
        cost: Double? = null,
        total: Long? = null,
    ) = TraceRecord(
        id = id,
        messageId = messageId,
        startTime = startTime,
        status = status,
        cost = cost,
        usage = total?.let { TraceUsage(total = it) },
    )

    @Test
    fun a_turn_that_spans_two_pages_is_one_turn() {
        // The failure this exists for: rendering each page as it arrives splits one turn across
        // two headings, and nothing about a single page reveals that it happened.
        val page1 = listOf(
            record("r3", "msg-2", "2026-09-18T10:00:02Z"),
            record("r2", "msg-1", "2026-09-18T09:00:01Z"),
        )
        val page2 = listOf(record("r1", "msg-1", "2026-09-18T09:00:00Z"))

        val turns = groupTraceRecords(page1 + page2)

        assertEquals(2, turns.size)
        assertEquals(listOf("r1", "r2"), turns[1].records.map { it.id })
    }

    @Test
    fun records_within_a_turn_are_ordered_by_time_however_they_arrived() {
        val turns = groupTraceRecords(
            listOf(
                record("c", "msg-1", "2026-09-18T09:00:03Z"),
                record("a", "msg-1", "2026-09-18T09:00:01Z"),
                record("b", "msg-1", "2026-09-18T09:00:02Z"),
            ),
        )

        assertEquals(listOf("a", "b", "c"), turns.single().records.map { it.id })
    }

    @Test
    fun turns_are_newest_first() {
        val turns = groupTraceRecords(
            listOf(
                record("r1", "msg-1", "2026-09-18T09:00:00Z"),
                record("r2", "msg-2", "2026-09-18T10:00:00Z"),
            ),
        )

        assertEquals(listOf("msg-2", "msg-1"), turns.map { it.messageId })
    }

    @Test
    fun a_record_repeated_across_pages_appears_once() {
        // An overlapping page, or a re-read after a run settles, must not double a record.
        val turns = groupTraceRecords(
            listOf(
                record("r1", "msg-1", "2026-09-18T09:00:00Z"),
                record("r1", "msg-1", "2026-09-18T09:00:00Z"),
            ),
        )

        assertEquals(1, turns.single().records.size)
    }

    @Test
    fun identical_timestamps_keep_a_stable_order() {
        // Without the id tiebreak two records stamped in the same millisecond can swap between
        // renders, which reads as the trace rearranging itself.
        val first = groupTraceRecords(
            listOf(
                record("b", "msg-1", "2026-09-18T09:00:00Z"),
                record("a", "msg-1", "2026-09-18T09:00:00Z"),
            ),
        )
        val second = groupTraceRecords(
            listOf(
                record("a", "msg-1", "2026-09-18T09:00:00Z"),
                record("b", "msg-1", "2026-09-18T09:00:00Z"),
            ),
        )

        assertEquals(first.single().records.map { it.id }, second.single().records.map { it.id })
    }

    @Test
    fun a_record_with_no_start_sorts_last_rather_than_first() {
        // An absent timestamp says nothing about when it ran, so it must not claim the top.
        val turns = groupTraceRecords(
            listOf(
                record("undated", "msg-1", ""),
                record("dated", "msg-1", "2026-09-18T09:00:00Z"),
            ),
        )

        assertEquals(listOf("dated", "undated"), turns.single().records.map { it.id })
    }

    @Test
    fun a_turn_summarises_what_its_records_reported() {
        val turns = groupTraceRecords(
            listOf(
                record("a", "msg-1", "2026-09-18T09:00:00Z", cost = 0.25, total = 100),
                record("b", "msg-1", "2026-09-18T09:00:01Z", cost = 0.75, total = 50),
                record("c", "msg-1", "2026-09-18T09:00:02Z", status = TraceStatus.ERROR),
            ),
        )
        val turn = turns.single()

        assertEquals(1.0, turn.totalCost)
        assertEquals(150L, turn.totalTokens)
        assertTrue(turn.hasError)
    }

    @Test
    fun an_unpriced_turn_reports_no_cost_rather_than_zero() {
        // Zero would read as "this was free", which is a different claim from "nothing priced it".
        val turn = groupTraceRecords(
            listOf(record("a", "msg-1", "2026-09-18T09:00:00Z")),
        ).single()

        assertNull(turn.totalCost)
        assertNull(turn.totalTokens)
    }

    @Test
    fun a_running_record_reports_no_duration() {
        // It has a start and no end. Timing it from "now" would present a number that grows on
        // every re-read as though it had been measured.
        val running = record("a", "msg-1", "2026-09-18T09:00:00Z", status = TraceStatus.RUNNING)

        assertNull(running.durationMillis { 1_000L })
        assertTrue(groupTraceRecords(listOf(running)).single().isRunning)
    }

    @Test
    fun a_finished_record_reports_the_span_between_its_stamps() {
        val finished = record("a", "msg-1", "start").copy(endTime = "end")
        val clock = mapOf("start" to 1_000L, "end" to 1_250L)

        assertEquals(250L, finished.durationMillis { clock[it] })
        // An unparseable stamp yields nothing rather than a wrong number.
        assertNull(finished.durationMillis { null })
    }
}
