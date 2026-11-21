package com.garfiec.librechat.feature.chat.components

import kotlin.test.Test
import kotlin.test.assertEquals

/** In-chat search counts what is drawn: a `\ui{…}` marker is never text, so it never matches. */
class UiMarkedSearchCountTest {

    @Test
    fun markersContributeNothingAndTheTextAroundThemIsCounted() {
        val text = "first forecast \\ui{forecast_1} second forecast"
        assertEquals(2, countTextPartOccurrences(text, "forecast"))
        assertEquals(2, countUiMarkedOccurrences(text, "forecast"))
    }

    @Test
    fun aMarkerInCodeIsDrawnAndCounted() {
        assertEquals(1, countTextPartOccurrences("`\\ui{forecast}`", "forecast"))
    }
}
