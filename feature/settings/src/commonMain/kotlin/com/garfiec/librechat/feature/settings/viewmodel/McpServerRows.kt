package com.garfiec.librechat.feature.settings.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.mcp.McpDisplayStatus
import com.garfiec.librechat.core.model.mcp.McpServer
import com.garfiec.librechat.core.model.mcp.McpServerStatus
import com.garfiec.librechat.core.model.mcp.mcpDisplayStatus
import com.garfiec.librechat.core.model.media.resolveAvatarUrl

/** One server as the list draws it. [name] addresses it; [title] is what the user reads. */
@Immutable
data class McpServerRow(
    val name: String,
    val title: String,
    val tileSeed: String,
    val description: String?,
    val url: String,
    /** Loadable as-is: a stored icon's relative `/images/…` path is joined to the server here. */
    val iconUrl: String?,
    val status: McpDisplayStatus,
    val error: String?,
    val toolCount: Int,
    val access: McpServerAccess,
)

/** The list's rows: servers that need the user first, then by title. */
fun mcpServerRows(
    servers: List<McpServer>,
    connectionStatus: Map<String, McpServerStatus>,
    toolCounts: Map<String, Int>,
    reinitializing: Set<String>,
    permissions: Map<String, Int>?,
    baseUrl: String,
): List<McpServerRow> = servers
    .map { server ->
        val status = connectionStatus[server.name]
        McpServerRow(
            name = server.name,
            title = server.title?.takeIf { it.isNotBlank() } ?: server.name,
            // The record id survives a rename of the title; YAML servers have only their name.
            tileSeed = server.dbId ?: server.name,
            description = server.description?.takeIf { it.isNotBlank() },
            url = server.url,
            iconUrl = resolveAvatarUrl(server.iconPath, baseUrl),
            status = mcpDisplayStatus(server, status, server.name in reinitializing),
            error = status?.error?.takeIf { it.isNotBlank() } ?: server.error?.takeIf { it.isNotBlank() },
            toolCount = toolCounts[server.name] ?: 0,
            access = mcpServerAccess(server, permissions),
        )
    }
    .sortedWith(compareBy<McpServerRow> { !it.status.needsAttention }.thenBy { it.title.lowercase() })

/** [rows] narrowed by the search box (title or name) and, when [attentionOnly], to those needing the user. */
fun filterMcpServerRows(rows: List<McpServerRow>, query: String, attentionOnly: Boolean): List<McpServerRow> {
    val needle = query.trim()
    return rows.filter { row ->
        (!attentionOnly || row.status.needsAttention) &&
            (needle.isEmpty() || row.title.contains(needle, ignoreCase = true) || row.name.contains(needle, ignoreCase = true))
    }
}

/** Below this many servers the search box is clutter: everything already fits on screen. */
const val MCP_SEARCH_THRESHOLD = 5
