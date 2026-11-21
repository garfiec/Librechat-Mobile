package com.garfiec.librechat.feature.settings.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.mcp.McpOAuthLaunch
import com.garfiec.librechat.core.model.mcp.McpServer
import com.garfiec.librechat.core.model.mcp.McpServerDiscovery
import com.garfiec.librechat.core.model.mcp.McpServerStatus
import com.garfiec.librechat.core.model.mcp.McpTool

@Immutable
data class McpUiState(
    val servers: List<McpServer> = emptyList(),
    val canCreateServer: Boolean = true,
    val connectionStatus: Map<String, McpServerStatus> = emptyMap(),
    /** Per-server verdicts from `GET /api/mcp/tools`, retained so either fetch can re-fold them. */
    val discovery: Map<String, McpServerDiscovery> = emptyMap(),
    val tools: List<McpTool> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val reinitializingServers: Set<String> = emptySet(),
    val error: String? = null,
    val errorRetry: McpRetry = McpRetry.Reload,
    /** The all-tools sheet, opened from the top bar. One server's tools live in its own sheet. */
    val showToolsSheet: Boolean = false,
    /** The server whose sheet is open, by stored name. */
    val selectedServerName: String? = null,
    /** Servers still owed one of their user variables (`auth-values`), which read as needing setup. */
    val missingUserVars: Set<String> = emptySet(),
    /** The server whose variables sheet is open, by stored name. */
    val variablesServerName: String? = null,
    val successMessage: String? = null,
    /**
     * An MCP server that answered a reinitialize by asking the user to authorize it
     * (v0.8.8 `oauthRequired` + `oauthUrl`).
     *
     * Held as state rather than acted on immediately because the consent has to be the user's:
     * silently launching a browser off a "reconnect" tap would hand a third-party provider the
     * session without anyone having agreed to it.
     */
    val pendingOAuth: McpOAuthPrompt? = null,
    /** The consent was given and the sign-in is being bound (`POST /oauth/bind`). */
    val isPreparingOAuth: Boolean = false,
    /**
     * A sign-in the user agreed to, ready to open: [McpOAuthLaunch.InApp] as the sign-in dialog,
     * [McpOAuthLaunch.External] in the system browser (servers before v0.8.9-rc1).
     */
    val oauthLaunch: McpOAuthLaunch? = null,
    /**
     * The user's permission bits per server `dbId`, or null while unknown (not loaded, or the
     * fetch failed). Read it through [mcpServerAccess], never directly.
     */
    val permissions: Map<String, Int>? = null,
    val searchQuery: String = "",
    val attentionOnly: Boolean = false,
    /** Every server, sorted for display; derived in [McpViewModel.uiState], never set directly. */
    val allRows: List<McpServerRow> = emptyList(),
    /** [allRows] narrowed by [searchQuery] and [attentionOnly]: what the list shows. */
    val rows: List<McpServerRow> = emptyList(),
    /** Each server's display title by stored name, for the all-tools sheet's headers. Derived. */
    val serverTitles: Map<String, String> = emptyMap(),
) {
    /** The open server's row; null once it is gone (deleted, or dropped by a reload), which closes its sheet. */
    val selectedRow: McpServerRow? get() = selectedServerName?.let { name -> allRows.firstOrNull { it.name == name } }

    /** The variables sheet's server; null once it is gone, which closes the sheet. */
    val variablesRow: McpServerRow?
        get() = variablesServerName?.let { name -> allRows.firstOrNull { it.name == name && it.userVars.isNotEmpty() } }

    val selectedTools: List<McpTool>
        get() = selectedServerName?.let { name -> tools.filter { it.serverName == name } }.orEmpty()

    val attentionCount: Int get() = allRows.count { it.status.needsAttention }
}

/** The action behind an error, so Retry repeats it rather than only reloading. */
sealed interface McpRetry {
    data object Reload : McpRetry
    data class Delete(val serverName: String) : McpRetry
    data class Reconnect(val serverName: String) : McpRetry
}

/** A server waiting on the user to authorize it, with the provider URL to send them to. */
data class McpOAuthPrompt(
    val serverName: String,
    val authorizationUrl: String,
)
