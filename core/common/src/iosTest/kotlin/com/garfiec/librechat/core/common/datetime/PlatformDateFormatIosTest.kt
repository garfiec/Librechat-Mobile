package com.garfiec.librechat.core.common.datetime

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The iOS actuals against real Foundation data. Pins the #445 report (a 24-hour Polish phone saw
 * "8:58 PM") and the old iOS bug of formatting everything as en_US.
 */
class PlatformDateFormatIosTest {

    private val instant = Instant.parse("2026-08-05T20:58:00Z")

    private fun absolute(prefs: DateTimeFormatPrefs, systemIs24Hour: Boolean, locale: String) =
        ResolvedDateTimeFormat.resolve(prefs, systemIs24Hour, locale).formatAbsolute(instant, TimeZone.UTC)

    @Test
    fun polishWith24HourClockHasNoDayPeriod() {
        val shown = absolute(DateTimeFormatPrefs(), systemIs24Hour = true, locale = "pl_PL")
        assertTrue("20:58" in shown, shown)
        assertFalse("PM" in shown, shown)
    }

    @Test
    fun clockOverrideBeatsTheLocaleConvention() {
        val us24 = absolute(DateTimeFormatPrefs(clock = ClockFormat.H24), systemIs24Hour = false, locale = "en_US")
        assertTrue("20:58" in us24, us24)
        val pl12 = absolute(DateTimeFormatPrefs(clock = ClockFormat.H12), systemIs24Hour = true, locale = "pl_PL")
        assertTrue("8:58" in pl12 && "20:58" !in pl12, pl12)
    }

    @Test
    fun fixedNumericStyleIsVerbatim() {
        val prefs = DateTimeFormatPrefs(date = DateFormatStyle.DMY_DOT)
        val shown = absolute(prefs, systemIs24Hour = true, locale = "pl_PL")
        assertEquals("05.08.2026 20:58", shown)
    }

    @Test
    fun monthNamesFollowTheLocaleNotEnUs() {
        val shown = absolute(DateTimeFormatPrefs(date = DateFormatStyle.LONG), systemIs24Hour = true, locale = "pl_PL")
        assertTrue("sierpnia" in shown, shown)
    }
}
