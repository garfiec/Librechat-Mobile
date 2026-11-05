package com.garfiec.librechat.feature.chat.model

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.mcp.McpDisplayStatus

@Immutable
data class McpServerDisplayData(
    val name: String,
    val title: String?,
    val description: String?,
    /** Its connection as the MCP servers page reads it ([com.garfiec.librechat.core.model.mcp.mcpDisplayStatus]). */
    val status: McpDisplayStatus,
)
