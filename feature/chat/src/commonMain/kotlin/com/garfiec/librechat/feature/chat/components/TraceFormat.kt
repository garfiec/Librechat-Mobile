package com.garfiec.librechat.feature.chat.components

import com.garfiec.librechat.core.model.trace.TraceRecordKind
import com.garfiec.librechat.core.model.trace.TraceStatus
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.time.Instant

/** ISO-8601 to epoch millis, or null for a stamp that cannot be read. */
internal fun parseTraceInstant(value: String): Long? =
    runCatching { Instant.parse(value).toEpochMilliseconds() }.getOrNull()

internal fun formatTraceDuration(millis: Long): String = when {
    millis < MILLIS_PER_SECOND -> "$millis ms"
    millis < MILLIS_PER_MINUTE -> "${oneDecimal(millis / MILLIS_PER_SECOND.toDouble())} s"
    else -> {
        val totalSeconds = millis / MILLIS_PER_SECOND
        "${totalSeconds / SECONDS_PER_MINUTE}m ${totalSeconds % SECONDS_PER_MINUTE}s"
    }
}

/**
 * USD, which is what the wire contract says a priced record carries — the server sends a bare
 * number and there is no currency field on `/api/config` to read instead.
 */
internal fun formatTraceCost(value: Double): String {
    val units = (abs(value) * COST_SCALE).roundToLong()
    val whole = units / COST_SCALE
    val fraction = (units % COST_SCALE).toString().padStart(COST_DECIMALS, '0')
    val sign = if (value < 0) "-" else ""
    return "$sign$$whole.$fraction"
}

internal fun formatTraceCount(value: Long): String {
    val digits = value.toString()
    if (digits.length <= GROUP_SIZE) return digits
    return digits.reversed().chunked(GROUP_SIZE).joinToString(",").reversed()
}

/** A one-word label for a record's kind; an unrecognized kind shows itself rather than nothing. */
internal fun traceKindLabel(kind: String): String = when (kind) {
    TraceRecordKind.AGENT -> "Agent"
    TraceRecordKind.GENERATION -> "Generation"
    TraceRecordKind.TOOL -> "Tool"
    TraceRecordKind.SPAN -> "Span"
    TraceRecordKind.EVENT -> "Event"
    else -> kind.ifEmpty { "Record" }
}

internal fun traceStatusLabel(status: String): String = when (status) {
    TraceStatus.OK -> "OK"
    TraceStatus.WARNING -> "Warning"
    TraceStatus.ERROR -> "Error"
    TraceStatus.RUNNING -> "Running"
    else -> status
}

private fun oneDecimal(value: Double): String {
    val tenths = (value * TENTHS).roundToLong()
    return "${tenths / TENTHS}.${tenths % TENTHS}"
}

private const val MILLIS_PER_SECOND = 1_000L
private const val MILLIS_PER_MINUTE = 60_000L
private const val SECONDS_PER_MINUTE = 60L
private const val COST_SCALE = 10_000L
private const val COST_DECIMALS = 4
private const val GROUP_SIZE = 3
private const val TENTHS = 10L
