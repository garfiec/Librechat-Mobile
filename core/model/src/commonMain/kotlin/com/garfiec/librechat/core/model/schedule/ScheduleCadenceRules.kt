package com.garfiec.librechat.core.model.schedule

/**
 * The cadence rules the schedule engine fires from, transcribed from
 * `packages/data-provider/src/cadence.ts`.
 *
 * **Registered in `scripts/mirrors.json`.** Nothing on the wire carries these, so drift is
 * silent: the editor would keep accepting a cadence the server has started refusing, or show a
 * cron line that no longer describes what runs.
 *
 * Only the STRUCTURED arms are mirrored. Upstream measures a raw cron expression's tightest gap
 * by walking occurrences with croner, which is a real cron engine and not something to
 * reimplement from memory — [cronIntervalMinutesOrUnknown] returns null for those and the caller
 * lets the server answer instead.
 */

/** Mirrors the server default when a weekly cadence omits `daysOfWeek`. */
private const val WEEKLY_DEFAULT_DAY = 1

private const val MINUTES_PER_HOUR = 60
private const val MINUTES_PER_DAY = 24 * 60
private const val DAYS_PER_WEEK = 7

/**
 * Spring-forward compresses consecutive wall-clock occurrences, so the enforceable minimum for
 * day-and-longer gaps is the nominal gap minus the largest real-world transition: two hours
 * (Antarctica/Troll; every other zone shifts at most one). A floor set exactly at the nominal
 * value would admit a schedule that genuinely violates it once a year. Hourly gaps are
 * unaffected — skipped hours lengthen the gap, never shorten it.
 */
private const val DST_COMPRESSION_MINUTES = 120

/** Minute, hour, day of month, month, day of week. Six- and seven-field forms are refused. */
const val SCHEDULE_CRON_FIELD_COUNT = 5

/**
 * The cron expression this cadence compiles to. Used to SHOW what a structured choice means and
 * to echo a raw expression back unchanged — the client never fires anything itself.
 */
fun cadenceToCron(cadence: ScheduleCadence): String {
    if (cadence.isCron) return cadence.expression.orEmpty()
    val minute = cadence.minute ?: 0
    val hour = cadence.hour ?: 0
    return when (cadence.frequency) {
        ScheduleFrequency.HOURLY -> "$minute * * * *"
        ScheduleFrequency.DAILY -> "$minute $hour * * *"
        ScheduleFrequency.WEEKDAYS -> "$minute $hour * * 1-5"
        else -> {
            val days = cadence.daysOfWeek?.takeIf { it.isNotEmpty() } ?: listOf(WEEKLY_DEFAULT_DAY)
            "$minute $hour * * ${days.distinct().sorted().joinToString(",")}"
        }
    }
}

/**
 * Minutes between the closest two occurrences, for the deployment's interval floor — or null
 * when this client cannot say.
 *
 * Null is returned for every cron cadence, and it means "ask the server", not "allowed": the
 * caller must let the submit through and surface the 400 rather than treating an unknown as a
 * pass or a fail.
 */
fun cadenceIntervalMinutesOrUnknown(cadence: ScheduleCadence): Int? = when {
    cadence.isCron -> null
    cadence.frequency == ScheduleFrequency.HOURLY -> MINUTES_PER_HOUR
    cadence.frequency == ScheduleFrequency.DAILY ||
        cadence.frequency == ScheduleFrequency.WEEKDAYS -> MINUTES_PER_DAY - DST_COMPRESSION_MINUTES
    else -> weeklyIntervalMinutes(cadence.daysOfWeek)
}

/**
 * The floor must reflect the SHORTEST gap between the selected days, including the wrap around
 * the end of the week — `[Mon, Tue]` fires 24 hours apart and has to be refused against a
 * daily-or-longer floor, however weekly it looks.
 */
private fun weeklyIntervalMinutes(daysOfWeek: List<Int>?): Int {
    // Deduped defensively: a payload built here is normalized, but a row stored by an older
    // client can hold `[1, 1]`, which would otherwise read as a zero-day gap and fail every floor.
    val days = daysOfWeek?.takeIf { it.isNotEmpty() }?.distinct()?.sorted()
        ?: listOf(WEEKLY_DEFAULT_DAY)
    if (days.size <= 1) return DAYS_PER_WEEK * MINUTES_PER_DAY - DST_COMPRESSION_MINUTES
    val minGapDays = days.indices.minOf { i ->
        if (i + 1 < days.size) days[i + 1] - days[i] else DAYS_PER_WEEK - days[i] + days[0]
    }
    return minGapDays * MINUTES_PER_DAY - DST_COMPRESSION_MINUTES
}

/**
 * Whether [expression] is the SHAPE the server stores — five whitespace-separated fields within
 * the length bound.
 *
 * Deliberately not a syntax check. Upstream validates with croner, the same parser the engine
 * fires from, precisely because a regex accepts patterns croner then rejects; reimplementing one
 * here would refuse expressions that work and accept ones that do not. This only catches the
 * mistakes worth catching before a round trip — an empty box, prose, the six-field form — and
 * everything else is the server's `400 Invalid cron expression` to answer.
 */
fun isPlausibleCronShape(expression: String): Boolean {
    val trimmed = expression.trim()
    if (trimmed.isEmpty() || trimmed.length > SCHEDULE_CRON_MAX_LENGTH) return false
    return trimmed.split(' ', '\t', '\n').count { it.isNotEmpty() } == SCHEDULE_CRON_FIELD_COUNT
}
