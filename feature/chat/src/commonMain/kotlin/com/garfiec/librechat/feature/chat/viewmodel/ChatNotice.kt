package com.garfiec.librechat.feature.chat.viewmodel

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.model.error.ServerErrorCode
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

    /** 409 `conversation_title_ownership_not_ready`: the title is still being generated, or a run is going. */
    RENAME_NOT_READY("rename_not_ready"),

    /** 404 `conversation_not_found`. */
    RENAME_NOT_FOUND("rename_not_found"),
    DELETE_FAILED("delete_failed"),
    ARCHIVE_FAILED("archive_failed"),
    DUPLICATE_FAILED("duplicate_failed"),
    PRESET_SAVE_FAILED("preset_save_failed"),
    PRESET_DELETE_FAILED("preset_delete_failed"),
    PRESET_UPDATE_FAILED("preset_update_failed"),

    /** An external (Langfuse) prompt picked from `/` whose live text could not be read. */
    PROMPT_RESOLVE_FAILED("prompt_resolve_failed"),
    CONTINUE_RESPONSE_FAILED("continue_response_failed"),

    /** A message edit the server refused, when it gave no reason of its own. */
    MESSAGE_SAVE_FAILED("message_save_failed"),

    /** Arg: the favorites limit. */
    FAVORITES_LIMIT("favorites_limit"),
    FAVORITES_UPDATE_FAILED("favorites_update_failed"),

    /** The chat's bookmarks couldn't be read, so the checklist closed. */
    BOOKMARKS_LOAD_FAILED("bookmarks_load_failed"),

    /** A checklist tick didn't save and was put back. */
    BOOKMARK_UPDATE_FAILED("bookmark_update_failed"),
    BOOKMARK_CREATE_FAILED("bookmark_create_failed"),

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

    /** The server's content filter blocked a send and gave no sentence of its own to show. */
    CONTENT_BLOCKED("content_blocked"),

    /** A send or queue left skill chips this chat can't run staged in the composer. */
    SKILLS_NOT_SENT("skills_not_sent"),

    /** Saving a queued edit removed skill chips this chat can't run from the queued message. */
    SKILLS_REMOVED_FROM_QUEUED("skills_removed_from_queued"),

    /**
     * Arg: whole seconds until the server's message limit lets a send through again. Rendered as a
     * plural in the largest unit that fits (see `localizedStreamError`).
     */
    MESSAGE_LIMIT_RETRY("message_limit_retry"),

    /**
     * The resume route's 403 "Decision not permitted for one or more tools": the policy no longer
     * allows a decision the user picked. Not the fingerprint 403; another decision can succeed.
     */
    TOOL_DECISION_NOT_PERMITTED("tool_decision_not_permitted"),

    /** A fork the server refused, or one that failed in transit. */
    FORK_FAILED("fork_failed"),

    /** Run Code on a reply's fenced block failed: refused (403/404/429) or the sandbox failed. */
    RUN_CODE_FAILED("run_code_failed"),

    /** A fork the server's rate limiter refused (429). */
    FORK_RATE_LIMITED("fork_rate_limited"),

    /**
     * "Branch without files" made a copy that still has its files: the server predates
     * `excludeFiles` and ignored it. The original conversation is unchanged.
     */
    BRANCH_WITHOUT_FILES_UNSUPPORTED("branch_without_files_unsupported"),

    /**
     * A send went out as "No workspace" because its coding agent's machine had nothing to pick
     * (offline, or a worker too old for workspaces): the chat is now sealed without files or commands.
     */
    CODE_WORKSPACE_SENT_WITHOUT("code_workspace_sent_without"),

    /** A workspace move/attach/detach refused as `locked`: the chat's decision changed elsewhere. */
    CODE_WORKSPACE_MOVE_STALE("code_workspace_move_stale"),

    /** A workspace change refused while a response is still running (409 without a `code`). */
    CODE_WORKSPACE_MOVE_BUSY("code_workspace_move_busy"),

    /** A workspace change that failed for any other reason. */
    CODE_WORKSPACE_MOVE_FAILED("code_workspace_move_failed"),

    /**
     * A message copy whose tool-call previews could not be fetched in full. Nothing was copied:
     * copying the previews would hand out shortened output as if it were the whole.
     */
    COPY_TOOL_CONTENT_FAILED("copy_tool_content_failed"),

    /** The server declined to withdraw a queued message. */
    QUEUE_WITHDRAW_REFUSED("queue_withdraw_refused"),

    /** A withdrawal was asked for while the previous one is still in flight. */
    QUEUE_WITHDRAW_BUSY("queue_withdraw_busy"),

    /** A queued message was sent before its edit could be saved; the edit was dropped. */
    QUEUE_EDIT_OVERTAKEN("queue_edit_overtaken"),

    /** A queued message opened for editing while the draft's files are still being attached or uploaded. */
    QUEUE_EDIT_WAITS_FOR_UPLOADS("queue_edit_waits_for_uploads"),

    /** "Send queued" while the server still holds a queued turn of this chat. */
    QUEUE_SERVER_HOLDS("queue_server_holds"),

    /** Locally queued messages left unsent were put back in the composer. */
    QUEUE_RESTORED_TO_COMPOSER("queue_restored_to_composer"),
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

/**
 * What the error channel shows for a send the server's content filter blocked
 * (`content_filter_block`, v0.8.9-rc1), or null when [e] is not one. The filter's own sentence
 * names what to remove ("Submitted content contains a email address. Remove it and try again."), so
 * it is shown when it survived screening ([screened] is the caller's screened message). A body with
 * no `message` would otherwise surface its code: the generic extraction falls back to `error`,
 * which here holds `content_filter_block` itself.
 *
 * Nothing was saved or sent to the model, so the turn is un-sent like any other pre-`created`
 * failure and its text goes back to the composer.
 */
internal fun contentFilterNotice(e: Throwable?, screened: String?): String? {
    val api = e as? ApiException ?: return null
    if (!ServerErrorCode.isContentFilterBlock(api.body)) return null
    val serverSentence = screened?.takeIf {
        api.serverAuthored && it == api.message && it != ServerErrorCode.CONTENT_FILTER_BLOCK
    }
    return serverSentence ?: ChatNotice.CONTENT_BLOCKED.marker()
}

/** Diag tag for the error channel's notices on their way to the snackbar. */
internal const val NOTICE_TAG = "ChatNotice"

/** A notice or stream-error marker's name for a Diag line; any other text is not logged. */
internal fun noticeLabel(raw: String?): String = when {
    raw == null -> "none"
    raw.startsWith(ChatNotice.MARKER_PREFIX) ->
        raw.removePrefix(ChatNotice.MARKER_PREFIX).substringBefore(ChatNotice.ARG_SEPARATOR)
    raw.startsWith(StreamErrorType.MARKER_PREFIX) ->
        raw.removePrefix(StreamErrorType.MARKER_PREFIX).substringBefore(StreamErrorType.ARG_SEPARATOR)
    else -> "text"
}
