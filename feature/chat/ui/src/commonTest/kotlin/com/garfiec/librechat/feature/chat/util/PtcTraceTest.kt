package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.model.StreamEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Upstream `applyPtcToolCall` / `prunePtcTraces` (`client/src/hooks/SSE/useStepHandler.ts`). */
class PtcTraceTest {

    private fun event(callId: String, status: String, args: String? = null, error: String? = null, durationMs: Long? = null) =
        StreamEvent.PtcToolCall(
            toolCallId = "code_1",
            callId = callId,
            name = "search_code_mcp_github",
            status = status,
            args = args,
            error = error,
            durationMs = durationMs,
        )

    @Test
    fun a_start_appends_and_its_settle_updates_the_row_in_place() {
        val started = PtcTrace.EMPTY
            .fold(event("a", "running", args = "q=x"))
            .fold(event("b", "running"))
        val settled = started.fold(event("a", "error", error = "boom", durationMs = 40))
        assertEquals(listOf("a", "b"), settled.entries.map { it.callId })
        val a = settled.entries.first()
        assertEquals(PtcCallStatus.ERROR, a.status)
        // The settle does not restate the args; the start's are kept.
        assertEquals("q=x", a.args)
        assertEquals("boom", a.error)
        assertEquals(40L, a.durationMs)
        assertTrue(settled.isRunning)
    }

    @Test
    fun a_settle_without_its_row_is_dropped() {
        val trace = PtcTrace.EMPTY.fold(event("a", "running"))
        assertSame(trace, trace.fold(event("gone", "success")))
    }

    @Test
    fun an_unknown_status_changes_nothing() {
        val trace = PtcTrace.EMPTY.fold(event("a", "running"))
        assertSame(trace, trace.fold(event("a", "paused")))
    }

    @Test
    fun the_cap_keeps_the_tail_and_counts_what_it_dropped() {
        var trace = PtcTrace.EMPTY
        repeat(PTC_TRACE_MAX_ENTRIES + 3) { trace = trace.fold(event("c$it", "running")) }
        assertEquals(PTC_TRACE_MAX_ENTRIES, trace.entries.size)
        assertEquals(3, trace.dropped)
        assertEquals("c3", trace.entries.first().callId)
        // An evicted row's settle does not come back at the tail.
        assertSame(trace, trace.fold(event("c0", "success")))
    }

    @Test
    fun a_resume_marks_running_rows_interrupted_and_a_late_settle_still_lands() {
        val trace = PtcTrace.EMPTY
            .fold(event("a", "running"))
            .fold(event("b", "running"))
            .fold(event("b", "success"))
        val resumed = trace.interrupted()
        assertEquals(listOf(PtcCallStatus.INTERRUPTED, PtcCallStatus.SUCCESS), resumed.entries.map { it.status })
        assertFalse(resumed.isRunning)
        assertSame(resumed, resumed.interrupted())
        assertEquals(PtcCallStatus.SUCCESS, resumed.fold(event("a", "success")).entries.first().status)
    }
}
