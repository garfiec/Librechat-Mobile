package com.garfiec.librechat.core.data.share

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.FailureKind
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.response.ShareErrorBody
import com.garfiec.librechat.core.model.share.ShareSheetError
import kotlinx.serialization.json.Json

/**
 * The failure ids the `/api/share` routes send: the share service's `code`s (v0.8.8
 * `SHARE_SERVICE_ERROR_STATUS`) and the content filter's `error`s. Mirrored by hand; see
 * `scripts/mirrors.json` `share-service-error-codes` and `share-content-filter-errors`.
 */
enum class ShareErrorCode(val wire: String) {
    INVALID_PARAMS("INVALID_PARAMS"),
    TARGET_MESSAGE_NOT_FOUND("TARGET_MESSAGE_NOT_FOUND"),
    NO_MESSAGES("NO_MESSAGES"),
    CONVERSATION_NOT_FOUND("CONVERSATION_NOT_FOUND"),
    SHARE_NOT_FOUND("SHARE_NOT_FOUND"),
    SHARE_EXISTS("SHARE_EXISTS"),
    SHARE_REVISION_MISMATCH("SHARE_REVISION_MISMATCH"),
    CONTENT_FILTER_BLOCK("content_filter_block"),
    CONTENT_FILTER_UNINSPECTABLE("content_filter_uninspectable"),
    ;

    companion object {
        fun fromWire(value: String?): ShareErrorCode? = value?.let { v -> entries.firstOrNull { it.wire == v } }
    }
}

/**
 * [status] is null when nothing came back. [serverMessage] is set only when the server's wording
 * passed the display screen (see [com.garfiec.librechat.core.common.result.toSafeError]).
 */
data class ShareFailure(
    val status: Int?,
    val code: ShareErrorCode?,
    val serverMessage: String?,
    val kind: FailureKind,
) {
    val isContentFilter: Boolean
        get() = code == ShareErrorCode.CONTENT_FILTER_BLOCK || code == ShareErrorCode.CONTENT_FILTER_UNINSPECTABLE

    /** The branch tail wasn't saved yet, or there was nothing to share: worth one re-resolved retry. */
    val isTargetRejection: Boolean
        get() = code == ShareErrorCode.TARGET_MESSAGE_NOT_FOUND || code == ShareErrorCode.NO_MESSAGES
}

private val errorJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

fun Result.Error.toShareFailure(): ShareFailure {
    val api = exception as? ApiException
    val body = api?.body?.let { text ->
        runCatching { errorJson.decodeFromString(ShareErrorBody.serializer(), text) }.getOrNull()
    }
    // A body's `code` (share service) wins over its `error` (content filter); they never co-occur.
    val code = ShareErrorCode.fromWire(body?.code) ?: ShareErrorCode.fromWire(body?.error)
    // [message] is the screened text: server wording survives it only unchanged, so equality with
    // the raw message is what says the server's sentence is fit to show.
    val serverMessage = message?.takeIf { api != null && api.serverAuthored && it == api.message }
    return ShareFailure(
        status = api?.statusCode,
        code = code,
        serverMessage = serverMessage,
        kind = kind,
    )
}

fun ShareFailure.toSheetError(): ShareSheetError = when {
    code == ShareErrorCode.TARGET_MESSAGE_NOT_FOUND -> ShareSheetError.TargetNotSaved
    code == ShareErrorCode.NO_MESSAGES -> ShareSheetError.NoMessages
    isContentFilter -> ShareSheetError.ContentBlocked(serverMessage)
    code == ShareErrorCode.CONVERSATION_NOT_FOUND || code == ShareErrorCode.SHARE_NOT_FOUND ->
        ShareSheetError.NotFound
    status == HTTP_NOT_FOUND -> ShareSheetError.NotFound
    status == HTTP_FORBIDDEN -> ShareSheetError.Forbidden
    status == HTTP_TOO_MANY_REQUESTS -> ShareSheetError.RateLimited
    kind == FailureKind.Network || kind == FailureKind.Timeout -> ShareSheetError.Network
    else -> ShareSheetError.Generic
}

private const val HTTP_FORBIDDEN = 403
private const val HTTP_NOT_FOUND = 404
private const val HTTP_TOO_MANY_REQUESTS = 429
