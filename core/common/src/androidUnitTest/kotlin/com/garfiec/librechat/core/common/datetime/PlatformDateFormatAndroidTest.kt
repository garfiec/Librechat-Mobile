package com.garfiec.librechat.core.common.datetime

import kotlinx.datetime.TimeZone
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The Android actuals against real ICU data (Robolectric), at the app's minSdk — the level whose
 * ICU is oldest. Pins the #445 report itself: a Polish-locale phone set to 24-hour saw "8:58 PM".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class PlatformDateFormatAndroidTest {

    private val instant = Instant.parse("2026-08-05T20:58:00Z")

    private fun absolute(prefs: DateTimeFormatPrefs, systemIs24Hour: Boolean, locale: String) =
        ResolvedDateTimeFormat.resolve(prefs, systemIs24Hour, locale).formatAbsolute(instant, TimeZone.UTC)

    @Test
    fun polishWith24HourClockHasNoDayPeriod() {
        val shown = absolute(DateTimeFormatPrefs(), systemIs24Hour = true, locale = "pl-PL")
        assertTrue("20:58" in shown, shown)
        assertFalse("PM" in shown, shown)
    }

    @Test
    fun clockOverrideBeatsTheLocaleConvention() {
        val us24 = absolute(DateTimeFormatPrefs(clock = ClockFormat.H24), systemIs24Hour = false, locale = "en-US")
        assertTrue("20:58" in us24, us24)
        val pl12 = absolute(DateTimeFormatPrefs(clock = ClockFormat.H12), systemIs24Hour = true, locale = "pl-PL")
        assertTrue("8:58" in pl12 && "20:58" !in pl12, pl12)
    }

    @Test
    fun fixedNumericStyleIsVerbatim() {
        val prefs = DateTimeFormatPrefs(date = DateFormatStyle.DMY_DOT)
        val shown = absolute(prefs, systemIs24Hour = true, locale = "pl-PL")
        assertEquals("05.08.2026 20:58", shown)
    }

    @Test
    fun monthNamesFollowTheLocale() {
        val shown = absolute(DateTimeFormatPrefs(date = DateFormatStyle.LONG), systemIs24Hour = true, locale = "pl-PL")
        assertTrue("sierpnia" in shown, shown)
    }

    @Test
    fun dayPeriodSkeletonsFormatWithoutThrowing() {
        // zh's 12-hour pattern uses ICU's flexible day period (`B`), which java.time rejects on API 26.
        val shown = absolute(DateTimeFormatPrefs(clock = ClockFormat.H12), systemIs24Hour = false, locale = "zh-CN")
        assertTrue(shown.isNotBlank())
    }
}
