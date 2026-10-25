package com.garfiec.librechat.feature.chat.viewmodel

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.model.error.StreamErrorType
import com.garfiec.librechat.core.network.api.chatRefusalText

/**
 * App-authored messages for the shared error channel (`ChatUiState.error`), carried as a marker and
 * localized where the channel is rendered (`localizedStreamError`), like
 * [com.garfiec.librechat.core.model.error.StreamErrorType]. Delegates cannot resolve compose
 * resources themselves: `getString` reads `Resources.getSystem()`, which is null under this
 * module's plain-JVM unit tests.
 *
 * Unlike a stream error a notice can carry arguments (a filename, a size), appended to the marker
 * after [ARG_SEPARATOR]. A marker that fails to parse renders as its raw text rather than nothing.
 */
enum class ChatNotice(val wire: String) {
    RENAME_FAILED("rename_failed"),
    DELETE_FAILED("delete_failed"),
    ARCHIVE_FAILED("archive_failed"),
    DUPLICATE_FAILED("duplicate_failed"),
    PRESET_SAVE_FAILED("preset_save_failed"),
    PRESET_DELETE_FAILED("preset_delete_failed"),
    PRESET_UPDATE_FAILED("preset_update_failed"),
    CONTINUE_RESPONSE_FAILED("continue_response_failed"),

    /** A message edit the server refused, when it gave no reason of its own. */
    MESSAGE_SAVE_FAILED("message_save_failed"),

    /** Arg: the favorites limit. */
    FAVORITES_LIMIT("favorites_limit"),
    FAVORITES_UPDATE_FAILED("favorites_update_failed"),

    /** Args: the filename, then the reason (server or exception text, shown as is). */
    UPLOAD_FAILED("upload_failed"),

    /** Arg: the filename. For a failure that came with no reason. */
    UPLOAD_FAILED_UNKNOWN("upload_failed_unknown"),

    /** Arg: the filename. */
    UPLOAD_UNREADABLE("upload_unreadable"),

    /** Args: the filename, then the server's size limit, already formatted. */
    UPLOAD_TOO_LARGE("upload_too_large"),
    SPEECH_PERMISSION_DENIED("speech_permission_denied"),
    SPEECH_UNAVAILABLE("speech_unavailable"),

    /** The server's middleware refused a send with a code this build does not recognize. */
    SEND_REFUSED("send_refused"),

    /** A send or queue left skill chips this chat can't run staged in the composer. */
    SKILLS_NOT_SENT("skills_not_sent"),

    /** Saving a queued edit removed skill chips this chat can't run from the queued message. */
    SKILLS_REMOVED_FROM_QUEUED("skills_removed_from_queued"),

    /**
     * Arg: whole seconds until the server's message limit lets a send through again. Rendered as a
     * plural in the largest unit that fits (see `localizedStreamError`).
     */
    MESSAGE_LIMIT_RETRY("message_limit_retry"),
    ;

    fun marker(vararg args: Any): String =
        (listOf("$MARKER_PREFIX$wire") + args.map { it.toString() }).joinToString(ARG_SEPARATOR)

    data class Parsed(val notice: ChatNotice, val args: List<String>)

    companion object {
        const val MARKER_PREFIX = "chat_notice:"

        /** U+001F (unit separator): a control character, so not one a filename or size will carry. */
        const val ARG_SEPARATOR = "\u001F"

        /** The notice [raw] is the marker of, or null if it is not one this build knows. */
        fun parse(raw: String): Parsed? {
            if (!raw.startsWith(MARKER_PREFIX)) return null
            val parts = raw.removePrefix(MARKER_PREFIX).split(ARG_SEPARATOR)
            val notice = entries.firstOrNull { it.wire == parts.first() } ?: return null
            return Parsed(notice, parts.drop(1))
        }

        /** [UPLOAD_FAILED] with [reason], or [UPLOAD_FAILED_UNKNOWN] when there is none. */
        fun uploadFailed(filename: String, reason: String?): String =
            if (reason.isNullOrBlank()) UPLOAD_FAILED_UNKNOWN.marker(filename) else UPLOAD_FAILED.marker(filename, reason)
    }
}

/**
 * What the error channel shows for a send the server's middleware refused (`ChatRefusal`), or null
 * when [e] is not one. A message limit that says how long to wait is [ChatNotice.MESSAGE_LIMIT_RETRY];
 * any other recognized code is its stream-error marker; an unrecognized one is
 * [ChatNotice.SEND_REFUSED]. A prose refusal is also null: it is the server's own sentence, and the
 * caller's screened message already carries it.
 */
internal fun refusalErrorMarker(e: Throwable?): String? {
    val api = e as? ApiException ?: return null
    val text = api.chatRefusalText ?: return null
    StreamErrorType.messageLimitRetryAfterSeconds(text)?.let { return ChatNotice.MESSAGE_LIMIT_RETRY.marker(it) }
    return StreamErrorType.refusalMarker(text)
        ?: ChatNotice.SEND_REFUSED.marker().takeUnless { api.serverAuthored }
}
