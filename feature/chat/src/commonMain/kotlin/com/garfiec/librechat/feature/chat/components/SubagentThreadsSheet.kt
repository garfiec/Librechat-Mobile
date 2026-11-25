package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.garfiec.librechat.feature.chat.viewmodel.SubagentThreadsViewModel
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
@Composable
internal fun SubagentThreadsSheet(
    parentConversationId: String,
    focusParentToolCallId: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    focusThreadId: String? = null,
    viewModel: SubagentThreadsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(parentConversationId, focusParentToolCallId, focusThreadId) {
        viewModel.openFor(parentConversationId, focusParentToolCallId, focusThreadId)
    }
    SubagentThreadsSheetContent(
        uiState = uiState,
        onBack = viewModel::backToList,
        onLoadOlder = { viewModel.loadOlder(parentConversationId) },
        onOpenThread = { viewModel.openThread(parentConversationId, it) },
        onDismiss = onDismiss,
        modifier = modifier,
    )
}
