package com.garfiec.librechat.feature.conversations.screen

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.feature.conversations.viewmodel.BookmarksEvent
import com.garfiec.librechat.feature.conversations.viewmodel.BookmarksFailure
import com.garfiec.librechat.feature.conversations.viewmodel.BookmarksViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Every bookmark of the account as cards: tap one for its chats, ⋮ to edit or delete it, hold one
 * to move it. [onBookmarkClick] opens the bookmark's chats. [onCloseSelf] runs once bookmarks are
 * turned off for the account (role or `interface.bookmarks`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    onBookmarkClick: (name: String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onCloseSelf: () -> Unit = onNavigateBack,
    viewModel: BookmarksViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshOnReturn() }

    val currentOnCloseSelf by rememberUpdatedState(onCloseSelf)
    LaunchedEffect(uiState.enabled) {
        if (!uiState.enabled) currentOnCloseSelf()
    }

    // Resolved in composition, so an in-app language switch reaches the snackbar too.
    val failureMessages by rememberUpdatedState(BookmarksFailure.entries.associateWith { stringResource(it.message) })
    LaunchedEffect(viewModel, snackbarHostState) {
        viewModel.events.collect { event ->
            when (event) {
                is BookmarksEvent.Failed -> snackbarHostState.showSnackbar(failureMessages.getValue(event.failure))
            }
        }
    }

    BookmarksScreenContent(
        state = uiState,
        snackbarHostState = snackbarHostState,
        onBookmarkClick = onBookmarkClick,
        onNavigateBack = onNavigateBack,
        onCreate = viewModel::openCreate,
        onRefresh = viewModel::refresh,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onRetry = viewModel::retry,
        onDrop = viewModel::onDrop,
        onEdit = viewModel::openEdit,
        onMoveBy = viewModel::moveBy,
        onSave = viewModel::save,
        onCloseEditor = viewModel::closeEditor,
        onDelete = viewModel::delete,
        modifier = modifier,
    )
}
