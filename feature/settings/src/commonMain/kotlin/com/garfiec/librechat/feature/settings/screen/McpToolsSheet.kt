package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.mcp.McpTool
import com.garfiec.librechat.core.ui.components.AdaptiveModalBottomSheet
import com.garfiec.librechat.core.ui.components.LowProfileDragHandle
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import org.jetbrains.compose.resources.stringResource

/**
 * Every MCP tool the user can reach, grouped under its server's title as the cards show it
 * ([serverTitles], by stored name; the stored name when untitled). One server's tools are in its own sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun McpToolsSheet(
    tools: List<McpTool>,
    serverTitles: Map<String, String>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val unknownServer = stringResource(Res.string.mcp_server_unknown)
    val toolsByServer = tools.groupBy { it.serverName ?: unknownServer }

    AdaptiveModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { LowProfileDragHandle() },
        sheetState = sheetState,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(
                text = stringResource(Res.string.mcp_tools_all),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp).semantics { heading() },
            )

            if (tools.isEmpty()) {
                Text(
                    text = stringResource(Res.string.no_tools_available),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    toolsByServer.forEach { (serverName, serverTools) ->
                        item(key = "hdr:$serverName", contentType = "header") {
                            Text(
                                text = serverTitles[serverName] ?: serverName,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp).semantics { heading() },
                            )
                        }
                        items(
                            items = serverTools,
                            key = { "tool:${it.serverName}::${it.name}" },
                            contentType = { "mcp_tool" },
                        ) { tool ->
                            McpToolRow(tool = tool)
                        }
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }
    }
}

/** A tool's identifier in monospace, since that is what a model calls, over its description. */
@Composable
internal fun McpToolRow(tool: McpTool, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = tool.name,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
        )
        val description = tool.description
        if (!description.isNullOrBlank()) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
