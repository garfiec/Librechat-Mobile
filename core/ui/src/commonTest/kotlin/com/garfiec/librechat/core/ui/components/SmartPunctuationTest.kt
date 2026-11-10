package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class SmartPunctuationTest {

    @Test
    fun quotes_and_dashes_become_what_was_typed() {
        val typed = TextFieldValue("{“a”: ‘it’s’} a—b„c‚", TextRange(20))

        val straight = typed.withoutSmartPunctuation(previous = "")

        assertEquals("{\"a\": 'it's'} a--b\"c'", straight.text)
    }

    @Test
    fun the_caret_after_a_dash_stays_after_both_dashes() {
        val typed = TextFieldValue("a—", TextRange(2))

        val straight = typed.withoutSmartPunctuation(previous = "a-")

        assertEquals("a--", straight.text)
        assertEquals(TextRange(3), straight.selection)
    }

    @Test
    fun a_selection_spanning_a_dash_widens_with_it() {
        val typed = TextFieldValue("x—y—z", TextRange(1, 4))

        assertEquals(TextRange(1, 6), typed.withoutSmartPunctuation(previous = "").selection)
    }

    @Test
    fun plain_text_is_returned_untouched() {
        val typed = TextFieldValue("cron 0 9 * * 1-5", TextRange(4))

        assertSame(typed, typed.withoutSmartPunctuation(previous = "cron 0 9 * * 1-"))
    }

    @Test
    fun text_already_in_the_field_keeps_its_punctuation() {
        val typed = TextFieldValue("{\"d\": \"it’s — fine\"}“", TextRange(21))

        val straight = typed.withoutSmartPunctuation(previous = "{\"d\": \"it’s — fine\"}")

        assertEquals("{\"d\": \"it’s — fine\"}\"", straight.text)
    }

    @Test
    fun the_state_transformation_straightens_only_the_typed_text_and_keeps_the_caret_at_the_end() {
        val state = TextFieldState("it’s ")
        state.edit {
            append("/cmd “x” a—")
            placeCursorAtEnd()
            with(SmartPunctuationTransformation) { transformInput() }
        }

        assertEquals("it’s /cmd \"x\" a--", state.text.toString())
        assertEquals(TextRange(17), state.selection)
    }
}
