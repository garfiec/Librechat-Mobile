package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.content.MessageContentPart
import com.garfiec.librechat.core.model.subagent.SubagentOrigin
import com.garfiec.librechat.core.model.subagent.SubagentSummary
import com.garfiec.librechat.core.model.subagent.SubagentThreadView
import com.garfiec.librechat.core.model.subagent.hasTruncatedContent
import com.garfiec.librechat.core.model.subagent.toContentParts
import com.garfiec.librechat.core.ui.components.LoadingIndicator
import com.garfiec.librechat.core.ui.components.LowProfileDragHandle
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.subagent_threads_back
import com.garfiec.librechat.feature.chat.resources.subagent_threads_empty
import com.garfiec.librechat.feature.chat.resources.subagent_threads_event_origin
import com.garfiec.librechat.feature.chat.resources.subagent_threads_load_older
import com.garfiec.librechat.feature.chat.resources.subagent_threads_older_unavailable
import com.garfiec.librechat.feature.chat.resources.subagent_threads_open
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
        dragHandle = { LowProfileDragHandle() },
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (uiState.isShowingThread) {
                    IconButton(onClick = viewModel::backToList) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.subagent_threads_back),
                        )
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
                    LoadingIndicator(Modifier.fillMaxWidth().height(120.dp))

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
                    // Weighted so the error line below keeps its space: the sheet's Column is
                    // height-capped, and an unweighted list long enough to fill it measures
                    // everything after it at zero — which is the only feedback a failed read has.
                    modifier = Modifier.weight(1f, fill = false),
                )

                uiState.children.isEmpty() ->
                    Message(stringResource(Res.string.subagent_threads_empty))

                else -> ChildList(
                    children = uiState.children,
                    truncated = uiState.childrenTruncated,
                    onOpen = { viewModel.openThread(parentConversationId, it) },
                    modifier = Modifier.weight(1f, fill = false),
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
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier.fillMaxWidth()) {
        items(children, key = { it.threadId }, contentType = { "child" }) { child ->
            // `status` is nullable and routinely absent, so the separator is joined only between
            // the parts that are actually there — otherwise an event-spawned child reads "Started
            // by an event · " and a status-less tool child draws an empty supporting line that
            // still occupies a row.
            val origin = stringResource(Res.string.subagent_threads_event_origin)
                .takeIf { child.origin == SubagentOrigin.EVENT }
            val supporting = listOfNotNull(origin, child.status?.takeIf { it.isNotBlank() })
                .joinToString(" · ")
            ListItem(
                headlineContent = {
                    Text(
                        text = child.title.ifBlank { child.subagentType },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                supportingContent = supporting.takeIf { it.isNotEmpty() }?.let { line ->
                    { Text(text = line, style = MaterialTheme.typography.bodySmall) }
                },
                leadingContent = {
                    Icon(Icons.Default.AccountTree, contentDescription = null, modifier = Modifier.size(20.dp))
                },
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { onOpen(child.threadId) }, modifier = Modifier.padding(start = 8.dp)) {
                Text(stringResource(Res.string.subagent_threads_open))
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
    modifier: Modifier = Modifier,
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
    //
    // `loadOlder` REPLACES the thread rather than appending, so every position shifts when an
    // older page lands. Keyed by position, a tool-call card would inherit the expansion state of
    // whatever unrelated part now sits at its index.
    //
    // Keyed on the SOURCE list, not on the converted one: the sheet recomposes on every state
    // change while it is open, and converting first would rebuild a part per activity item on each
    // pass only for `remember` to deep-compare the fresh list away.
    val keyedParts = remember(view) { view.renderedActivity.toContentParts().withStableKeys() }
    LazyColumn(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (olderUnavailable) {
            item(key = "older-unavailable", contentType = "notice") {
                // The retained chain vanished between requests, so no cursor brings it back. A
                // load-more here would spin on nothing for ever.
                Text(
                    text = stringResource(Res.string.subagent_threads_older_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (canLoadOlder) {
            item(key = "load-older", contentType = "action") {
                TextButton(onClick = onLoadOlder, enabled = !isLoadingOlder) {
                    Text(stringResource(Res.string.subagent_threads_load_older))
                }
            }
        }
        items(
            items = keyedParts,
            key = { it.key },
            contentType = { it.part.type },
        ) { (key, part) ->
            if (part.type == ContentType.ACTIVITY_LABEL) {
                OrphanActivityLabel(part.activityLabel.orEmpty())
            } else {
                ContentPartDispatcher(
                    part = part,
                    stateKey = "subagent-thread:$key",
                    // A child never renders another child's card; depth is bounded to one upstream.
                    allowSubagentCard = false,
                    // The projection carries no attachments of its own, so nothing is hoisted here.
                    hideAttachments = true,
                )
            }
        }
        if (view.activityTruncated || view.activity.hasTruncatedContent) {
            item(key = "truncated", contentType = "notice") {
                Text(
                    text = stringResource(Res.string.subagent_threads_truncated),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** One rendered part plus the key that follows it across a thread replacement. */
private data class KeyedThreadPart(val key: String, val part: MessageContentPart)

/**
 * Stable keys for a thread's parts.
 *
 * A tool call carries a real id. Nothing else does, so the rest are identified by what they
 * render — stable for the same logical part, and identical only between parts that draw
 * identically. Duplicate-key crashes are not acceptable either way, so a repeat is suffixed with
 * its occurrence: two identical rows can still swap state, which is invisible precisely because
 * they render the same.
 */
private fun List<MessageContentPart>.withStableKeys(): List<KeyedThreadPart> {
    val seen = mutableMapOf<String, Int>()
    return map { part ->
        // `think` and the tool name are in the chain because `toContentParts` fills exactly one
        // field per arm: a REASONING item lands in `think` with `text` null, so reading `text`
        // alone identifies every reasoning block as the empty string and collapses the whole
        // column back onto positional keys — the defect these keys exist to prevent. Same for a
        // TOOL arm the projection served without a `toolCallId`.
        val identity = part.toolCall?.id?.takeIf { it.isNotEmpty() }
            ?: part.activityLabel?.takeIf { it.isNotEmpty() }
            ?: part.text?.takeIf { it.isNotEmpty() }
            ?: part.think?.takeIf { it.isNotEmpty() }
            ?: part.toolCall?.name?.takeIf { it.isNotEmpty() }
            ?: ""
        val base = "${part.type}:${identity.hashCode()}"
        // Not `MutableMap.merge`: that is a java.util.Map default method with no Kotlin/Native
        // counterpart, so it compiles on Android and breaks the iOS build from commonMain.
        val occurrence = (seen[base] ?: 0) + 1
        seen[base] = occurrence
        KeyedThreadPart(if (occurrence == 1) base else "$base#$occurrence", part)
    }
}
