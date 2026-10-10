package com.garfiec.librechat.feature.settings.viewmodel

import com.garfiec.librechat.core.model.mcp.McpApiKeyConfig
import com.garfiec.librechat.core.model.mcp.McpOAuthConfig
import com.garfiec.librechat.core.model.mcp.McpOboConfig
import com.garfiec.librechat.core.model.mcp.McpServer

/** Stored fields a save resends unchanged; this app never edits them. */
internal data class McpPreservedFields(
    val iconPath: String?,
    val obo: McpOboConfig?,
)

/**
 * What an edit of [editing] must resend as stored, given the auth the dialog is saving. Both write
 * routes replace the whole config, so a field the dialog cannot show is deleted unless it is sent
 * back. MIRRORED from upstream `useMCPServerForm`, which round-trips both through its form state:
 * - `iconPath` always goes back as it was;
 * - `obo` goes back only while the save carries neither an API key nor OAuth. Upstream treats OBO
 *   as its own auth type and drops `obo` when the user picks another; this app has no OBO choice,
 *   so "no other auth" is where an OBO server stays.
 *
 * Resending `obo` exactly is also what lets a user without `CONFIGURE_OBO` edit an OBO server at
 * all: the update route refuses any change to it from such a caller with a 403, dropping it
 * included (`violatesOboLockdown`, `api/server/controllers/mcp.js`).
 */
internal fun preservedFieldsFor(
    editing: McpServer?,
    apiKey: McpApiKeyConfig?,
    oauth: McpOAuthConfig?,
): McpPreservedFields = McpPreservedFields(
    iconPath = editing?.iconPath,
    obo = editing?.obo?.takeIf { apiKey == null && oauth == null },
)
