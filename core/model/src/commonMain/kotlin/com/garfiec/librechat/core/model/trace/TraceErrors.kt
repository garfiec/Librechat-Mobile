package com.garfiec.librechat.core.model.trace

import com.garfiec.librechat.core.common.result.ApiException
import kotlinx.serialization.json.Json

/**
 * The `errorCode` a failed trace read carried, or null when it was not one of the route's own
 * refusals.
 *
 * Read off the exception rather than the message: the codes are what tell apart a wait (rate
 * limited), a retry (timeout) and an operator's problem (unauthorized), and the accompanying
 * `error` sentence is English prose written server-side that nothing can branch on.
 */
fun Throwable?.traceErrorCode(): String? {
    val body = (this as? ApiException)?.body?.takeIf { it.isNotBlank() } ?: return null
    return runCatching { lenientJson.decodeFromString<TraceErrorResponse>(body) }
        .getOrNull()
        ?.errorCode
        ?.takeIf { it.isNotEmpty() }
}

/** True for a refusal that will answer the same way until the request itself changes. */
fun Throwable?.isTerminalTraceError(): Boolean =
    traceErrorCode() in TERMINAL_TRACE_ERROR_CODES

private val TERMINAL_TRACE_ERROR_CODES = setOf(
    TraceErrorCode.DISABLED,
    TraceErrorCode.NOT_FOUND,
    TraceErrorCode.INVALID_REQUEST,
    TraceErrorCode.UNSUPPORTED,
)

private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }
