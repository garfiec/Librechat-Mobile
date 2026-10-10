package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.Composable
import com.garfiec.librechat.core.model.trace.TraceRecordKind
import com.garfiec.librechat.core.model.trace.TraceStatus
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.trace_kind_agent
import com.garfiec.librechat.feature.chat.resources.trace_kind_event
import com.garfiec.librechat.feature.chat.resources.trace_kind_generation
import com.garfiec.librechat.feature.chat.resources.trace_kind_other
import com.garfiec.librechat.feature.chat.resources.trace_kind_span
import com.garfiec.librechat.feature.chat.resources.trace_kind_tool
import com.garfiec.librechat.feature.chat.resources.trace_running
import com.garfiec.librechat.feature.chat.resources.trace_status_error
import com.garfiec.librechat.feature.chat.resources.trace_status_ok
import com.garfiec.librechat.feature.chat.resources.trace_status_warning
import org.jetbrains.compose.resources.stringResource
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

/**
 * A one-word label for a record's kind. An unrecognized kind shows its raw wire value rather than
 * nothing — the whole point of holding `kind` as a String is that a value this build has never seen
 * degrades one row instead of failing the page, and hiding it would undo that.
 */
@Composable
internal fun traceKindLabel(kind: String): String = when (kind) {
    TraceRecordKind.AGENT -> stringResource(Res.string.trace_kind_agent)
    TraceRecordKind.GENERATION -> stringResource(Res.string.trace_kind_generation)
    TraceRecordKind.TOOL -> stringResource(Res.string.trace_kind_tool)
    TraceRecordKind.SPAN -> stringResource(Res.string.trace_kind_span)
    TraceRecordKind.EVENT -> stringResource(Res.string.trace_kind_event)
    else -> kind.ifEmpty { stringResource(Res.string.trace_kind_other) }
}

@Composable
internal fun traceStatusLabel(status: String): String = when (status) {
    TraceStatus.OK -> stringResource(Res.string.trace_status_ok)
    TraceStatus.WARNING -> stringResource(Res.string.trace_status_warning)
    TraceStatus.ERROR -> stringResource(Res.string.trace_status_error)
    TraceStatus.RUNNING -> stringResource(Res.string.trace_running)
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
