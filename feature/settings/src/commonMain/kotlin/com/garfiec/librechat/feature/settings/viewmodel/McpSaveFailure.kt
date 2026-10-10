package com.garfiec.librechat.feature.settings.viewmodel

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.error.ServerErrorCode

/** A refused MCP server save, as [McpViewModel] reports it. */
internal data class McpSaveFailure(
    /** `MCP_OAUTH_SECRET_REENTRY_REQUIRED`: the client secret must be typed again. */
    val secretReentry: Boolean,
    /** `MCP_API_KEY_REENTRY_REQUIRED` (v0.8.8-rc4): the admin API key must be typed again. */
    val keyReentry: Boolean,
    /**
     * The line the open dialog shows, or null for a re-entry refusal: the dialog words those from
     * its own string resources, so they are localized.
     */
    val dialogMessage: String?,
    /** The same outcome as a snackbar, for a save whose dialog closed while it ran. */
    val snackbarMessage: String,
)

/**
 * Classifies a refused save. A rejected credential binding is a prompt-for-input outcome, never a
 * retry: the same body can only be refused again.
 */
internal fun Result.Error.toMcpSaveFailure(fallback: String): McpSaveFailure {
    val body = (exception as? ApiException)?.body
    val badRequest = isHttpStatus(HTTP_BAD_REQUEST)
    val code = body?.takeIf { badRequest }?.let(ServerErrorCode::from)
    val secretReentry = code == ServerErrorCode.OAUTH_SECRET_REENTRY_REQUIRED
    val keyReentry = code == ServerErrorCode.API_KEY_REENTRY_REQUIRED
    val detail = serverReason(body, badRequest) ?: message ?: fallback
    return McpSaveFailure(
        secretReentry = secretReentry,
        keyReentry = keyReentry,
        dialogMessage = detail.takeUnless { secretReentry || keyReentry },
        snackbarMessage = when {
            secretReentry ->
                "This server's OAuth endpoints changed, so the saved client secret no longer applies. " +
                    "Enter the client secret again to save."
            keyReentry ->
                "This server's connection settings changed, so the saved API key no longer applies. " +
                    "Enter the API key again to save."
            else -> detail
        },
    )
}

/**
 * The server's own account of a refused write, or null to fall back to the screened message.
 *
 * These bypass the generic display screen, so only the MCP controller's own envelopes count: a
 * 400 schema refusal's `errors[]`, and a coded `{ error: "MCP_…", message }`. A proxy in front of
 * the server can answer with the same keys, which is why the status and the prefix are checked and
 * the text is bounded.
 */
private fun serverReason(body: String?, badRequest: Boolean): String? {
    val issues = if (badRequest) ServerErrorCode.validationMessages(body) else emptyList()
    if (issues.isNotEmpty()) {
        return issues.take(MAX_ISSUES).joinToString("\n") { it.bounded() }
    }
    return ServerErrorCode.codedMessage(body, codePrefix = MCP_CODE_PREFIX)?.bounded()
}

private fun String.bounded(): String =
    if (length <= MAX_REASON_CHARS) this else take(MAX_REASON_CHARS).trimEnd() + "…"

/** The MCP write routes report a rejected credential binding, and a schema refusal, with 400. */
private const val HTTP_BAD_REQUEST = 400
private const val MCP_CODE_PREFIX = "MCP_"
private const val MAX_ISSUES = 5
private const val MAX_REASON_CHARS = 300
