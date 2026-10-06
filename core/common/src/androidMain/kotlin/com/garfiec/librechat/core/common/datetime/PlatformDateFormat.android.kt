package com.garfiec.librechat.core.common.datetime

import android.icu.text.DateTimePatternGenerator
import android.icu.text.SimpleDateFormat
import android.icu.util.ULocale
import kotlinx.datetime.TimeZone
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Instant
import android.icu.util.TimeZone as IcuTimeZone

// ICU rather than java.time throughout: skeleton lookups can return ICU-only pattern letters
// (`B`, flexible day period, e.g. zh "Bh:mm"), which java.time's DateTimeFormatter rejects on API 26.

// Nullable reads below: under plain JVM unit tests the android.icu stubs return null rather than
// throwing, and a date must degrade to its skeleton/blank there, not crash an unrelated test.
actual fun platformBestPattern(skeleton: String, localeTag: String): String =
    DateTimePatternGenerator.getInstance(ULocale.forLanguageTag(localeTag))
        ?.getBestPattern(skeleton)
        ?: skeleton

private val formatterCache = ConcurrentHashMap<String, SimpleDateFormat>()

actual fun platformFormatInstant(
    instant: Instant,
    pattern: String,
    localeTag: String,
    timeZone: TimeZone,
): String {
    val zoneId = timeZone.icuId()
    val formatter = formatterCache.getOrPut("$pattern\u0000$localeTag\u0000$zoneId") {
        SimpleDateFormat(pattern, ULocale.forLanguageTag(localeTag)).apply {
            this.timeZone = IcuTimeZone.getTimeZone(zoneId)
        }
    }
    // SimpleDateFormat keeps mutable calendar state; one formatter may be shared across threads.
    return synchronized(formatter) { formatter.format(Date(instant.toEpochMilliseconds())) } ?: ""
}

/** kotlinx names UTC "Z", which ICU doesn't know (it would silently fall back to GMT anyway). */
private fun TimeZone.icuId(): String = if (id == "Z") "UTC" else id
