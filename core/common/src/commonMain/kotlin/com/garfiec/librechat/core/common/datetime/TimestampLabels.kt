package com.garfiec.librechat.core.common.datetime

import com.garfiec.librechat.core.common.extensions.RelativeTimeReference
import kotlinx.datetime.daysUntil
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * What a timestamp says, before it becomes text.
 *
 * The relative buckets need plural string resources, which live in core/ui and only resolve in
 * composition, so the decision (here, pure and testable) is split from the wording (`resolve()` in
 * core/ui). Anything already rendered by a date pattern travels as [Text].
 */
sealed interface TimestampLabel {
    data object JustNow : TimestampLabel
    data class MinutesAgo(val count: Int) : TimestampLabel
    data class HoursAgo(val count: Int) : TimestampLabel
    data class DaysAgo(val count: Int) : TimestampLabel
    data class WeeksAgo(val count: Int) : TimestampLabel

    /** "Yesterday 20:58" — [time] is already formatted. */
    data class Yesterday(val time: String) : TimestampLabel

    data class Text(val text: String) : TimestampLabel
}

private const val MINUTES_PER_HOUR = 60
private const val HOURS_PER_DAY = 24
private const val DAYS_PER_WEEK = 7
private const val RELATIVE_CUTOFF_DAYS = 30

/**
 * A message timestamp in the user's chosen [TimestampStyle].
 *
 * RELATIVE buckets by *elapsed* time; a timestamp slightly in the future (server clock ahead of the
 * device) reads as [TimestampLabel.JustNow]. SMART buckets by *calendar day* via `daysUntil`, so DST
 * and midnight are exact; a future calendar day falls through to the full date and time.
 */
fun Instant.messageLabel(format: ResolvedDateTimeFormat, reference: RelativeTimeReference): TimestampLabel {
    val tz = reference.timeZone
    return when (format.prefs.style) {
        TimestampStyle.ABSOLUTE -> TimestampLabel.Text(format.formatAbsolute(this, tz))
        TimestampStyle.RELATIVE -> relativeBucket(reference, weeks = true)
            ?: TimestampLabel.Text(format.formatAbsolute(this, tz))
        TimestampStyle.SMART -> {
            val days = toLocalDateTime(tz).date.daysUntil(reference.today)
            when (days) {
                0 -> TimestampLabel.Text(format.format(this, format.patterns.time, tz))
                1 -> TimestampLabel.Yesterday(format.format(this, format.patterns.time, tz))
                in 2 until DAYS_PER_WEEK -> TimestampLabel.Text(format.format(this, format.patterns.weekdayTime, tz))
                else -> TimestampLabel.Text(format.formatAbsolute(this, tz))
            }
        }
    }
}

/**
 * Conversation-list / drawer row label: relative up to a week, then the date alone — without the
 * year when it is the current one.
 */
fun Instant.listLabel(format: ResolvedDateTimeFormat, reference: RelativeTimeReference): TimestampLabel =
    relativeBucket(reference, weeks = false) ?: run {
        val date = toLocalDateTime(reference.timeZone).date
        val pattern = if (date.year == reference.today.year) {
            format.patterns.dateNoYear
        } else {
            format.patterns.dateWithYear
        }
        TimestampLabel.Text(format.format(this, pattern, reference.timeZone))
    }

/** Elapsed-time bucket, or null once the timestamp is past the relative range. */
private fun Instant.relativeBucket(reference: RelativeTimeReference, weeks: Boolean): TimestampLabel? {
    val elapsed = reference.now - this
    val minutes = elapsed.inWholeMinutes
    val hours = elapsed.inWholeHours
    val days = elapsed.inWholeDays
    return when {
        minutes < 1 -> TimestampLabel.JustNow
        minutes < MINUTES_PER_HOUR -> TimestampLabel.MinutesAgo(minutes.toInt())
        hours < HOURS_PER_DAY -> TimestampLabel.HoursAgo(hours.toInt())
        days < DAYS_PER_WEEK -> TimestampLabel.DaysAgo(days.toInt())
        weeks && days < RELATIVE_CUTOFF_DAYS -> TimestampLabel.WeeksAgo((days / DAYS_PER_WEEK).toInt())
        else -> null
    }
}

/**
 * Conversation-list section, localized at render time; [key] is the stable, Bundle-safe string for
 * `LazyColumn` item keys.
 */
sealed interface DateGroup {
    val key: String

    data object Today : DateGroup {
        override val key = "today"
    }

    data object Yesterday : DateGroup {
        override val key = "yesterday"
    }

    data object Previous7Days : DateGroup {
        override val key = "prev7"
    }

    data object Previous30Days : DateGroup {
        override val key = "prev30"
    }

    data class Month(val year: Int, val month: Int) : DateGroup {
        override val key: String get() = "month_${year}_$month"
    }

    /** A conversation with no `updatedAt`. */
    data object Unknown : DateGroup {
        override val key = "unknown"
    }
}

private const val PREVIOUS_7_DAYS_MAX = 7
private const val PREVIOUS_30_DAYS_MAX = 30

fun Instant.toDateGroup(reference: RelativeTimeReference): DateGroup {
    val date = toLocalDateTime(reference.timeZone).date
    val daysBetween = date.daysUntil(reference.today)
    return when {
        daysBetween == 0 -> DateGroup.Today
        daysBetween == 1 -> DateGroup.Yesterday
        daysBetween <= PREVIOUS_7_DAYS_MAX -> DateGroup.Previous7Days
        daysBetween <= PREVIOUS_30_DAYS_MAX -> DateGroup.Previous30Days
        else -> DateGroup.Month(date.year, date.month.number)
    }
}
