package com.garfiec.librechat.feature.schedules.screen

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import com.garfiec.librechat.feature.schedules.viewmodel.ScheduleEditorViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ScheduleEditorScreen(
    scheduleId: String?,
    onBack: () -> Unit,
    onSaveComplete: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScheduleEditorViewModel = koinViewModel { parametersOf(scheduleId) },
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val currentOnSaveComplete by rememberUpdatedState(onSaveComplete)
    val currentOnBack by rememberUpdatedState(onBack)

    LaunchedEffect(uiState.savedScheduleId) {
        if (uiState.savedScheduleId != null) currentOnSaveComplete()
    }
    LaunchedEffect(uiState.exitRequested) {
        if (uiState.exitRequested) currentOnBack()
    }
    LaunchedEffect(uiState.error) {
        // A conflict keeps its message on screen with a Reload beside it; everything else is a
        // transient report.
        if (!uiState.hasConflict) {
            uiState.error?.let {
                snackbarHostState.showSnackbar(it)
                viewModel.clearError()
            }
        }
    }

    ScheduleEditorScreenContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = viewModel::onBackRequested,
        onDiscardChanges = viewModel::discardChanges,
        onDismissDiscard = viewModel::dismissDiscardConfirmation,
        onSave = viewModel::save,
        onReload = viewModel::reload,
        onChange = viewModel::update,
        modifier = modifier,
    )
}
