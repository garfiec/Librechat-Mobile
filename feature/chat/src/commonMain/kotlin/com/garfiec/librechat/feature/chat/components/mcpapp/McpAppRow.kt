package com.garfiec.librechat.feature.chat.components.mcpapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.mcp.McpAppOccurrence
import com.garfiec.librechat.core.model.mcp.selectToolCallUiResources

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
