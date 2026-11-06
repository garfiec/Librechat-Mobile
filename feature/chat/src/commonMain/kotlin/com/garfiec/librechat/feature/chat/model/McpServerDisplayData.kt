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
    /** The server signs in with OAuth, as its connection status reports it. */
    val requiresOAuth: Boolean = false,
    /**
     * A chip's sign-in reconnect or a "Cancel sign-in" is still out, this server's or another's.
     * Until it ends the chips' sign-in taps would be ignored, so they aren't offered.
     */
    val signInBusy: Boolean = false,
)
