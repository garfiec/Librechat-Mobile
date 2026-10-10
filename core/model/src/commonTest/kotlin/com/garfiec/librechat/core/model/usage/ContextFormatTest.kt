package com.garfiec.librechat.core.model.usage

import kotlin.test.Test
import kotlin.test.assertEquals

class ContextFormatTest {

    @Test
    fun tokens_use_compact_notation_with_one_decimal() {
        assertEquals("946", formatTokens(946))
        assertEquals("82.1K", formatTokens(82_100))
        assertEquals("787K", formatTokens(787_000))
        assertEquals("1.3M", formatTokens(1_300_000))
        assertEquals("1M", formatTokens(999_950))
        assertEquals("0", formatTokens(-5))
    }

    /** Every surface rounds, never truncates: 83.3K of 787K is 11%, not 10%. */
    @Test
    fun percent_rounds_like_web_everywhere() {
        assertEquals(11, percentOf(83_300, 787_000))
        assertEquals(100, percentOf(2_000, 1_000))
        assertEquals(0, percentOf(10, 0))
    }
}
