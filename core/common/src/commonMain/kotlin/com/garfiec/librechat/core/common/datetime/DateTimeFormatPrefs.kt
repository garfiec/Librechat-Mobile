package com.garfiec.librechat.core.common.datetime

/**
 * How a message timestamp reads. Only message timestamps follow this; every other
 * date in the app is either always absolute (file details, schedules) or always relative (list rows).
 *
 * - [RELATIVE] — "5m ago" … "2w ago", then the full date and time from 30 days on. The default.
 * - [SMART] — messaging-app style: the time today, "Yesterday 20:58", the weekday for the rest of
 *   the week, then the full date and time.
 * - [ABSOLUTE] — always the full date and time.
 */
enum class TimestampStyle {
    RELATIVE, SMART, ABSOLUTE;

    fun toStorageString(): String = when (this) {
        RELATIVE -> "relative"
        SMART -> "smart"
        ABSOLUTE -> "absolute"
    }

    companion object {
        fun fromString(value: String?): TimestampStyle = when (value) {
            "smart" -> SMART
            "absolute" -> ABSOLUTE
            else -> RELATIVE
        }
    }
}

/** 12/24-hour clock. [SYSTEM] follows the device's clock setting, not the locale's convention. */
enum class ClockFormat {
    SYSTEM, H12, H24;

    fun toStorageString(): String = when (this) {
        SYSTEM -> "system"
        H12 -> "h12"
        H24 -> "h24"
    }

    companion object {
        fun fromString(value: String?): ClockFormat = when (value) {
            "h12" -> H12
            "h24" -> H24
            else -> SYSTEM
        }
    }
}

/**
 * Date shape. The SYSTEM-derived styles ([SYSTEM], [SYSTEM_NUMERIC], [LONG]) resolve through the
 * platform's locale data, so field order, separators and month names follow the app language. The
 * fixed styles are spelled out verbatim for users whose locale default isn't what they want.
 */
enum class DateFormatStyle {
    SYSTEM, SYSTEM_NUMERIC, DMY_DOT, DMY_SLASH, DMY_DASH, MDY_SLASH, YMD_DASH, YMD_SLASH, LONG;

    fun toStorageString(): String = when (this) {
        SYSTEM -> "system"
        SYSTEM_NUMERIC -> "system_numeric"
        DMY_DOT -> "dmy_dot"
        DMY_SLASH -> "dmy_slash"
        DMY_DASH -> "dmy_dash"
        MDY_SLASH -> "mdy_slash"
        YMD_DASH -> "ymd_dash"
        YMD_SLASH -> "ymd_slash"
        LONG -> "long"
    }

    companion object {
        fun fromString(value: String?): DateFormatStyle =
            entries.firstOrNull { it.toStorageString() == value } ?: SYSTEM
    }
}

/** The three stored date/time choices. */
data class DateTimeFormatPrefs(
    val style: TimestampStyle = TimestampStyle.RELATIVE,
    val clock: ClockFormat = ClockFormat.SYSTEM,
    val date: DateFormatStyle = DateFormatStyle.SYSTEM,
)
