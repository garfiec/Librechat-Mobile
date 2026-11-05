package com.garfiec.librechat.feature.chat.components.mcpapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.mcp.McpAppOccurrence
import com.garfiec.librechat.core.model.mcp.selectToolCallUiResources
import com.garfiec.librechat.core.ui.components.AdaptiveCard
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedButton
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.mcp_app_load_error
import com.garfiec.librechat.feature.chat.resources.mcp_app_open
import com.garfiec.librechat.feature.chat.resources.mcp_app_open_named
import com.garfiec.librechat.feature.chat.resources.mcp_app_shared_unavailable
import org.jetbrains.compose.resources.stringResource

/** Cached per core/ui Compose perf rule 13. */
private val ROW_SHAPE = RoundedCornerShape(12.dp)

/**
 * The Apps among [attachments] that [toolCallIds] produced, numbered across the whole message, as
 * web numbers them. Pure: the rows' input.
 */
internal fun mcpAppsFor(attachments: List<Attachment>, toolCallIds: Collection<String>): List<McpAppOccurrence> {
    if (toolCallIds.isEmpty()) return emptyList()
    val ids = toolCallIds.toSet()
    return selectToolCallUiResources(attachments).filter { it.attachment.toolCallId in ids }
}

/**
 * The inert "Open app" rows for the Apps [toolCallIds] produced. Drawn outside the tool card and
 * outside any collapsed activity group, as web places them; nothing is fetched until a tap. An open
 * App draws as its inline card instead, an evicted one as "Tap to resume". Draws nothing unless the
 * server's `mcpApps.enabled` is on ([LocalMcpApps]).
 */
@Composable
internal fun McpAppRows(
    attachments: List<Attachment>,
    toolCallIds: Collection<String>,
    modifier: Modifier = Modifier,
) {
    val apps = LocalMcpApps.current ?: return
    if (!apps.enabled) return
    val occurrences = remember(attachments, toolCallIds) { mcpAppsFor(attachments, toolCallIds) }
    if (occurrences.isEmpty()) return
    val controller = apps.controller?.takeIf { !apps.readOnly }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (controller == null) {
            occurrences.forEach { occurrence ->
                key(occurrence.key) {
                    McpAppRow(occurrence = occurrence, readOnly = apps.readOnly, onOpen = { apps.open(occurrence) })
                }
            }
        } else {
            val state by controller.viewModel.state.collectAsStateWithLifecycle()
            val awaiting by controller.viewModel.awaiting.collectAsStateWithLifecycle()
            occurrences.forEach { occurrence ->
                key(occurrence.key) {
                    val session = state.sessionFor(occurrence.key)
                    when {
                        // Open: the App runs here, in its card.
                        session != null -> McpAppInlineCard(session, state, session.id in awaiting, controller)
                        occurrence.key in state.evicted ->
                            McpAppEvictedCard(occurrence, controller, onOpen = { apps.open(occurrence) })
                        else -> McpAppRow(occurrence = occurrence, readOnly = false, onOpen = { apps.open(occurrence) })
                    }
                }
            }
        }
    }
}

@Composable
private fun McpAppRow(occurrence: McpAppOccurrence, readOnly: Boolean, onOpen: () -> Unit) {
    val resource = occurrence.resource
    val toolName = resource.toolName.orEmpty()
    AdaptiveCard(
        modifier = Modifier.fillMaxWidth(),
        shape = ROW_SHAPE,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Widgets,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = toolName,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(
                        text = resource.serverName.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val note = when {
                    // Web shows this only without inline HTML; a shared view never runs either kind.
                    readOnly -> Res.string.mcp_app_shared_unavailable
                    resource.serverBinding.isNullOrEmpty() -> Res.string.mcp_app_load_error
                    else -> null
                }
                if (note != null) {
                    Text(
                        text = stringResource(note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!readOnly && !resource.serverBinding.isNullOrEmpty()) {
                val label = stringResource(Res.string.mcp_app_open_named, toolName, occurrence.ordinal)
                AdaptiveOutlinedButton(
                    onClick = onOpen,
                    modifier = Modifier.semantics { contentDescription = label },
                ) {
                    Text(stringResource(Res.string.mcp_app_open))
                }
            }
        }
    }
}
