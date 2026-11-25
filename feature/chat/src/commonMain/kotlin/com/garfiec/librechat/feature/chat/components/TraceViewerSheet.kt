package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.garfiec.librechat.feature.chat.viewmodel.TraceViewerViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * The conversation trace (v0.8.8-rc3): what the tracing backend recorded for each turn.
 *
 * A deliberately reduced port of upstream's desktop viewer. Its zoomable timeline, collapsible
 * record tree, text filter and Langfuse session link are all absent — they trade screen for
 * navigation, which is the wrong trade on a phone for a surface opened to answer one question.
 * What is kept is what answers it: turns newest first, each record's kind, model, duration and
 * status, and the record detail behind a tap.
 */
@Composable
internal fun TraceViewerSheet(
    conversationId: String,
    isStreaming: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TraceViewerViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(conversationId) { viewModel.openFor(conversationId) }

    // A run that SETTLES while the sheet is open added a turn, so the newest page is re-read.
    // The previous value is tracked rather than keying the effect on `isStreaming` alone: that
    // fires on first composition too, which would re-read the page `openFor` has just fetched.
    var wasStreaming by remember { mutableStateOf(isStreaming) }
    LaunchedEffect(isStreaming) {
        val settled = wasStreaming && !isStreaming
        wasStreaming = isStreaming
        if (settled) viewModel.refresh()
    }

    TraceViewerSheetContent(
        uiState = uiState,
        onCloseDetail = viewModel::closeDetail,
        onRefresh = viewModel::refresh,
        onSelect = viewModel::select,
        onLoadOlder = viewModel::loadOlder,
        onRetry = viewModel::retry,
        onDismiss = onDismiss,
        modifier = modifier,
    )
}
