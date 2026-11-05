package com.garfiec.librechat.feature.chat.util

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.StreamEvent

/**
 * A row's outcome. [INTERRUPTED] is never sent: it is what the client concludes about a call still
 * running when a stream gap swallowed its settling event (upstream `PtcTraceStatus`).
 */
enum class PtcCallStatus { RUNNING, SUCCESS, ERROR, INTERRUPTED }

/** One tool call a programmatic tool-calling program made from inside the sandbox (upstream `PtcTraceEntry`). */
@Immutable
data class PtcTraceEntry(
    val callId: String,
    val name: String,
    val status: PtcCallStatus,
    val args: String? = null,
    val error: String? = null,
    val durationMs: Long? = null,
)

/** One program's trace: the retained tail, and how many rows the cap discarded so it is never silent. */
@Immutable
data class PtcTrace(val entries: List<PtcTraceEntry> = emptyList(), val dropped: Int = 0) {
    val isRunning: Boolean get() = entries.any { it.status == PtcCallStatus.RUNNING }

    companion object {
        val EMPTY = PtcTrace()
    }
}

/**
 * Upstream `applyPtcToolCall` (`client/src/hooks/SSE/useStepHandler.ts`): `running` appends a row
 * in dispatch order, a settling event updates its row in place by `call_id` (keeping what it
 * does not restate, such as the start's args), and a settle whose row is gone (evicted by the cap,
 * or never seen across a resume gap) is dropped rather than re-appended out of order. At most
 * [PTC_TRACE_MAX_ENTRIES] rows are kept; older ones are counted in [PtcTrace.dropped].
 *
 * Returns this same instance when nothing changed, so a caller can skip the state update.
 */
fun PtcTrace.fold(event: StreamEvent.PtcToolCall): PtcTrace {
    val status = when (event.status) {
        StreamEvent.PtcToolCall.STATUS_RUNNING -> PtcCallStatus.RUNNING
        StreamEvent.PtcToolCall.STATUS_SUCCESS -> PtcCallStatus.SUCCESS
        StreamEvent.PtcToolCall.STATUS_ERROR -> PtcCallStatus.ERROR
        else -> return this
    }
    val index = entries.indexOfFirst { it.callId == event.callId }
    if (index != -1) {
        val previous = entries[index]
        val updated = previous.copy(
            name = event.name,
            status = status,
            args = event.args ?: previous.args,
            error = event.error ?: previous.error,
            durationMs = event.durationMs ?: previous.durationMs,
        )
        return copy(entries = entries.toMutableList().also { it[index] = updated })
    }
    if (status != PtcCallStatus.RUNNING) return this
    val appended = entries + PtcTraceEntry(
        callId = event.callId,
        name = event.name,
        status = status,
        args = event.args,
        error = event.error,
        durationMs = event.durationMs,
    )
    val overflow = appended.size - PTC_TRACE_MAX_ENTRIES
    return if (overflow <= 0) copy(entries = appended) else PtcTrace(appended.drop(overflow), dropped + overflow)
}

/**
 * Upstream `prunePtcTraces`, run on a resume: rows still `running` become [PtcCallStatus.INTERRUPTED]
 * rather than spin forever (the snapshot cannot rebuild them, and a settle lost in the gap is never
 * replayed). Marked, not removed, so a call still executing across the reconnect settles its own
 * row when its event arrives.
 */
fun PtcTrace.interrupted(): PtcTrace =
    if (!isRunning) {
        this
    } else {
        copy(entries = entries.map { if (it.status == PtcCallStatus.RUNNING) it.copy(status = PtcCallStatus.INTERRUPTED) else it })
    }

/** Upstream `PTC_TRACE_MAX_ENTRIES`: a program looping over a large collection can make thousands of calls. */
const val PTC_TRACE_MAX_ENTRIES = 100
