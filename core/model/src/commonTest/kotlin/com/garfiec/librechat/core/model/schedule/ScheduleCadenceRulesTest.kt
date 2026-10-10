package com.garfiec.librechat.core.model.schedule

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The transcribed cadence rules. Nothing on the wire carries these, so a drift from upstream is
 * invisible at runtime — the editor keeps accepting a cadence the server has started refusing.
 * Registered in `scripts/mirrors.json`; these assertions are the other half of that guard.
 */
class ScheduleCadenceRulesTest {

    @Test
    fun structured_cadences_compile_to_the_expressions_the_engine_fires() {
        assertEquals(
            "30 * * * *",
            cadenceToCron(ScheduleCadence.structured(ScheduleFrequency.HOURLY, hour = 9, minute = 30)),
        )
        assertEquals(
            "0 9 * * *",
            cadenceToCron(ScheduleCadence.structured(ScheduleFrequency.DAILY, hour = 9, minute = 0)),
        )
        assertEquals(
            "15 7 * * 1-5",
            cadenceToCron(ScheduleCadence.structured(ScheduleFrequency.WEEKDAYS, hour = 7, minute = 15)),
        )
        assertEquals(
            "0 18 * * 1,4",
            cadenceToCron(
                ScheduleCadence.structured(
                    ScheduleFrequency.WEEKLY,
                    hour = 18,
                    minute = 0,
                    daysOfWeek = listOf(4, 1),
                ),
            ),
        )
    }

    @Test
    fun an_hourly_cadence_ignores_its_hour_but_still_carries_one() {
        // The server's structured schema requires `hour` on every arm, hourly included, so a
        // payload that omits it is a 400 even though the compiled expression never reads it.
        val cadence = ScheduleCadence.structured(ScheduleFrequency.HOURLY, hour = 9, minute = 5)

        assertEquals(9, cadence.hour)
        assertEquals("5 * * * *", cadenceToCron(cadence))
    }

    @Test
    fun a_weekly_cadence_with_no_days_falls_back_to_monday() {
        assertEquals(
            "0 8 * * 1",
            cadenceToCron(ScheduleCadence(frequency = ScheduleFrequency.WEEKLY, hour = 8, minute = 0)),
        )
    }

    @Test
    fun a_cron_cadence_is_echoed_unchanged() {
        assertEquals("0 9,17 * * 1-5", cadenceToCron(ScheduleCadence.cron(" 0 9,17 * * 1-5 ")))
    }

    @Test
    fun the_interval_floor_allows_for_a_spring_forward() {
        // A daily cadence is 1440 minutes nominally, but the wall clock compresses it by up to
        // two hours once a year, so a floor set at the nominal value would admit a schedule that
        // genuinely violates it.
        assertEquals(60, cadenceIntervalMinutesOrUnknown(daily(ScheduleFrequency.HOURLY)))
        assertEquals(1440 - 120, cadenceIntervalMinutesOrUnknown(daily(ScheduleFrequency.DAILY)))
        assertEquals(1440 - 120, cadenceIntervalMinutesOrUnknown(daily(ScheduleFrequency.WEEKDAYS)))
    }

    @Test
    fun a_weekly_cadence_is_measured_by_its_shortest_gap() {
        // Mon+Tue looks weekly and fires 24 hours apart, so it must be refused against a
        // daily-or-longer floor.
        assertEquals(
            1440 - 120,
            cadenceIntervalMinutesOrUnknown(weekly(listOf(1, 2))),
        )
        assertEquals(
            7 * 1440 - 120,
            cadenceIntervalMinutesOrUnknown(weekly(listOf(3))),
        )
    }

    @Test
    fun the_weekly_gap_wraps_around_the_end_of_the_week() {
        // Saturday and Sunday are adjacent across the boundary, not six days apart.
        assertEquals(1440 - 120, cadenceIntervalMinutesOrUnknown(weekly(listOf(0, 6))))
    }

    @Test
    fun a_duplicated_day_does_not_read_as_a_zero_gap() {
        // A payload built here is normalized, but a row written by an older client can hold
        // `[1, 1]`, which would otherwise fail every floor.
        assertEquals(7 * 1440 - 120, cadenceIntervalMinutesOrUnknown(weekly(listOf(1, 1))))
    }

    @Test
    fun a_cron_cadence_reports_unknown_rather_than_guessing() {
        // Measuring a cron expression's tightest gap means walking occurrences with a real cron
        // engine. Null is "ask the server" — the caller must submit and surface the 400, not
        // treat it as a pass or a fail.
        assertNull(cadenceIntervalMinutesOrUnknown(ScheduleCadence.cron("*/5 * * * *")))
    }

    @Test
    fun the_cron_shape_check_catches_only_what_is_worth_a_round_trip() {
        assertTrue(isPlausibleCronShape("0 9 * * 1-5"))
        assertFalse(isPlausibleCronShape(""))
        assertFalse(isPlausibleCronShape("   "))
        assertFalse(isPlausibleCronShape("every morning"))
        // The six-field form carries seconds and the server refuses it.
        assertFalse(isPlausibleCronShape("0 0 9 * * 1-5"))
        assertFalse(isPlausibleCronShape("0 9 * *"))
        assertFalse(isPlausibleCronShape("0 9 * * " + "1".repeat(SCHEDULE_CRON_MAX_LENGTH)))
    }

    @Test
    fun a_syntactically_odd_but_five_field_expression_is_left_to_the_server() {
        // croner accepts patterns a regex would refuse and refuses ones it would accept, so this
        // check deliberately waves through anything with the right shape.
        assertTrue(isPlausibleCronShape("0 0 30 2 *"))
    }

    private fun daily(frequency: String) =
        ScheduleCadence.structured(frequency, hour = 9, minute = 0)

    private fun weekly(days: List<Int>) =
        ScheduleCadence(frequency = ScheduleFrequency.WEEKLY, hour = 9, minute = 0, daysOfWeek = days)
}
