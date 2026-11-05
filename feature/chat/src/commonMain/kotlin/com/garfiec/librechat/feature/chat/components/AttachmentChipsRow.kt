package com.garfiec.librechat.feature.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.common.ToolConstants
import com.garfiec.librechat.core.ui.components.AdaptiveAssistChip
import com.garfiec.librechat.feature.chat.model.McpServerDisplayData
import com.garfiec.librechat.feature.chat.resources.*
import com.garfiec.librechat.feature.chat.resources.Res
import org.jetbrains.compose.resources.stringResource

/**
 * Floating row of chips above the chat input field naming the enabled tools (Web Search, Code,
 * File Search, Skills) and the selected MCP servers. Shown only when there is a chip to display.
 * Files, picked skills and quotes are the field's attachment stack ([ComposerAttachmentStack]).
 */
@Composable
fun AttachmentChipsRow(
    enabledTools: Set<String>,
    modifier: Modifier = Modifier,
    mcpServers: List<McpServerDisplayData> = emptyList(),
    selectedMcpServerNames: Set<String> = emptySet(),
    /**
     * Whether ephemeral tool selections (web search / code / file search / MCP) should be
     * shown as chips. False for the agents endpoint, where the underlying selections are
     * retained but not sent (see [ChatUiState.showEphemeralTools]); only the display is
     * suppressed so switching back to a concrete model restores the chips.
     */
    showEphemeralTools: Boolean = true,
) {
    // Memoize ephemeral state once to avoid repeated guards and per-recomposition MCP filtering when tools are hidden.
    val ephemeral = remember(showEphemeralTools, enabledTools, mcpServers, selectedMcpServerNames) {
        if (!showEphemeralTools) {
            EphemeralChips()
        } else {
            EphemeralChips(
                hasWebSearch = ToolConstants.WEB_SEARCH in enabledTools,
                hasCode = ToolConstants.CODE_INTERPRETER in enabledTools,
                hasFileSearch = ToolConstants.FILE_SEARCH in enabledTools,
                // Present only where the toggle is offered and on (see ChatUiState.effectiveEnabledTools).
                hasSkills = ToolConstants.SKILLS in enabledTools,
                selectedMcpServers = mcpServers.filter { it.name in selectedMcpServerNames },
            )
        }
    }
    val hasWebSearch = ephemeral.hasWebSearch
    val hasCode = ephemeral.hasCode
    val hasFileSearch = ephemeral.hasFileSearch
    val hasSkills = ephemeral.hasSkills
    val selectedMcpServers = ephemeral.selectedMcpServers
    val hasAnyChip = hasWebSearch || hasCode || hasFileSearch || hasSkills || selectedMcpServers.isNotEmpty()

    AnimatedVisibility(
        visible = hasAnyChip,
        enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
        exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut(),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (hasWebSearch) {
                ToolIndicatorChip(
                    label = stringResource(Res.string.tool_web_search),
                    icon = Icons.Default.TravelExplore,
                    semanticDescription = stringResource(Res.string.cd_web_search_enabled),
                )
            }
            if (hasCode) {
                ToolIndicatorChip(
                    label = stringResource(Res.string.tool_code),
                    icon = Icons.Default.Code,
                    semanticDescription = stringResource(Res.string.cd_code_enabled),
                )
            }
            if (hasFileSearch) {
                ToolIndicatorChip(
                    label = stringResource(Res.string.tool_file_search),
                    icon = Icons.Default.FindInPage,
                    semanticDescription = stringResource(Res.string.cd_file_search_enabled),
                )
            }
            // Skills chip (per-conversation toggle)
            if (hasSkills) {
                ToolIndicatorChip(
                    label = stringResource(Res.string.tool_skills),
                    icon = Icons.Default.Extension,
                    semanticDescription = stringResource(Res.string.cd_skills_enabled),
                )
            }
            // MCP server chips — one chip per selected server
            selectedMcpServers.forEach { server ->
                ToolIndicatorChip(
                    label = server.title ?: server.name,
                    icon = Icons.Default.Extension,
                    semanticDescription = stringResource(Res.string.cd_mcp_server_enabled, server.title ?: server.name),
                )
            }
        }
    }
}

/**
 * Display-only tool indicator chip. Shows that a tool or MCP server is enabled.
 * Not interactive — users manage tools via the tools bottom sheet.
 */
@Composable
private fun ToolIndicatorChip(
    label: String,
    icon: ImageVector,
    semanticDescription: String,
) {
    AdaptiveAssistChip(
        onClick = {},
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            leadingIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        modifier = Modifier.semantics {
            contentDescription = semanticDescription
        },
    )
}

private data class EphemeralChips(
    val hasWebSearch: Boolean = false,
    val hasCode: Boolean = false,
    val hasFileSearch: Boolean = false,
    val hasSkills: Boolean = false,
    val selectedMcpServers: List<McpServerDisplayData> = emptyList(),
)
