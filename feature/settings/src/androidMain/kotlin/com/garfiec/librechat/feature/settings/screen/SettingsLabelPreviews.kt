package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.ArtifactShortcut
import com.garfiec.librechat.core.model.mcp.McpDisplayStatus
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme

// One server with a single tool and one with several, so both plural forms of the count show; and
// pinned artifacts, whose type line is the label this screen translates.

@PreviewNarrowScreens
@Composable
private fun SettingsLabelsMaterialPreview() = SettingsLabelsHost(UiStyle.MATERIAL)

@PreviewNarrowScreens
@Composable
private fun SettingsLabelsGlassPreview() = SettingsLabelsHost(UiStyle.LIQUID_GLASS)

@Composable
private fun SettingsLabelsHost(style: UiStyle) {
    LibreChatTheme(darkTheme = true, uiStyle = style) {
        Surface {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp)) {
                listOf(1, 3).forEach { count ->
                    McpServerCard(
                        row = sampleMcpRow(title = "GitHub", status = McpDisplayStatus.CONNECTED, toolCount = count),
                        onOpen = {},
                        onReconnect = {},
                        onEdit = {},
                        onDelete = {},
                    )
                }
                listOf("application/vnd.mermaid", "application/vnd.react").forEach { type ->
                    ArtifactShortcutRow(
                        shortcut = ArtifactShortcut(
                            id = type, label = "", emoji = null, identifier = type, type = type,
                            title = "Quarterly revenue", language = null, content = "", version = 1, createdAt = 0,
                        ),
                        onDelete = {},
                    )
                }
            }
        }
    }
}
