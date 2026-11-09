package com.garfiec.librechat.core.model.mcp

/**
 * A value the user owes an MCP server before it connects (`customUserVars` in its config), such as a
 * personal token. Saved per user on the server, which never returns it, only whether it is set.
 */
data class McpUserVar(
    val key: String,
    val title: String,
    /** The server's text, which may hold inline HTML links (web renders it sanitized). */
    val description: String,
    /** Upstream masks the field unless the config says `sensitive: false`. */
    val sensitive: Boolean = true,
)
