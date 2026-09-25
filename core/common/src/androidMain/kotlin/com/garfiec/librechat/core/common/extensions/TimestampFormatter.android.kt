package com.garfiec.librechat.core.common.extensions

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

actual fun formatRelativeTimestamp(isoTimestamp: String): String {
    val date = parseTimestamp(isoTimestamp) ?: return ""
    val now = System.currentTimeMillis()
    val diff = now - date.time
    if (diff < 0) return "now"

    val seconds = TimeUnit.MILLISECONDS.toSeconds(diff)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val days = TimeUnit.MILLISECONDS.toDays(diff)

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
    val format = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault())
    return format.format(date)
}

private fun parseTimestamp(timestamp: String): Date? {
    val formats = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
    )
    for (pattern in formats) {
        try {
            val sdf = SimpleDateFormat(pattern, Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            return sdf.parse(timestamp)
        } catch (_: Exception) {
            continue
        }
    }
    return null
}
