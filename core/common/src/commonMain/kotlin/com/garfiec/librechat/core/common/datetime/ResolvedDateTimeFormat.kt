package com.garfiec.librechat.core.common.datetime

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.time.Instant

/** Formats an instant against an already-resolved pattern. Production uses the platform formatter. */
fun interface InstantPatternFormatter {
    fun format(instant: Instant, pattern: String, localeTag: String, timeZone: TimeZone): String
}

private val PlatformInstantFormatter = InstantPatternFormatter(::platformFormatInstant)

/**
 * The user's date/time choices, resolved against the device clock setting and the app locale.
 *
 * Built once at the app root and handed down through `LocalDateTimeFormat`; every formatter in the
 * app takes one. Equality covers everything that changes the output ([prefs], [patterns],
 * [localeTag]) and deliberately not the [formatter] function, so two resolutions of the same inputs
 * compare equal and don't invalidate `remember` keys.
 */
class ResolvedDateTimeFormat(
    val prefs: DateTimeFormatPrefs,
    val patterns: DateTimePatterns,
    val localeTag: String,
    private val formatter: InstantPatternFormatter = PlatformInstantFormatter,
) {
    fun format(
        instant: Instant,
        pattern: String,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): String = formatter.format(instant, pattern, localeTag, timeZone)

    /** Full date and time — what every "absolute" display in the app shows. */
    fun formatAbsolute(instant: Instant, timeZone: TimeZone = TimeZone.currentSystemDefault()): String =
        format(instant, patterns.dateTime, timeZone)

    /**
     * [iso] as a full date and time, or null when it doesn't parse. Call sites fall back to the raw
     * string on null — a date we can't read is still better shown than blanked.
     */
    fun formatAbsoluteOrNull(iso: String, timeZone: TimeZone = TimeZone.currentSystemDefault()): String? =
        parseIsoInstantOrNull(iso)?.let { formatAbsolute(it, timeZone) }

    fun formatMonthYear(year: Int, month: Int, timeZone: TimeZone): String =
        format(LocalDate(year, month, 1).atStartOfDayIn(timeZone), patterns.monthYear, timeZone)

    override fun equals(other: Any?): Boolean =
        other is ResolvedDateTimeFormat &&
            prefs == other.prefs &&
            patterns == other.patterns &&
            localeTag == other.localeTag

    override fun hashCode(): Int = (prefs.hashCode() * 31 + patterns.hashCode()) * 31 + localeTag.hashCode()

    override fun toString(): String = "ResolvedDateTimeFormat($prefs, $localeTag, $patterns)"

    companion object {
        /** Resolves every pattern for [prefs] through the platform locale data. */
        fun resolve(
            prefs: DateTimeFormatPrefs,
            systemIs24Hour: Boolean,
            localeTag: String,
        ): ResolvedDateTimeFormat = ResolvedDateTimeFormat(
            prefs = prefs,
            patterns = resolveDateTimePatterns(prefs, systemIs24Hour) { platformBestPattern(it, localeTag) },
            localeTag = localeTag,
        )
    }
}

/** Parses an ISO-8601 instant ("…Z", "…+02:00", any fraction length), or null. */
fun parseIsoInstantOrNull(iso: String): Instant? = runCatching { Instant.parse(iso) }.getOrNull()
