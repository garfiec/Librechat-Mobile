package com.garfiec.librechat.core.common.datetime

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

// The browser build formats with US-English conventions: the skeletons the app asks for map to
// fixed patterns, and month and weekday names are English.

private val skeletonPatterns = mapOf(
    "hm" to "h:mm a",
    "Hm" to "HH:mm",
    "MMMd" to "MMM d",
    "yMMMd" to "MMM d, y",
    "yMMMdhm" to "MMM d, y h:mm a",
    "yMMMdHm" to "MMM d, y HH:mm",
    "Md" to "M/d",
    "yMd" to "M/d/y",
    "yMdhm" to "M/d/y h:mm a",
    "yMdHm" to "M/d/y HH:mm",
    "MMMMd" to "MMMM d",
    "yMMMMd" to "MMMM d, y",
    "yMMMMdhm" to "MMMM d, y h:mm a",
    "yMMMMdHm" to "MMMM d, y HH:mm",
    "EEEhm" to "EEE h:mm a",
    "EEEHm" to "EEE HH:mm",
    "yMMMM" to "MMMM y",
)

actual fun platformBestPattern(skeleton: String, localeTag: String): String =
    skeletonPatterns[skeleton] ?: skeleton

actual fun platformFormatInstant(
    instant: Instant,
    pattern: String,
    localeTag: String,
    timeZone: TimeZone,
): String {
    val t = instant.toLocalDateTime(timeZone)
    val out = StringBuilder()
    var i = 0
    while (i < pattern.length) {
        val c = pattern[i]
        if (c == '\'') {
            val end = pattern.indexOf('\'', i + 1).let { if (it < 0) pattern.length else it }
            out.append(if (end == i + 1) "'" else pattern.substring(i + 1, end))
            i = end + 1
            continue
        }
        var run = 1
        while (i + run < pattern.length && pattern[i + run] == c) run++
        out.append(t.field(c, run))
        i += run
    }
    return out.toString()
}

private fun LocalDateTime.field(c: Char, run: Int): String = when (c) {
    'y' -> if (run == 2) (year % 100).pad(2) else year.toString()
    'M', 'L' -> when {
        run >= 4 -> MONTHS[month.ordinal]
        run == 3 -> MONTHS[month.ordinal].take(3)
        else -> (month.ordinal + 1).pad(run)
    }
    'd' -> day.pad(run)
    'E', 'c' -> WEEKDAYS[dayOfWeek.ordinal].let { if (run >= 4) it else it.take(3) }
    'h' -> (hour % 12).let { if (it == 0) 12 else it }.pad(run)
    'H' -> hour.pad(run)
    'm' -> minute.pad(run)
    's' -> second.pad(run)
    'a' -> if (hour < 12) "AM" else "PM"
    else -> c.toString().repeat(run)
}

private fun Int.pad(width: Int): String = toString().padStart(width, '0')

private val MONTHS = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)
private val WEEKDAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
