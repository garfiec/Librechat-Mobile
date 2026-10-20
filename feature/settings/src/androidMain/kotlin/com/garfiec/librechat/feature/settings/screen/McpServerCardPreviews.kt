package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.mcp.McpDisplayStatus
import com.garfiec.librechat.core.model.mcp.McpTool
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.settings.viewmodel.McpServerAccess
import com.garfiec.librechat.feature.settings.viewmodel.McpServerRow

internal fun sampleMcpRow(
    title: String,
    status: McpDisplayStatus,
    description: String? = null,
    toolCount: Int = 0,
    error: String? = null,
    canEdit: Boolean = true,
) = McpServerRow(
    name = title.lowercase(),
    title = title,
    tileSeed = title.lowercase(),
    description = description,
    url = "https://mcp.example.com/" + title.lowercase().replace(' ', '-'),
    iconUrl = null,
    status = status,
    error = error,
    toolCount = toolCount,
    access = McpServerAccess(canEdit = canEdit, canDelete = canEdit),
)

private val everyStatus = listOf(
    sampleMcpRow("Linear", McpDisplayStatus.NEEDS_SIGN_IN, "Issues and projects for the mobile team"),
    sampleMcpRow("Home Assistant", McpDisplayStatus.FAILED, error = "connect ECONNREFUSED 10.0.0.12:8123"),
    sampleMcpRow(
        "GitHub",
        McpDisplayStatus.CONNECTED,
        "Issues, pull requests and code search across your repositories",
        toolCount = 41,
    ),
    sampleMcpRow("Company docs", McpDisplayStatus.ON_DEMAND, canEdit = false),
    sampleMcpRow("Calendar", McpDisplayStatus.CONNECTING),
    sampleMcpRow("Notion", McpDisplayStatus.NEEDS_SETUP),
    sampleMcpRow("Filesystem", McpDisplayStatus.DISCONNECTED, canEdit = false),
)

@PreviewNarrowScreens
@Composable
private fun McpCardsLightMaterialPreview() = McpCardsHost(dark = false, UiStyle.MATERIAL)

@PreviewNarrowScreens
@Composable
private fun McpCardsDarkMaterialPreview() = McpCardsHost(dark = true, UiStyle.MATERIAL)

@PreviewNarrowScreens
@Composable
private fun McpCardsLightGlassPreview() = McpCardsHost(dark = false, UiStyle.LIQUID_GLASS)

@PreviewNarrowScreens
@Composable
private fun McpCardsDarkGlassPreview() = McpCardsHost(dark = true, UiStyle.LIQUID_GLASS)

@PreviewNarrowScreens
@Composable
private fun McpSheetHeaderMaterialPreview() = McpSheetHeaderHost(dark = false, UiStyle.MATERIAL)

@PreviewNarrowScreens
@Composable
private fun McpSheetHeaderGlassPreview() = McpSheetHeaderHost(dark = true, UiStyle.LIQUID_GLASS)

/** The sheet's header for a failing server (full error, Retry) over a connected one's tools. */
@Composable
private fun McpSheetHeaderHost(dark: Boolean, style: UiStyle) {
    LibreChatTheme(darkTheme = dark, uiStyle = style) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 16.dp)) {
                McpServerSheetHeader(row = everyStatus[1], onReconnect = {}, onEdit = {}, onDelete = {})
                McpServerSheetHeader(row = everyStatus[2], onReconnect = {}, onEdit = {}, onDelete = {})
                listOf(
                    McpTool(name = "create_issue", description = "Create a new issue in a GitHub repository"),
                    McpTool(name = "get_file_contents", description = "Read a file or directory from a repository"),
                ).forEach { McpToolRow(it, Modifier.padding(horizontal = 20.dp)) }
            }
        }
    }
}

@Composable
private fun McpCardsHost(dark: Boolean, style: UiStyle) {
    LibreChatTheme(darkTheme = dark, uiStyle = style) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp)) {
                everyStatus.forEach { row ->
                    McpServerCard(row = row, onOpen = {}, onReconnect = {}, onEdit = {}, onDelete = {})
                }
            }
        }
    }
}
