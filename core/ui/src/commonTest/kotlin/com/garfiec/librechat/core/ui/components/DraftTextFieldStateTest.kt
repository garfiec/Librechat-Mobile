package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.ui.text.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals

class DraftTextFieldStateTest {

    private val reported = mutableListOf<String>()

    private fun DraftTextFieldState.type(text: String) {
        state.setTextAndPlaceCursorAtEnd(text)
        observed(text) { reported += it }
    }

    @Test
    fun the_seeded_text_is_not_reported_as_typing() {
        val field = DraftTextFieldState("draft")
        field.observed("draft") { reported += it }

        assertEquals(emptyList(), reported)
        assertEquals(TextRange(5), field.state.selection)
    }

    @Test
    fun each_edit_is_reported_once() {
        val field = DraftTextFieldState("")
        field.type("h")
        field.type("hi")

        assertEquals(listOf("h", "hi"), reported)
    }

    @Test
    fun a_view_model_write_replaces_the_text_with_the_caret_at_the_end_and_is_not_reported_back() {
        val field = DraftTextFieldState("")
        field.type("hello")
        field.follow("hello")

        field.follow("")
        field.observed(field.state.text.toString()) { reported += it }
        assertEquals("", field.state.text.toString())

        field.follow("restored draft")
        field.observed(field.state.text.toString()) { reported += it }
        assertEquals("restored draft", field.state.text.toString())
        assertEquals(TextRange(14), field.state.selection)
        assertEquals(listOf("hello"), reported)
    }

    @Test
    fun a_lagging_echo_does_not_overwrite_newer_typing() {
        val field = DraftTextFieldState("")
        field.type("a")
        field.type("ab")

        field.follow("a")

        assertEquals("ab", field.state.text.toString())
    }

    @Test
    fun typing_after_a_view_model_write_is_reported() {
        val field = DraftTextFieldState("")
        field.follow("prompt")
        field.observed("prompt") { reported += it }
        field.type("prompt!")

        assertEquals(listOf("prompt!"), reported)
    }
}
