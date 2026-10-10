package com.garfiec.librechat.core.common.datetime

import kotlinx.datetime.TimeZone
import platform.Foundation.NSCache
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeZoneForSecondsFromGMT
import platform.Foundation.timeZoneWithName
import kotlin.time.Instant

// The locale is always built from an identifier, never NSLocale.currentLocale: currentLocale ignores
// the in-app language override until relaunch, and it carries the user's iOS 12/24-hour toggle,
// which would override an explicit "HH" and defeat the in-app clock setting.

actual fun platformBestPattern(skeleton: String, localeTag: String): String =
    NSDateFormatter.dateFormatFromTemplate(skeleton, 0u, NSLocale(localeIdentifier = localeTag))
        ?: skeleton

// NSCache is thread-safe and NSDateFormatter is safe to use concurrently (iOS 7+).
private val formatterCache = NSCache()

actual fun platformFormatInstant(
    instant: Instant,
    pattern: String,
    localeTag: String,
    timeZone: TimeZone,
): String {
    val key = "$pattern\u0000$localeTag\u0000${timeZone.id}"
    val formatter = formatterCache.objectForKey(key) as? NSDateFormatter
        ?: NSDateFormatter().apply {
            locale = NSLocale(localeIdentifier = localeTag)
            dateFormat = pattern
            this.timeZone = timeZone.toNsTimeZone()
        }.also { formatterCache.setObject(it, forKey = key) }
    val seconds = instant.epochSeconds + instant.nanosecondsOfSecond / NANOS_PER_SECOND
    return formatter.stringFromDate(NSDate.dateWithTimeIntervalSince1970(seconds))
}

private const val NANOS_PER_SECOND = 1_000_000_000.0

private fun TimeZone.toNsTimeZone(): NSTimeZone =
    NSTimeZone.timeZoneWithName(id) ?: NSTimeZone.timeZoneForSecondsFromGMT(0)
