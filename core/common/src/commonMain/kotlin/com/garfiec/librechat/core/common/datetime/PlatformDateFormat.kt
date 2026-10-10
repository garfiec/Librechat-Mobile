package com.garfiec.librechat.core.common.datetime

import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/**
 * The locale's best LDML pattern for [skeleton] ("yMMMd" → "d MMM y" in pl).
 *
 * [localeTag] is a platform locale identifier produced by `rememberDateLocaleTag()` in core/ui;
 * treat it as opaque. Returns [skeleton] itself if the platform has no answer.
 */
expect fun platformBestPattern(skeleton: String, localeTag: String): String

/**
 * Formats [instant] with an LDML [pattern] in [localeTag] and [timeZone].
 *
 * Formatter instances are cached per (pattern, locale, zone), so this is cheap enough to call per
 * row while scrolling. The zone is part of the key so a device that changes time zone gets a fresh
 * formatter rather than one still pinned to the old zone.
 */
expect fun platformFormatInstant(
    instant: Instant,
    pattern: String,
    localeTag: String,
    timeZone: TimeZone,
): String
