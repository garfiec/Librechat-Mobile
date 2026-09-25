package com.garfiec.librechat.core.common.extensions

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.localeWithLocaleIdentifier
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.timeZoneWithName

actual fun formatRelativeTimestamp(isoTimestamp: String): String {
    val date = parseTimestamp(isoTimestamp) ?: return ""
    val nowSeconds = NSDate().timeIntervalSince1970
    val diff = (nowSeconds - date.timeIntervalSince1970).toLong()
    if (diff < 0) return "now"

    val seconds = diff
    val minutes = diff / 60
    val hours = diff / 3600
    val days = diff / 86400

    return when {
        seconds < 60 -> "now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        days < 30 -> "${days / 7}w ago"
        else -> formatAbsoluteTimestamp(isoTimestamp)
    }
}

actual fun formatAbsoluteTimestamp(isoTimestamp: String): String {
    val date = parseTimestamp(isoTimestamp) ?: return ""
    val formatter = NSDateFormatter().apply {
        dateFormat = "MMM d, yyyy h:mm a"
        locale = NSLocale.localeWithLocaleIdentifier("en_US")
    }
    return formatter.stringFromDate(date)
}

private fun parseTimestamp(timestamp: String): NSDate? {
    val formats = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
    )
    for (pattern in formats) {
        val formatter = NSDateFormatter().apply {
            dateFormat = pattern
            locale = NSLocale.localeWithLocaleIdentifier("en_US_POSIX")
            timeZone = NSTimeZone.timeZoneWithName("UTC")!!
        }
        val date = formatter.dateFromString(timestamp)
        if (date != null) return date
    }
    return null
}
