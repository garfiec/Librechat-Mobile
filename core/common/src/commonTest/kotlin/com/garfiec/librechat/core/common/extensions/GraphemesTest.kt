package com.garfiec.librechat.core.common.extensions

import kotlin.test.Test
import kotlin.test.assertEquals

class GraphemesTest {
    @Test
    fun a_plain_letter_is_one_char() {
        assertEquals("G", "GitHub".firstGrapheme())
        assertEquals("", "".firstGrapheme())
    }

    @Test
    fun a_surrogate_pair_emoji_is_kept_whole() {
        assertEquals("🚀", "🚀 Rocket".firstGrapheme())
    }

    @Test
    fun a_flag_keeps_both_regional_indicators() {
        assertEquals("🇺🇸", "🇺🇸 US".firstGrapheme())
    }

    @Test
    fun a_zwj_sequence_and_skin_tone_stay_joined() {
        val family = "👨‍👩‍👧"
        assertEquals(family, "${family}x".firstGrapheme())
        assertEquals("👍🏽", "👍🏽ok".firstGrapheme())
    }

    @Test
    fun a_lone_surrogate_yields_nothing() {
        assertEquals("", "\uDE80abc".firstGrapheme())
    }
}
