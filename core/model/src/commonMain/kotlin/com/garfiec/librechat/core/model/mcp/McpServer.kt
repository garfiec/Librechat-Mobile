package com.garfiec.librechat.core.model.mcp

/**
 * Represents an MCP server as shown in the UI.
 * The backend returns a map of server name -> config from GET /api/mcp/servers.
 * This model is constructed from that map in the API layer.
 */
data class McpServer(
    val name: String,
    val url: String = "",
    val type: McpServerType = McpServerType.SSE,
    val title: String? = null,
    val description: String? = null,
    val tools: List<McpTool> = emptyList(),
    val isConnected: Boolean = false,
    val error: String? = null,
    val apiKey: McpApiKeyConfig? = null,
    val oauth: McpOAuthConfig? = null,
    /**
     * Set on the web, never edited here. Kept only to be resent: the update route replaces the
     * stored config, so an edit that omits it deletes it.
     */
    val iconPath: String? = null,
    /** On-behalf-of token exchange, configured on the web; resent unchanged on an edit. */
    val obo: McpOboConfig? = null,
)
