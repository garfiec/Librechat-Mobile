package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.resources.action_retry
import com.garfiec.librechat.feature.settings.viewmodel.MemoriesViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * The Memories page: the opt-out switch and pool usage, then search, the partition chips and the
 * memory cards. The switch card stays on screen in every state, the first-load error included.
 */
@Composable
fun MemoriesScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MemoriesViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val retryLabel = stringResource(Res.string.action_retry)
    val gridState = rememberLazyGridState()

    LaunchedEffect(viewModel) {
        showMemoryFailures(
            events = viewModel.events,
            snackbarHostState = snackbarHostState,
            retryLabel = retryLabel,
            onRetry = viewModel::retry,
        )
    }
    LaunchedEffect(viewModel) {
        viewModel.scrollToTop.collect { gridState.animateScrollToItem(0) }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshOnReturn() }

    val actions = remember(viewModel) {
        MemoriesActions(
            openCreate = viewModel::openCreate,
            refresh = viewModel::refresh,
            setMemoriesEnabled = viewModel::setMemoriesEnabled,
            retryPreference = viewModel::retryPreference,
            retryLoad = viewModel::retryLoad,
            updateQuery = viewModel::updateQuery,
            selectPartition = viewModel::selectPartition,
            openMemory = viewModel::openMemory,
            updateEditorKey = viewModel::updateEditorKey,
            updateEditorValue = viewModel::updateEditorValue,
            dismissEditor = viewModel::dismissEditor,
            saveEditor = viewModel::saveEditor,
            confirmDiscard = viewModel::confirmDiscard,
            keepEditing = viewModel::keepEditing,
            deleteMemory = viewModel::deleteMemory,
        )
    }
    MemoriesContent(
        uiState = uiState,
        actions = actions,
        snackbarHostState = snackbarHostState,
        gridState = gridState,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}
