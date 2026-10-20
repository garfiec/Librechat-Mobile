package com.garfiec.librechat.feature.settings.screen

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.garfiec.librechat.core.model.mcp.McpAuthMode
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.settings.viewmodel.McpServerDraft
import com.garfiec.librechat.feature.settings.viewmodel.McpServerEditorUiState

/** A new OAuth server, filled in up to the trust row, so every section shows. */
private val addingLinear = McpServerEditorUiState(
    draft = McpServerDraft(
        name = "Linear",
        description = "Issues and projects for the mobile team",
        url = "https://mcp.linear.app/sse",
        authMode = McpAuthMode.OAUTH,
        oauthClientId = "switchboard",
    ),
    isLoading = false,
)

@PreviewNarrowScreens
@Composable
private fun McpEditorMaterialPreview() = McpEditorHost(dark = false, UiStyle.MATERIAL)

@PreviewNarrowScreens
@Composable
private fun McpEditorGlassPreview() = McpEditorHost(dark = true, UiStyle.LIQUID_GLASS)

@Composable
private fun McpEditorHost(dark: Boolean, style: UiStyle) {
    LibreChatTheme(darkTheme = dark, uiStyle = style) {
        Surface(color = MaterialTheme.colorScheme.background) {
            McpServerEditorForm(uiState = addingLinear, onChange = {}, onReload = {})
        }
    }
}
