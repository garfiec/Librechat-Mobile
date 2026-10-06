package com.garfiec.librechat.feature.schedules.screen

import com.garfiec.librechat.core.common.datetime.DateTimeFormatPrefs
import com.garfiec.librechat.core.common.datetime.DateTimePatterns
import com.garfiec.librechat.core.common.datetime.ResolvedDateTimeFormat
import kotlinx.datetime.toLocalDateTime
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

    // Platform date formatting isn't available under JVM unit tests; this stand-in renders the
    // local date and time the way a display format would, so the assertions are about which value
    // reaches the card, not about locale data.
    private val format = ResolvedDateTimeFormat(
        prefs = DateTimeFormatPrefs(),
        patterns = DateTimePatterns("", "", "", "", "", ""),
        localeTag = "en-US",
        formatter = { instant, _, _, zone ->
            instant.toLocalDateTime(zone).let { "${it.date} ${it.time}" }
        },
    )

    @Test
    fun `a wire timestamp is reformatted for display`() {
        val shown = wireValue.asScheduleTimestamp(format)
        assertTrue(shown.isNotEmpty(), "a parseable stamp must render as something")
        assertFalse(shown == wireValue, "the raw wire value must not reach the card")
    }

    @Test
    fun `no wire punctuation survives formatting`() {
        val shown = wireValue.asScheduleTimestamp(format)
        assertFalse(shown.contains('T'), "the date/time separator is wire shape, not display: $shown")
        assertFalse(shown.endsWith("Z"), "the UTC marker is wire shape, not display: $shown")
    }

    /**
     * Falling back to the raw string keeps a diagnosable value on screen. The alternative — an
     * empty result — would render "Next run " with nothing after it.
     */
    @Test
    fun `an unparseable value falls back to itself rather than to blank`() {
        assertEquals("not-a-timestamp", "not-a-timestamp".asScheduleTimestamp(format))
        assertEquals("", "".asScheduleTimestamp(format))
    }
}
