package com.garfiec.librechat.feature.settings.viewmodel

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.FailureKind
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.MEMORY_AGENT_ACCESS_DENIED
import com.garfiec.librechat.core.data.repository.MEMORY_AGENT_NOT_FOUND
import com.garfiec.librechat.core.data.repository.MEMORY_NOT_FOUND
import com.garfiec.librechat.core.data.repository.MemoryContentFilteredException
import com.garfiec.librechat.core.data.repository.MemoryKeyRouteShadowedException
import com.garfiec.librechat.core.model.error.ServerErrorCode

/**
 * The `error` of a write the content filter refused (`contentFilterBlockResponse`,
 * `packages/api/src/middleware/contentFilter.ts`). The `message` beside it is English, so the page
 * shows its own string instead. Registered in `scripts/mirrors.json`.
 */
internal const val MEMORY_CONTENT_FILTER_CODE = "content_filter_block"

/**
 * Classifies a failed memories request. No text of the app's own reaches the UI: only a 400 the
 * server explained, which [Result.Error.message] already screened for display, is shown verbatim
 * (the web client does the same).
 */
internal fun Result.Error.toMemoryFailure(): MemoryFailure {
    if (exception is MemoryContentFilteredException) return MemoryFailure.ContentFiltered
    if (exception is MemoryKeyRouteShadowedException) return MemoryFailure.KeyRouteShadowed
    val api = exception as? ApiException
        ?: return if (kind == FailureKind.Network || kind == FailureKind.Timeout) {
            MemoryFailure.Network
        } else {
            MemoryFailure.Generic
        }
    val code = ServerErrorCode.from(api.body)
    return when {
        api.statusCode == HTTP_CONFLICT -> MemoryFailure.DuplicateKey
        api.statusCode == HTTP_NOT_FOUND && code == MEMORY_AGENT_NOT_FOUND -> MemoryFailure.AgentUnavailable
        api.statusCode == HTTP_FORBIDDEN && code == MEMORY_AGENT_ACCESS_DENIED -> MemoryFailure.AgentUnavailable
        api.statusCode == HTTP_FORBIDDEN -> MemoryFailure.Forbidden
        api.statusCode == HTTP_BAD_REQUEST && code == MEMORY_CONTENT_FILTER_CODE -> MemoryFailure.ContentFiltered
        api.statusCode == HTTP_BAD_REQUEST -> serverSentence(api)?.let(MemoryFailure::ServerMessage)
            ?: MemoryFailure.Generic
        else -> MemoryFailure.Generic
    }
}

/**
 * True when a memory handler said the row is gone (`Memory not found.`, on both the key and by-id
 * routes), so the shown list is stale. Any other 404 is not that: the agent-partition one means the
 * memory is still there, and a bodiless one is a route the request never reached (a blank key on a
 * server without the by-id routes), so treating it as gone would drop a row that still exists.
 */
internal fun Result.Error.isNotFound(): Boolean =
    isHttpStatus(HTTP_NOT_FOUND) &&
        ServerErrorCode.from((exception as? ApiException)?.body) == MEMORY_NOT_FOUND

/**
 * The server's own sentence, when [Result.Error.message] is it. `toSafeError` replaces text that
 * fails the display screen with an English fallback of the app's own, which must not be shown.
 */
private fun Result.Error.serverSentence(api: ApiException): String? =
    message?.takeIf { api.serverAuthored && it == api.message && it.isNotBlank() }

private const val HTTP_BAD_REQUEST = 400
private const val HTTP_FORBIDDEN = 403
private const val HTTP_NOT_FOUND = 404
private const val HTTP_CONFLICT = 409
