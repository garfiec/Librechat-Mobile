package com.garfiec.librechat.feature.schedules.screen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The schedule card renders `nextRunAt` / `firedAt`, which arrive as ISO-8601 UTC wire values.
 * These pin that none of that shape reaches the screen, and that an unparseable value degrades to
 * something readable rather than to blank.
 */
class ScheduleTimestampTest {

    private val wireValue = "2026-09-22T14:00:23.681Z"

    @Test
    fun `a wire timestamp is reformatted for display`() {
        val shown = wireValue.asScheduleTimestamp()
        assertTrue(shown.isNotEmpty(), "a parseable stamp must render as something")
        assertFalse(shown == wireValue, "the raw wire value must not reach the card")
    }

    @Test
    fun `no wire punctuation survives formatting`() {
        val shown = wireValue.asScheduleTimestamp()
        assertFalse(shown.contains('T'), "the date/time separator is wire shape, not display: $shown")
        assertFalse(shown.endsWith("Z"), "the UTC marker is wire shape, not display: $shown")
    }

    /**
     * Falling back to the raw string keeps a diagnosable value on screen. The alternative —
     * [com.garfiec.librechat.core.common.extensions.formatAbsoluteTimestamp]'s empty result — would
     * render "Next run " with nothing after it.
     */
    @Test
    fun `an unparseable value falls back to itself rather than to blank`() {
        assertEquals("not-a-timestamp", "not-a-timestamp".asScheduleTimestamp())
        assertEquals("", "".asScheduleTimestamp())
    }
}
