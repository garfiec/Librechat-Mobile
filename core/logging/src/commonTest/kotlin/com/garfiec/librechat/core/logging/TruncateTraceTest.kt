package com.garfiec.librechat.core.logging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TruncateTraceTest {

    private val deepTrace = buildString {
        appendLine("kotlin.IllegalStateException: LayoutNode should be attached to an owner")
        repeat(2000) { appendLine("    at 0x$it measure recursion") }
        append("    at entryPoint_layoutSubviews")
    }

    @Test
    fun a_short_trace_is_untouched() {
        assertEquals("short", truncateTrace("short", maxChars = 100, keepTail = true))
    }

    @Test
    fun a_crash_trace_keeps_both_the_throw_site_and_the_entry_point() {
        val out = truncateTrace(deepTrace, maxChars = 1000, keepTail = true)

        assertTrue(out.startsWith("kotlin.IllegalStateException: LayoutNode should be attached"), out)
        assertTrue(out.endsWith("entryPoint_layoutSubviews"), out)
        assertTrue(out.contains("…(${deepTrace.length - 1000} chars truncated)…"), out)
    }

    @Test
    fun an_ordinary_trace_keeps_only_its_head() {
        val out = truncateTrace(deepTrace, maxChars = 1000, keepTail = false)

        assertEquals(deepTrace.take(1000) + "…(truncated)", out)
    }
}
