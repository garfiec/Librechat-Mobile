package com.garfiec.librechat.core.model.trace

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Turn grouping and summary, which is the one piece of real view logic here.
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
        kind: String = TraceRecordKind.SPAN,
        parentId: String? = null,
        cost: Double? = null,
        usage: TraceUsage? = null,
    ) = TraceRecord(
        id = id,
        messageId = messageId,
        startTime = startTime,
        status = status,
        kind = kind,
        parentId = parentId,
        cost = cost,
        usage = usage,
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
        // Upstream drops such a record; keeping it is deliberate — on a diagnostic surface the
        // malformed row is the one most likely to be what the user came to look at.
        val turns = groupTraceRecords(
            listOf(
                record("undated", "msg-1", ""),
                record("dated", "msg-1", "2026-09-18T09:00:00Z"),
            ),
        )

        assertEquals(listOf("dated", "undated"), turns.single().records.map { it.id })
    }

    @Test
    fun a_child_nests_under_its_parent() {
        val turn = groupTraceRecords(
            listOf(
                record("tool", "msg-1", "2026-09-18T09:00:01Z", parentId = "agent"),
                record("agent", "msg-1", "2026-09-18T09:00:00Z"),
            ),
        ).single()

        assertEquals(listOf("agent" to 0, "tool" to 1), turn.rows.map { it.record.id to it.depth })
    }

    @Test
    fun a_record_whose_parent_has_not_loaded_is_a_root_rather_than_missing() {
        // Every page but the first cites parents that are still one page away.
        val turn = groupTraceRecords(
            listOf(record("orphan", "msg-1", "2026-09-18T09:00:00Z", parentId = "not-loaded")),
        ).single()

        assertEquals(listOf("orphan" to 0), turn.rows.map { it.record.id to it.depth })
    }

    @Test
    fun a_parent_in_another_turn_does_not_pull_the_record_out_of_its_own() {
        val turns = groupTraceRecords(
            listOf(
                record("a", "msg-1", "2026-09-18T09:00:00Z"),
                record("b", "msg-2", "2026-09-18T10:00:00Z", parentId = "a"),
            ),
        )

        assertEquals(2, turns.size)
        assertEquals(listOf("b" to 0), turns.first().rows.map { it.record.id to it.depth })
    }

    @Test
    fun a_parent_cycle_is_cut_instead_of_looping_forever() {
        // Not paranoia about the server so much as about the cost: a naive walk does not return,
        // and every record still has to appear exactly once.
        val turn = groupTraceRecords(
            listOf(
                record("a", "msg-1", "2026-09-18T09:00:00Z", parentId = "b"),
                record("b", "msg-1", "2026-09-18T09:00:01Z", parentId = "a"),
            ),
        ).single()

        assertEquals(setOf("a", "b"), turn.rows.map { it.record.id }.toSet())
        assertEquals(2, turn.rows.size)
    }

    @Test
    fun a_record_that_is_its_own_parent_is_a_root() {
        val turn = groupTraceRecords(
            listOf(record("a", "msg-1", "2026-09-18T09:00:00Z", parentId = "a")),
        ).single()

        assertEquals(listOf("a" to 0), turn.rows.map { it.record.id to it.depth })
    }

    @Test
    fun a_turn_summarises_what_its_records_reported() {
        val turn = groupTraceRecords(
            listOf(
                generation("a", "2026-09-18T09:00:00Z", cost = 0.25, input = 80, output = 20, total = 100),
                generation("b", "2026-09-18T09:00:01Z", cost = 0.75, input = 30, output = 20, total = 50),
                record(
                    "c",
                    "msg-1",
                    "2026-09-18T09:00:02Z",
                    status = TraceStatus.ERROR,
                    kind = TraceRecordKind.TOOL,
                ),
            ),
        ).single()

        assertEquals(1.0, turn.summary.cost)
        assertEquals(150L, turn.summary.totalTokens)
        assertEquals(110L, turn.summary.inputTokens)
        assertEquals(2, turn.summary.generationCount)
        assertEquals(1, turn.summary.toolCallCount)
        assertEquals(1, turn.summary.errorCount)
    }

    @Test
    fun one_unpriced_generation_withholds_the_whole_cost() {
        // THE case this rule exists for. Summing only what was priced yields a smaller number that
        // looks exactly like a real total, so a model call the backend could not price has to
        // suppress the figure rather than quietly leave itself out of it.
        val turn = groupTraceRecords(
            listOf(
                generation("priced", "2026-09-18T09:00:00Z", cost = 0.25),
                generation("unpriced", "2026-09-18T09:00:01Z", cost = null),
            ),
        ).single()

        assertNull(turn.summary.cost)
    }

    @Test
    fun an_unpriced_tool_does_not_withhold_the_cost() {
        // Only generations are expected to carry a price, so a tool without one is not a gap.
        val turn = groupTraceRecords(
            listOf(
                generation("gen", "2026-09-18T09:00:00Z", cost = 0.25),
                record("tool", "msg-1", "2026-09-18T09:00:01Z", kind = TraceRecordKind.TOOL),
            ),
        ).single()

        assertEquals(0.25, turn.summary.cost)
    }

    @Test
    fun a_generation_without_a_reported_total_is_counted_from_its_halves() {
        val turn = groupTraceRecords(
            listOf(generation("a", "2026-09-18T09:00:00Z", input = 70, output = 30, total = null)),
        ).single()

        assertEquals(100L, turn.summary.totalTokens)
    }

    @Test
    fun an_unpriced_turn_reports_no_cost_rather_than_zero() {
        // Zero would read as "this was free", which is a different claim from "nothing priced it".
        val turn = groupTraceRecords(
            listOf(record("a", "msg-1", "2026-09-18T09:00:00Z")),
        ).single()

        assertNull(turn.summary.cost)
        assertEquals(0L, turn.summary.totalTokens)
    }

    @Test
    fun a_running_record_reports_no_duration() {
        // It has a start and no end. Timing it from "now" would present a number that grows on
        // every re-read as though it had been measured.
        val running = record("a", "msg-1", "2026-09-18T09:00:00Z", status = TraceStatus.RUNNING)

        assertNull(running.durationMillis { 1_000L })
        assertTrue(groupTraceRecords(listOf(running)).single().summary.runningCount > 0)
    }

    @Test
    fun a_finished_record_reports_the_span_between_its_stamps() {
        val finished = record("a", "msg-1", "start").copy(endTime = "end")
        val clock = mapOf("start" to 1_000L, "end" to 1_250L)

        assertEquals(250L, finished.durationMillis { clock[it] })
        // An unparseable stamp yields nothing rather than a wrong number.
        assertNull(finished.durationMillis { null })
    }

    @Test
    fun a_summary_over_several_turns_counts_them() {
        val summary = summarizeTrace(
            listOf(
                record("a", "msg-1", "2026-09-18T09:00:00Z"),
                record("b", "msg-2", "2026-09-18T10:00:00Z"),
            ),
        )

        assertEquals(2, summary.turnCount)
        assertEquals(2, summary.recordCount)
    }

    private fun generation(
        id: String,
        startTime: String,
        cost: Double? = null,
        input: Long? = null,
        output: Long? = null,
        total: Long? = null,
    ) = record(
        id = id,
        messageId = "msg-1",
        startTime = startTime,
        kind = TraceRecordKind.GENERATION,
        cost = cost,
        usage = if (input == null && output == null && total == null) {
            null
        } else {
            TraceUsage(input = input, output = output, total = total)
        },
    )
}
