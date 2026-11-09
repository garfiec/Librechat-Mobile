package com.garfiec.librechat.core.ui.mcp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.garfiec.librechat.core.model.mcp.McpUserVar
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme

/** Nothing saved yet: every field is owed, so Save waits for all of them. */
@PreviewNarrowScreens
@Composable
private fun McpVariablesUnsetMaterialPreview() = VariablesSurface(UiStyle.MATERIAL, unset)

@PreviewNarrowScreens
@Composable
private fun McpVariablesUnsetGlassPreview() = VariablesSurface(UiStyle.LIQUID_GLASS, unset)

/** Both saved: empty fields keep them, the button only connects, and Remove is offered. */
@PreviewNarrowScreens
@Composable
private fun McpVariablesSetMaterialPreview() = VariablesSurface(UiStyle.MATERIAL, allSet)

@PreviewNarrowScreens
@Composable
private fun McpVariablesSetGlassPreview() = VariablesSurface(UiStyle.LIQUID_GLASS, allSet)

@Composable
private fun VariablesSurface(style: UiStyle, flags: Map<String, Boolean>) {
    LibreChatTheme(darkTheme = true, uiStyle = style) {
        Box(Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) {
            McpVariablesSheetContent(
                serverLabel = "GitHub",
                vars = vars,
                flags = flags,
                revealed = emptySet(),
                onRevealedChange = { _, _ -> },
                onBusyChange = {},
                onSave = { true },
                onRemove = { true },
                onDismiss = {},
            )
        }
    }
}

private val vars = listOf(
    McpUserVar(
        key = "GITHUB_TOKEN",
        title = "Personal access token",
        description = "A classic token with repo scope. <a href=\"https://github.com/settings/tokens\">Create one</a>",
    ),
    McpUserVar(key = "GITHUB_USER", title = "Username", description = "", sensitive = false),
)

private val unset = mapOf("GITHUB_TOKEN" to false, "GITHUB_USER" to false)
private val allSet = mapOf("GITHUB_TOKEN" to true, "GITHUB_USER" to true)
