package com.garfiec.librechat.feature.settings.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.garfiec.librechat.feature.settings.screen.McpServerEditorScreen
import com.garfiec.librechat.feature.settings.screen.McpServersScreen
import kotlinx.serialization.Serializable

@Serializable data object McpServers : SettingsRoute

/** Add an MCP server (null) or edit the one stored under [serverName]. */
@Serializable data class McpServerEditor(val serverName: String? = null) : SettingsRoute

fun EntryProviderScope<NavKey>.mcpServersEntry(
    onNavigate: (NavKey) -> Unit,
    onBack: () -> Unit,
) {
    entry<McpServers> {
        McpServersScreen(
            onNavigateBack = onBack,
            onAddServer = { onNavigate(McpServerEditor()) },
            onEditServer = { name -> onNavigate(McpServerEditor(name)) },
        )
    }
    entry<McpServerEditor> { key ->
        McpServerEditorScreen(
            serverName = key.serverName,
            onBack = onBack,
            onSaveComplete = onBack,
        )
    }
}
