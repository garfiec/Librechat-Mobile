package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.error.StreamErrorType
import com.garfiec.librechat.feature.chat.resources.*
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.viewmodel.ChatNotice
import org.jetbrains.compose.resources.stringResource

/**
 * Resolves the shared error channel's value for display, swapping a typed marker for the localized
 * sentence that says what the user can actually do about it.
 *
 * **Anything unrecognized passes through unchanged.** That is the contract, not a fallback: the
 * channel carries server-authored text and app-authored text as well as markers, a newer server
 * will emit codes this build has never heard of, and none of those may reach the user as a bare
 * identifier or an empty string.
 *
 * **Every surface that renders `ChatUiState.error` calls this** — the two platform snackbars and
 * both model-selector banners. The value a user sees must not depend on which one is showing it,
 * and the two are concurrent: `error` is cleared only when the Long snackbar returns, so a selector
 * opened in that window renders the same value at the same time. The model-related codes are the
 * ones that send a user to the selector, so that overlap is the expected path, not an edge case.
 */
@Composable
internal fun localizedStreamError(raw: String): String {
    ChatNotice.parse(raw)?.let { return localizedNotice(it) }
    if (!raw.startsWith(StreamErrorType.MARKER_PREFIX)) return raw
    val wire = raw.removePrefix(StreamErrorType.MARKER_PREFIX)
    // Matched on the enum rather than on the marker string so a code removed from the enum stops
    // resolving here too, instead of leaving a branch that can never be reached.
    val type = StreamErrorType.entries.firstOrNull { it.wire == wire } ?: return raw
    return when (type) {
        StreamErrorType.RESOURCE_RECOVERY_REQUIRED -> stringResource(Res.string.error_resource_recovery_required)
        StreamErrorType.MODEL_NOT_FOUND -> stringResource(Res.string.error_model_not_found)
        StreamErrorType.MODEL_RATE_LIMIT -> stringResource(Res.string.error_model_rate_limit)
        StreamErrorType.MODEL_STREAM_CLOSED -> stringResource(Res.string.error_model_stream_closed)
        StreamErrorType.MODEL_STREAM_STALLED -> stringResource(Res.string.error_model_stream_stalled)
        StreamErrorType.MISSING_MODEL -> stringResource(Res.string.error_missing_model)
        StreamErrorType.MODELS_NOT_LOADED -> stringResource(Res.string.error_models_not_loaded)
        StreamErrorType.ENDPOINT_MODELS_NOT_LOADED -> stringResource(Res.string.error_endpoint_models_not_loaded)
        StreamErrorType.INVALID_AGENT_PROVIDER -> stringResource(Res.string.error_invalid_agent_provider)
        StreamErrorType.REFUSAL -> stringResource(Res.string.error_refusal)
        StreamErrorType.INPUT_LENGTH -> stringResource(Res.string.error_input_length)
        StreamErrorType.FINAL_CONTEXT_OVERFLOW -> stringResource(Res.string.error_final_context_overflow)
        StreamErrorType.COMPACTION_SKIPPED -> stringResource(Res.string.error_compaction_skipped)
        StreamErrorType.COMPACTION_FAILED -> stringResource(Res.string.error_compaction_failed)
        StreamErrorType.MODERATION -> stringResource(Res.string.error_moderation)
        StreamErrorType.AUTH_RATE_LIMITED -> stringResource(Res.string.error_auth_rate_limited)
        StreamErrorType.AUTH_BANNED -> stringResource(Res.string.error_auth_banned)
        StreamErrorType.AUTH_CROSS_ORIGIN -> stringResource(Res.string.error_auth_cross_origin)
        StreamErrorType.SHARE_LIMIT -> stringResource(Res.string.error_share_limit)
        StreamErrorType.STREAM_EXPIRED -> stringResource(Res.string.error_stream_expired)
        StreamErrorType.UPSTREAM_MODEL_ERROR -> stringResource(Res.string.error_upstream_model_error)
        StreamErrorType.EMPTY_MESSAGES -> stringResource(Res.string.error_empty_messages)
        StreamErrorType.CODE_WORKSPACE_UNAVAILABLE ->
            stringResource(Res.string.error_code_workspace_unavailable)
        StreamErrorType.STATEFUL_CODE_ENVIRONMENT_NOT_ALLOWED ->
            stringResource(Res.string.error_stateful_code_environment_not_allowed)
        StreamErrorType.MCP_AUTHENTICATION_REJECTED -> stringResource(Res.string.error_mcp_authentication_rejected)
        StreamErrorType.MCP_AUTHENTICATION_REFRESH_FAILED ->
            stringResource(Res.string.error_mcp_authentication_refresh_failed)
    }
}

@Composable
private fun localizedNotice(parsed: ChatNotice.Parsed): String {
    val res = when (parsed.notice) {
        ChatNotice.RENAME_FAILED -> Res.string.notice_rename_failed
        ChatNotice.DELETE_FAILED -> Res.string.notice_delete_failed
        ChatNotice.ARCHIVE_FAILED -> Res.string.notice_archive_failed
        ChatNotice.DUPLICATE_FAILED -> Res.string.notice_duplicate_failed
        ChatNotice.SHARE_LINK_FAILED -> Res.string.notice_share_link_failed
        ChatNotice.PRESET_SAVE_FAILED -> Res.string.notice_preset_save_failed
        ChatNotice.PRESET_DELETE_FAILED -> Res.string.notice_preset_delete_failed
        ChatNotice.PRESET_UPDATE_FAILED -> Res.string.notice_preset_update_failed
        ChatNotice.CONTINUE_RESPONSE_FAILED -> Res.string.notice_continue_response_failed
        ChatNotice.FAVORITES_LIMIT -> Res.string.notice_favorites_limit
        ChatNotice.FAVORITES_UPDATE_FAILED -> Res.string.notice_favorites_update_failed
        ChatNotice.UPLOAD_FAILED -> Res.string.notice_upload_failed
        ChatNotice.UPLOAD_FAILED_UNKNOWN -> Res.string.notice_upload_failed_unknown
        ChatNotice.UPLOAD_UNREADABLE -> Res.string.notice_upload_unreadable
        ChatNotice.UPLOAD_TOO_LARGE -> Res.string.notice_upload_too_large
        ChatNotice.SPEECH_PERMISSION_DENIED -> Res.string.notice_speech_permission_denied
        ChatNotice.SPEECH_UNAVAILABLE -> Res.string.notice_speech_unavailable
    }
    val args = parsed.args
    // A notice with a third argument needs its own branch here; `else` would drop it.
    return when (args.size) {
        0 -> stringResource(res)
        1 -> stringResource(res, args[0])
        else -> stringResource(res, args[0], args[1])
    }
}

/**
 * The failure a turn was saved with, ready for [localizedStreamError], or null for any other
 * message.
 *
 * From v0.8.8-rc2 the server persists a failed turn as `error: true` with no content parts and
 * the failure itself as `text` — for an initialization failure, the JSON of its typed payload.
 * Rendered as ordinary text that JSON is what the thread showed, where the web shows the error.
 */
internal fun persistedTurnError(message: Message): String? =
    if (isPersistedTurnError(message)) StreamErrorType.markerOrText(message.text) else null

/**
 * Whether [message] is a persisted failed turn — the test [persistedTurnError] classifies, without
 * classifying. The thread and search only branch on it, and search runs it for every message on
 * every keystroke.
 */
internal fun isPersistedTurnError(message: Message): Boolean =
    message.error && message.content.isNullOrEmpty() && message.text.isNotBlank()

/**
 * A stream error as it sits in the thread — the in-band `error` part and a persisted failed turn
 * both render through here.
 *
 * Adds what [localizedStreamError] cannot carry through a string: for an `upstream_model_error`,
 * the provider's status in the headline and, from v0.8.8-rc4, the provider's own message under it.
 * A gateway or proxy rejection explains itself only there. The shared error channel (snackbar,
 * selector banners) keeps the headline alone; the full account is in the thread.
 */
@Composable
internal fun StreamErrorPart(raw: String, modifier: Modifier = Modifier) {
    val classified = remember(raw) { StreamErrorType.markerOrText(raw) }
    val detail = remember(raw) { StreamErrorType.upstreamModelErrorDetail(raw) }
    val status = detail?.status
    val headline = if (status != null) {
        stringResource(Res.string.error_upstream_model_error_status, status)
    } else {
        localizedStreamError(classified)
    }
    ErrorContentPart(errorText = headline, detail = detail?.message, modifier = modifier)
}

/**
 * Whether an error detail reads in place or collapses. Mirrors upstream `ErrorWithDetail`
 * (`client/src/components/Messages/Content/Error/parts.tsx`): up to 240 characters on one line is
 * a sentence; anything longer, or spanning lines, is a body to open.
 */
internal fun isInlineErrorDetail(detail: String): Boolean =
    detail.length <= INLINE_DETAIL_LENGTH && detail.none { it == '\n' || it == '\r' }

private const val INLINE_DETAIL_LENGTH = 240
