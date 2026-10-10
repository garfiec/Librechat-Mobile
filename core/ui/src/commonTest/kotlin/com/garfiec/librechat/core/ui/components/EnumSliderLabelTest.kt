package com.garfiec.librechat.core.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The enum slider's value label must say what the request does. "Unset" is true only of a value
 * that is not sent: the empty option, or one a per-model rule took away. A value outside the options
 * for any other reason is sent as itself, and must be named as itself.
 */
class EnumSliderLabelTest {

    private val options = listOf("", "low", "medium", "high")

    private fun label(value: String, removed: Set<String> = emptySet()) =
        enumSliderLabel(value, options, optionLabels = null, removedValues = removed)

    @Test
    fun anOfferedValueIsNamedByItsOption() {
        assertEquals("medium", label("medium"))
        assertEquals("Unset", label(""))
    }

    @Test
    fun aValueAPerModelRuleTookAwayReadsUnset() {
        assertEquals("Unset", label("minimal", removed = setOf("minimal")))
    }

    /** `max` before the server version is detected, or a value newer than the app: sent, so named. */
    @Test
    fun aKeptOutOfListValueIsNamedAsItself() {
        assertEquals("max", label("max", removed = setOf("minimal")))
        assertEquals("ultra", label("ultra"))
    }

    @Test
    fun optionLabelsStillApply() {
        assertEquals(
            "Off",
            enumSliderLabel("", options, optionLabels = mapOf("" to "Off"), removedValues = emptySet()),
        )
    }
}
