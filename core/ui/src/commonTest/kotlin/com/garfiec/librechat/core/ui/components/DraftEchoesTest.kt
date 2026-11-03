package com.garfiec.librechat.core.ui.components

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DraftEchoesTest {

    @Test
    fun lagging_echoes_of_typing_never_replace_the_field() {
        val echoes = DraftEchoes("")
        echoes.reported("a")
        echoes.reported("ab")

        assertFalse(echoes.isOwnWrite("a", fieldText = "ab"))
        assertFalse(echoes.isOwnWrite("ab", fieldText = "ab"))
    }

    @Test
    fun an_echo_skipped_by_conflation_is_not_mistaken_for_a_write() {
        val echoes = DraftEchoes("")
        echoes.reported("a")
        echoes.reported("ab")
        echoes.reported("abc")

        assertFalse(echoes.isOwnWrite("ab", fieldText = "abc"))
        assertFalse(echoes.isOwnWrite("abc", fieldText = "abc"))
    }

    @Test
    fun a_clear_from_the_view_model_replaces_the_field() {
        val echoes = DraftEchoes("")
        echoes.reported("t")
        echoes.reported("te")
        assertFalse(echoes.isOwnWrite("te", fieldText = "te"))

        assertTrue(echoes.isOwnWrite("", fieldText = "te"))
    }

    @Test
    fun a_text_the_field_never_typed_replaces_the_field() {
        val echoes = DraftEchoes("")
        echoes.reported("x")

        assertTrue(echoes.isOwnWrite("composer answer", fieldText = "x"))
    }

    @Test
    fun an_unchanged_text_from_the_view_model_leaves_the_field_alone() {
        // A ViewModel ignoring an edit (a save in flight) keeps emitting the old text.
        val echoes = DraftEchoes("old")
        echoes.reported("old!")

        assertFalse(echoes.isOwnWrite("old", fieldText = "old!"))
    }

    @Test
    fun a_write_back_to_an_earlier_typed_text_after_catching_up_still_counts() {
        val echoes = DraftEchoes("")
        echoes.reported("a")
        assertFalse(echoes.isOwnWrite("a", fieldText = "a"))
        echoes.reported("ab")
        assertFalse(echoes.isOwnWrite("ab", fieldText = "ab"))

        assertTrue(echoes.isOwnWrite("a", fieldText = "ab"))
    }
}
