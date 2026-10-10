package com.garfiec.librechat.feature.settings.screen

import com.garfiec.librechat.core.model.mcp.McpApiKeyConfig
import com.garfiec.librechat.core.model.mcp.McpApiKeySource
import com.garfiec.librechat.core.model.mcp.McpAuthorizationType
import com.garfiec.librechat.core.model.mcp.McpServer

/**
 * Who supplies the key when the dialog opens: the stored server's own source on an edit, and
 * [McpApiKeySource.ADMIN] otherwise — a new server, or an edit that switches auth to an API key.
 * MIRRORED from upstream `useMCPServerForm` (`api_key_source: apiKeyConfig?.source || 'admin'`).
 *
 * The edit half is load-bearing, not cosmetic. The update route replaces the config, and reads never
 * return the key, so an edit that resends a different source rewrites the server: `user` over a
 * stored admin key discards that key with a 200 and prompts every user for their own (see
 * DISCOVERY.md, `PATCH /api/mcp/servers/:serverName`).
 */
internal fun initialApiKeySource(editingServer: McpServer?): McpApiKeySource =
    editingServer?.apiKey?.source ?: McpApiKeySource.ADMIN

/**
 * The `apiKey` object a save sends. MIRRORED from upstream `useMCPServerForm`'s submit:
 * - `key` only when the admin supplies it and one was typed. A blank key on an admin edit is sent
 *   as no key, which asks the server to keep the stored one — and, from v0.8.8-rc4, is what lets
 *   it refuse with `MCP_API_KEY_REENTRY_REQUIRED` when the connection changed. A per-user server
 *   never carries a key; the server would strip it anyway.
 * - `custom_header` only for the custom authorization type.
 */
internal fun apiKeyConfigFrom(
    source: McpApiKeySource,
    authorizationType: McpAuthorizationType,
    key: String,
    customHeader: String,
): McpApiKeyConfig = McpApiKeyConfig(
    source = source,
    authorizationType = authorizationType,
    key = key.trim().ifBlank { null }?.takeIf { source == McpApiKeySource.ADMIN },
    customHeader = customHeader.trim().ifBlank { null }?.takeIf { authorizationType == McpAuthorizationType.CUSTOM },
)
