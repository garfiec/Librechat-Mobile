package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.subagent.SubagentOrigin
import com.garfiec.librechat.core.model.subagent.SubagentSummary
import com.garfiec.librechat.core.model.subagent.SubagentThreadView
import com.garfiec.librechat.core.model.subagent.hasTruncatedContent
import com.garfiec.librechat.core.model.subagent.toContentParts
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.subagent_threads_empty
import com.garfiec.librechat.feature.chat.resources.subagent_threads_event_origin
import com.garfiec.librechat.feature.chat.resources.subagent_threads_load_older
import com.garfiec.librechat.feature.chat.resources.subagent_threads_older_unavailable
import com.garfiec.librechat.feature.chat.resources.subagent_threads_title
import com.garfiec.librechat.feature.chat.resources.subagent_threads_truncated
import com.garfiec.librechat.feature.chat.resources.subagent_threads_unavailable
import com.garfiec.librechat.feature.chat.viewmodel.SubagentThreadsViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Read-only viewer for the child threads a conversation spawned (v0.8.8-rc2).
 *
 * Opens on the LIST, even when a trace card asked for one child: an event-spawned child has no
 * parent tool call and therefore no card, so the list is the only surface from which it can ever
 * be reached. The card's id is used to jump straight into its own thread once the index resolves
 * the join.
 *
 * Read-only throughout. The control route exists and is deliberately not called — see
 * `DISCOVERY.md`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SubagentThreadsSheet(
    parentConversationId: String,
    focusParentToolCallId: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SubagentThreadsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(parentConversationId, focusParentToolCallId) {
        viewModel.openFor(parentConversationId, focusParentToolCallId)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, modifier = modifier) {
        Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (uiState.isShowingThread) {
                    IconButton(onClick = viewModel::backToList) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
                Text(
                    text = uiState.openThread?.title?.ifBlank { null }
                        ?: stringResource(Res.string.subagent_threads_title),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(8.dp))

            when {
                uiState.isLoadingIndex || uiState.isLoadingThread ->
                    Box(Modifier.fillMaxWidth().height(120.dp)) {
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                    }

                // One 404 covers "no children", "not yours" and "this IS a child thread". None is
                // a verdict about the server, so it says nothing about other conversations.
                uiState.hasNoChildView ->
                    Message(stringResource(Res.string.subagent_threads_unavailable))

                uiState.openThread != null -> ThreadBody(
                    view = uiState.openThread!!,
                    canLoadOlder = uiState.canLoadOlder,
                    olderUnavailable = uiState.olderHistoryUnavailable,
                    isLoadingOlder = uiState.isLoadingOlder,
                    onLoadOlder = { viewModel.loadOlder(parentConversationId) },
                )

                uiState.children.isEmpty() ->
                    Message(stringResource(Res.string.subagent_threads_empty))

                else -> ChildList(
                    children = uiState.children,
                    truncated = uiState.childrenTruncated,
                    onOpen = { viewModel.openThread(parentConversationId, it) },
                )
            }

            uiState.error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 24.dp),
    )
}

@Composable
private fun ChildList(
    children: List<SubagentSummary>,
    truncated: Boolean,
    onOpen: (String) -> Unit,
) {
    LazyColumn(Modifier.fillMaxWidth()) {
        items(children, key = { it.threadId }) { child ->
            ListItem(
                headlineContent = {
                    Text(
                        text = child.title.ifBlank { child.subagentType },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                supportingContent = {
                    val suffix = child.status.orEmpty()
                    Text(
                        text = if (child.origin == SubagentOrigin.EVENT) {
                            stringResource(Res.string.subagent_threads_event_origin) + " · " + suffix
                        } else {
                            suffix
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                leadingContent = {
                    Icon(Icons.Default.AccountTree, contentDescription = null, modifier = Modifier.size(20.dp))
                },
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { onOpen(child.threadId) }, modifier = Modifier.padding(start = 8.dp)) {
                Text(stringResource(Res.string.subagent_threads_title))
            }
        }
        if (truncated) {
            item {
                Text(
                    text = stringResource(Res.string.subagent_threads_truncated),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ThreadBody(
    view: SubagentThreadView,
    canLoadOlder: Boolean,
    olderUnavailable: Boolean,
    isLoadingOlder: Boolean,
    onLoadOlder: () -> Unit,
) {
    // The view's own activity shape converts to the content parts the app already draws, so the
    // shared dispatcher renders the child's reasoning, tools and text instead of a second set of
    // renderers built for this sheet.
    //
    // Activity LABELS are the exception and render flat. Grouping is a separate pure transform
    // applied at `MessageContentAndActions`, not something the dispatcher does — a bare label
    // through it draws nothing at all. Folding the child's tools under collapsible headers would
    // mean running that transform here too; a read-only trace does not need it, and a label that
    // silently vanished would be worse than one shown as the line it is.
    val parts = view.activity.toContentParts()
    LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (olderUnavailable) {
            item {
                // The retained chain vanished between requests, so no cursor brings it back. A
                // load-more here would spin on nothing for ever.
                Text(
                    text = stringResource(Res.string.subagent_threads_older_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (canLoadOlder) {
            item {
                TextButton(onClick = onLoadOlder, enabled = !isLoadingOlder) {
                    Text(stringResource(Res.string.subagent_threads_load_older))
                }
            }
        }
        items(parts.size) { index ->
            val part = parts[index]
            if (part.type == ContentType.ACTIVITY_LABEL) {
                OrphanActivityLabel(part.activityLabel.orEmpty())
            } else {
                ContentPartDispatcher(
                    part = part,
                    stateKey = "subagent-thread:$index",
                    // A child never renders another child's card; depth is bounded to one upstream.
                    allowSubagentCard = false,
                    // The projection carries no attachments of its own, so nothing is hoisted here.
                    hideAttachments = true,
                )
            }
        }
        if (view.activityTruncated || view.activity.hasTruncatedContent) {
            item {
                Text(
                    text = stringResource(Res.string.subagent_threads_truncated),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
