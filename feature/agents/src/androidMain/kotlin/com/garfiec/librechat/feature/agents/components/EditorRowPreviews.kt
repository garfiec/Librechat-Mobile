package com.garfiec.librechat.feature.agents.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.agents.components.model.AgentAdvancedSettings
import com.garfiec.librechat.feature.agents.components.model.AgentSharingState
import com.garfiec.librechat.feature.agents.screen.InstructionsField
import com.garfiec.librechat.feature.agents.screen.SelectedToolRow

// The agent editor's collapsible headers and rows, hosted in the form's 16dp gutters.

@PreviewNarrowScreens
@Composable
private fun EditorHeadersMaterialPreview() = EditorRowSurface(UiStyle.MATERIAL) { EditorHeadersHost() }

@PreviewNarrowScreens
@Composable
private fun EditorHeadersGlassPreview() = EditorRowSurface(UiStyle.LIQUID_GLASS) { EditorHeadersHost() }

@Composable
private fun EditorRowSurface(style: UiStyle, content: @Composable () -> Unit) {
    LibreChatTheme(darkTheme = true, uiStyle = style) {
        // The form's text takes its colour from the screen's surface, which a preview has none of.
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground, content = content)
    }
}

/** Every collapsible section header the editor stacks, plus the instructions header and a tool row. */
@Composable
private fun EditorHeadersHost() {
    var instructions by remember { mutableStateOf("") }
    var sharing by remember { mutableStateOf(AgentSharingState()) }
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(16.dp)) {
        InstructionsField(value = instructions, onValueChange = { instructions = it })
        SelectedToolRow(
            toolName = "web_search",
            toolDescription = "Searches the web and cites the pages it read for every answer",
            onRemove = {},
        )
        AgentAdvancedPanel(
            settings = AgentAdvancedSettings(),
            onSettingsChange = {},
            provider = "openAI",
            model = "gpt-4o",
            extendedEffortSupported = false,
        )
        AgentActionsPanel(actions = emptyList(), onSaveAction = { _, _, _ -> }, onDeleteAction = {})
        AgentSharingSection(sharingState = sharing, onSharingChange = { sharing = it })
        AgentChainSection(
            chainAgentIds = listOf("a", "b"),
            availableAgents = emptyList(),
            chainMax = 10,
            onAddAgent = {},
            onRemoveAgent = {},
        )
        AgentHandoffsSection(
            edges = emptyList(),
            availableAgents = emptyList(),
            currentAgentId = null,
            onAddEdge = {},
            onUpdateEdge = { _, _ -> },
            onRemoveEdge = {},
        )
    }
}
