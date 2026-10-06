package com.garfiec.librechat.core.common.datetime

import kotlin.test.Test
import kotlin.test.assertEquals

/** Skeleton choice per preference. [bestPattern] is the identity, so a skeleton shows up verbatim. */
class DateTimePatternsTest {

    private fun resolve(
        date: DateFormatStyle = DateFormatStyle.SYSTEM,
        clock: ClockFormat = ClockFormat.SYSTEM,
        systemIs24Hour: Boolean = true,
    ) = resolveDateTimePatterns(DateTimeFormatPrefs(clock = clock, date = date), systemIs24Hour) { "<$it>" }

    @Test
    fun systemClockFollowsDeviceSetting() {
        assertEquals("<Hm>", resolve(systemIs24Hour = true).time)
        assertEquals("<hm>", resolve(systemIs24Hour = false).time)
    }

    @Test
    fun clockOverrideBeatsDeviceSetting() {
        assertEquals("<hm>", resolve(clock = ClockFormat.H12, systemIs24Hour = true).time)
        assertEquals("<Hm>", resolve(clock = ClockFormat.H24, systemIs24Hour = false).time)
        assertEquals("<EEEhm>", resolve(clock = ClockFormat.H12).weekdayTime)
    }

    @Test
    fun localizedStylesUseCombinedSkeletons() {
        val system = resolve(DateFormatStyle.SYSTEM)
        assertEquals("<yMMMd>", system.dateWithYear)
        assertEquals("<MMMd>", system.dateNoYear)
        assertEquals("<yMMMdHm>", system.dateTime)

        assertEquals("<yMdHm>", resolve(DateFormatStyle.SYSTEM_NUMERIC).dateTime)
        assertEquals("<yMMMMdhm>", resolve(DateFormatStyle.LONG, ClockFormat.H12).dateTime)
    }

    @Test
    fun fixedStylesAreVerbatim() {
        val dot = resolve(DateFormatStyle.DMY_DOT)
        assertEquals("dd.MM.yyyy", dot.dateWithYear)
        assertEquals("dd.MM", dot.dateNoYear)
        assertEquals("dd.MM.yyyy <Hm>", dot.dateTime)

        assertEquals("dd/MM", resolve(DateFormatStyle.DMY_SLASH).dateNoYear)
        assertEquals("MM/dd/yyyy <hm>", resolve(DateFormatStyle.MDY_SLASH, ClockFormat.H12).dateTime)
        assertEquals("dd-MM-yyyy", resolve(DateFormatStyle.DMY_DASH).dateWithYear)
        assertEquals("yyyy/MM/dd", resolve(DateFormatStyle.YMD_SLASH).dateWithYear)
        assertEquals("MM/dd", resolve(DateFormatStyle.YMD_SLASH).dateNoYear)
    }

    @Test
    fun yearMonthDayDashNeverDropsTheYear() {
        assertEquals("yyyy-MM-dd", resolve(DateFormatStyle.YMD_DASH).dateNoYear)
    }

    @Test
    fun storageRoundTrips() {
        TimestampStyle.entries.forEach { assertEquals(it, TimestampStyle.fromString(it.toStorageString())) }
        ClockFormat.entries.forEach { assertEquals(it, ClockFormat.fromString(it.toStorageString())) }
        DateFormatStyle.entries.forEach { assertEquals(it, DateFormatStyle.fromString(it.toStorageString())) }
        assertEquals(DateTimeFormatPrefs(), DateTimeFormatPrefs(
            TimestampStyle.fromString(null), ClockFormat.fromString("bogus"), DateFormatStyle.fromString(null),
        ))
    }
}
