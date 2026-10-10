package com.garfiec.librechat.core.common.datetime

/**
 * Every LDML pattern the app formats with, resolved once per (prefs, clock setting, locale).
 *
 * Resolving is the expensive half of date formatting — a skeleton lookup walks the locale's pattern
 * data — so it happens when a preference or the locale changes, never per row. Formatting an instant
 * against an already-resolved pattern is the cheap half.
 */
data class DateTimePatterns(
    val dateWithYear: String,
    /** The date without its year, for a date in the current year that is shown without a time. */
    val dateNoYear: String,
    val time: String,
    /** Abbreviated weekday plus time ("Mon 20:58"), in the locale's own order. */
    val weekdayTime: String,
    /** Full date plus time — what "absolute" means everywhere. */
    val dateTime: String,
    /** Month and year, for conversation-list section headers. */
    val monthYear: String,
)

/**
 * Builds [DateTimePatterns] for [prefs].
 *
 * Locale-derived date styles ask [bestPattern] for one *combined* skeleton ("yMMMdHm") rather than
 * gluing a date and a time together, so the locale decides word order and the joiner between them.
 * The fixed numeric styles have no locale to ask, so they join with a single space.
 *
 * @param systemIs24Hour the device clock setting, used when [ClockFormat.SYSTEM] is chosen.
 * @param bestPattern maps an LDML skeleton to the locale's best pattern for it.
 */
fun resolveDateTimePatterns(
    prefs: DateTimeFormatPrefs,
    systemIs24Hour: Boolean,
    bestPattern: (skeleton: String) -> String,
): DateTimePatterns {
    val is24Hour = when (prefs.clock) {
        ClockFormat.SYSTEM -> systemIs24Hour
        ClockFormat.H12 -> false
        ClockFormat.H24 -> true
    }
    val timeSkeleton = if (is24Hour) "Hm" else "hm"
    val time = bestPattern(timeSkeleton)

    fun localized(withYear: String, noYear: String) = Triple(
        bestPattern(withYear),
        bestPattern(noYear),
        bestPattern(withYear + timeSkeleton),
    )

    fun fixed(withYear: String, noYear: String) = Triple(withYear, noYear, "$withYear $time")

    val (dateWithYear, dateNoYear, dateTime) = when (prefs.date) {
        DateFormatStyle.SYSTEM -> localized("yMMMd", "MMMd")
        DateFormatStyle.SYSTEM_NUMERIC -> localized("yMd", "Md")
        DateFormatStyle.LONG -> localized("yMMMMd", "MMMMd")
        DateFormatStyle.DMY_DOT -> fixed("dd.MM.yyyy", "dd.MM")
        DateFormatStyle.DMY_SLASH -> fixed("dd/MM/yyyy", "dd/MM")
        DateFormatStyle.DMY_DASH -> fixed("dd-MM-yyyy", "dd-MM")
        DateFormatStyle.MDY_SLASH -> fixed("MM/dd/yyyy", "MM/dd")
        // The ISO shape: a year-less "08-05" isn't ISO any more, so the year is always kept.
        DateFormatStyle.YMD_DASH -> fixed("yyyy-MM-dd", "yyyy-MM-dd")
        DateFormatStyle.YMD_SLASH -> fixed("yyyy/MM/dd", "MM/dd")
    }

    return DateTimePatterns(
        dateWithYear = dateWithYear,
        dateNoYear = dateNoYear,
        time = time,
        weekdayTime = bestPattern("EEE$timeSkeleton"),
        dateTime = dateTime,
        monthYear = bestPattern("yMMMM"),
    )
}
