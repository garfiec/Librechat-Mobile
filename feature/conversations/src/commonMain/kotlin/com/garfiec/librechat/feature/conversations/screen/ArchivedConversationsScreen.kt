package com.garfiec.librechat.feature.conversations.screen

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.feature.conversations.resources.*
import com.garfiec.librechat.feature.conversations.viewmodel.ArchivedConversationsEvent
import com.garfiec.librechat.feature.conversations.viewmodel.ArchivedConversationsViewModel
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivedConversationsScreen(
    onNavigateBack: () -> Unit,
    onConversationClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ArchivedConversationsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ArchivedConversationsEvent.ShowError -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    ArchivedConversationsScreenContent(
        state = uiState,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onConversationClick = onConversationClick,
        onRefresh = { viewModel.refresh() },
        onRetry = {
            viewModel.dismissError()
            viewModel.loadArchivedConversations()
        },
        onLoadMore = viewModel::loadMore,
        onRetryLoadMore = viewModel::retryLoadMore,
        onUnarchive = viewModel::unarchiveConversation,
        onDelete = viewModel::deleteConversation,
        modifier = modifier,
    )
}
