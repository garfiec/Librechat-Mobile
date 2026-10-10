package com.garfiec.librechat.core.common.datetime

import com.garfiec.librechat.core.common.extensions.RelativeTimeReference
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Bucket and pattern *selection*. The fake formatter echoes the chosen pattern instead of rendering
 * it, because platform date formatting is locale data, not logic, and isn't available under JVM
 * unit tests anyway.
 */
class TimestampLabelsTest {

    private val now = Instant.parse("2026-07-19T12:00:00Z")
    private val reference = RelativeTimeReference(now, TimeZone.UTC)

    private val patterns = DateTimePatterns(
        dateWithYear = "DATE_Y",
        dateNoYear = "DATE",
        time = "TIME",
        weekdayTime = "WEEKDAY_TIME",
        dateTime = "DATE_TIME",
        monthYear = "MONTH_YEAR",
    )

    private fun format(style: TimestampStyle) = ResolvedDateTimeFormat(
        prefs = DateTimeFormatPrefs(style = style),
        patterns = patterns,
        localeTag = "en-US",
        formatter = { _, pattern, _, _ -> pattern },
    )

    private val relative = format(TimestampStyle.RELATIVE)
    private val smart = format(TimestampStyle.SMART)
    private val absolute = format(TimestampStyle.ABSOLUTE)

    private fun Instant.message(f: ResolvedDateTimeFormat) = messageLabel(f, reference)

    @Test
    fun relativeBuckets() {
        assertEquals(TimestampLabel.JustNow, (now - 59.seconds).message(relative))
        assertEquals(TimestampLabel.MinutesAgo(1), (now - 1.minutes).message(relative))
        assertEquals(TimestampLabel.MinutesAgo(59), (now - 59.minutes).message(relative))
        assertEquals(TimestampLabel.HoursAgo(23), (now - 23.hours).message(relative))
        assertEquals(TimestampLabel.DaysAgo(6), (now - 6.days).message(relative))
        assertEquals(TimestampLabel.WeeksAgo(1), (now - 7.days).message(relative))
        assertEquals(TimestampLabel.WeeksAgo(4), (now - 29.days).message(relative))
        assertEquals(TimestampLabel.Text("DATE_TIME"), (now - 30.days).message(relative))
    }

    @Test
    fun relativeFutureReadsAsJustNow() {
        assertEquals(TimestampLabel.JustNow, (now + 5.minutes).message(relative))
    }

    @Test
    fun absoluteIsAlwaysDateTime() {
        assertEquals(TimestampLabel.Text("DATE_TIME"), (now - 1.minutes).message(absolute))
        assertEquals(TimestampLabel.Text("DATE_TIME"), (now - 400.days).message(absolute))
    }

    @Test
    fun smartUsesCalendarDaysNotElapsedTime() {
        // 11:59 today and 00:01 today are both "today", however far apart.
        assertEquals(TimestampLabel.Text("TIME"), Instant.parse("2026-07-19T00:01:00Z").message(smart))
        // Two minutes before midnight is yesterday even though only ~12h have elapsed.
        assertEquals(TimestampLabel.Yesterday("TIME"), Instant.parse("2026-07-18T23:58:00Z").message(smart))
        assertEquals(TimestampLabel.Text("WEEKDAY_TIME"), Instant.parse("2026-07-17T08:00:00Z").message(smart))
        assertEquals(TimestampLabel.Text("WEEKDAY_TIME"), Instant.parse("2026-07-13T08:00:00Z").message(smart))
        // Seven days back is the same weekday as today — ambiguous, so the full date.
        assertEquals(TimestampLabel.Text("DATE_TIME"), Instant.parse("2026-07-12T23:00:00Z").message(smart))
    }

    @Test
    fun smartFutureDayFallsBackToFullDate() {
        assertEquals(TimestampLabel.Text("DATE_TIME"), Instant.parse("2026-07-20T01:00:00Z").message(smart))
        // A few seconds of clock skew stays "today".
        assertEquals(TimestampLabel.Text("TIME"), (now + 5.seconds).message(smart))
    }

    @Test
    fun smartAcrossDstUsesLocalCalendar() {
        // Europe/Warsaw springs forward on 2026-03-29: that day is 23h long. 00:30 on the 29th is
        // still "yesterday" at 00:10 on the 30th, though under 24h elapsed.
        val zone = TimeZone.of("Europe/Warsaw")
        val dstReference = RelativeTimeReference(Instant.parse("2026-03-29T22:10:00Z"), zone)
        assertEquals(
            TimestampLabel.Yesterday("TIME"),
            Instant.parse("2026-03-28T23:30:00Z").messageLabel(smart, dstReference),
        )
    }

    @Test
    fun listLabelDropsCurrentYearOnly() {
        assertEquals(TimestampLabel.DaysAgo(6), (now - 6.days).listLabel(relative, reference))
        assertEquals(TimestampLabel.Text("DATE"), (now - 7.days).listLabel(relative, reference))
        assertEquals(TimestampLabel.Text("DATE_Y"), Instant.parse("2025-12-31T12:00:00Z").listLabel(relative, reference))
    }

    @Test
    fun listLabelIgnoresMessageStyle() {
        assertEquals(TimestampLabel.HoursAgo(3), (now - 3.hours).listLabel(absolute, reference))
    }

    @Test
    fun dateGroups() {
        assertEquals(DateGroup.Today, now.toDateGroup(reference))
        assertEquals(DateGroup.Yesterday, (now - 1.days).toDateGroup(reference))
        // Inclusive at 7 and 30 days.
        assertEquals(DateGroup.Previous7Days, (now - 2.days).toDateGroup(reference))
        assertEquals(DateGroup.Previous7Days, (now - 7.days).toDateGroup(reference))
        assertEquals(DateGroup.Previous30Days, (now - 8.days).toDateGroup(reference))
        assertEquals(DateGroup.Previous30Days, (now - 30.days).toDateGroup(reference))
        assertEquals(DateGroup.Month(2026, 5), Instant.parse("2026-05-01T00:00:00Z").toDateGroup(reference))
    }

    @Test
    fun dateGroupKeysAreDistinct() {
        val keys = listOf(
            DateGroup.Today, DateGroup.Yesterday, DateGroup.Previous7Days, DateGroup.Previous30Days,
            DateGroup.Month(2026, 5), DateGroup.Month(2025, 5), DateGroup.Unknown,
        ).map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun parsesEveryIsoShapeTheServerSends() {
        val expected = Instant.parse("2026-08-05T18:58:00Z")
        listOf(
            "2026-08-05T18:58:00.000Z",
            "2026-08-05T18:58:00Z",
            "2026-08-05T20:58:00.000+02:00",
            "2026-08-05T20:58:00+02:00",
        ).forEach { assertEquals(expected, parseIsoInstantOrNull(it), it) }
        assertEquals(null, parseIsoInstantOrNull("not a date"))
        assertEquals(null, absolute.formatAbsoluteOrNull(""))
    }
}
