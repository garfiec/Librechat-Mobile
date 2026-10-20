package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.mcp.McpDisplayStatus
import com.garfiec.librechat.core.model.mcp.McpTool
import com.garfiec.librechat.core.ui.components.AdaptiveFilledTonalButton
import com.garfiec.librechat.core.ui.components.AdaptiveModalBottomSheet
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedButton
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedTextField
import com.garfiec.librechat.core.ui.components.InitialTile
import com.garfiec.librechat.core.ui.components.LowProfileDragHandle
import com.garfiec.librechat.core.ui.components.TileStatusDot
import com.garfiec.librechat.core.ui.components.clearFocusOnTap
import com.garfiec.librechat.core.ui.platform.rememberReduceMotion
import com.garfiec.librechat.core.ui.theme.statusColors
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.viewmodel.McpServerRow
import org.jetbrains.compose.resources.stringResource

/**
 * One server: what it is, whether it works and why not, what it can do, and the actions on it.
 * Opened by tapping a card. The screen stops drawing it once the server is gone (deleted, or dropped
 * by a reload), so a delete from here closes it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun McpServerSheet(
    row: McpServerRow,
    tools: List<McpTool>,
    onDismiss: () -> Unit,
    onReconnect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by rememberSaveable(row.name) { mutableStateOf("") }
    val shownTools = remember(tools, query) {
        val needle = query.trim()
        if (needle.isEmpty()) {
            tools
        } else {
            tools.filter { it.name.contains(needle, ignoreCase = true) || it.description.orEmpty().contains(needle, ignoreCase = true) }
        }
    }

    AdaptiveModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { LowProfileDragHandle() },
        sheetState = sheetState,
        // The tool filter is a text field, and the sheet's own window never sees the app root's tap.
        modifier = modifier.clearFocusOnTap(),
    ) {
        LazyColumn(Modifier.fillMaxWidth()) {
            item(key = "header", contentType = "header") {
                McpServerSheetHeader(row, onReconnect = onReconnect, onEdit = onEdit, onDelete = onDelete)
            }
            if (row.status == McpDisplayStatus.CONNECTED || tools.isNotEmpty()) {
                item(key = "tools_title", contentType = "tools_title") {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(Res.string.mcp_tools_title),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            text = tools.size.toString(),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (tools.size > TOOL_FILTER_THRESHOLD) {
                    item(key = "tools_filter", contentType = "tools_filter") {
                        AdaptiveOutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                            placeholder = { Text(stringResource(Res.string.mcp_tools_filter_hint)) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            singleLine = true,
                        )
                    }
                }
                if (shownTools.isEmpty()) {
                    item(key = "tools_empty", contentType = "tools_empty") {
                        Text(
                            text = stringResource(
                                if (tools.isEmpty()) Res.string.no_tools_available else Res.string.mcp_tools_no_match,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                        )
                    }
                }
                items(items = shownTools, key = { "tool:${it.name}" }, contentType = { "mcp_tool" }) { tool ->
                    McpToolRow(tool = tool, modifier = Modifier.padding(horizontal = 20.dp))
                }
            }
            item(key = "bottom", contentType = "spacer") { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun McpServerSheetHeader(
    row: McpServerRow,
    onReconnect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val presentation = row.status.presentation(statusColors())
    val ring = MaterialTheme.colorScheme.surfaceContainerLow
    val reduceMotion = rememberReduceMotion()
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            InitialTile(seed = row.tileSeed, label = row.title, imageUrl = row.iconUrl, size = 48.dp) {
                TileStatusDot(
                    color = presentation.tint,
                    ringColor = ring,
                    pulsing = row.status == McpDisplayStatus.CONNECTING && !reduceMotion,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = row.title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
                if (row.description != null) {
                    Text(
                        text = row.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    presentation.label?.let { McpMetaItem(stringResource(it), presentation.icon, presentation.tint) }
                    if (!row.access.canEdit) McpMetaItem(stringResource(Res.string.mcp_view_only), Icons.Default.Lock)
                }
            }
        }
        if (row.status == McpDisplayStatus.FAILED && row.error != null) {
            Text(
                text = row.error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (row.status == McpDisplayStatus.NEEDS_SETUP) {
            Text(
                text = stringResource(Res.string.mcp_needs_setup_detail),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (row.url.isNotBlank()) {
            Text(
                text = row.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val action = row.status.cardAction()
            if (action != null) {
                AdaptiveFilledTonalButton(onClick = onReconnect) { Text(stringResource(action.label)) }
            } else {
                AdaptiveOutlinedButton(onClick = onReconnect) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Text(stringResource(Res.string.mcp_action_reconnect), modifier = Modifier.padding(start = 8.dp))
                }
            }
            if (row.access.canEdit) {
                AdaptiveOutlinedButton(onClick = onEdit) { Text(stringResource(Res.string.mcp_action_edit)) }
            }
            if (row.access.canDelete) {
                AdaptiveOutlinedButton(onClick = onDelete) {
                    Text(stringResource(Res.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/** Past this many tools a filter field earns its space; fewer fit on a screen or two. */
private const val TOOL_FILTER_THRESHOLD = 8
