package com.garfiec.librechat.feature.conversations.screen

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.feature.conversations.resources.Res
import com.garfiec.librechat.feature.conversations.resources.project_instructions_conflict
import com.garfiec.librechat.feature.conversations.resources.project_instructions_save_failed
import com.garfiec.librechat.feature.conversations.resources.project_instructions_view_theirs
import com.garfiec.librechat.feature.conversations.viewmodel.ProjectInstructionsEvent
import com.garfiec.librechat.feature.conversations.viewmodel.ProjectInstructionsViewModel
import org.jetbrains.compose.resources.getString
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * A project's instructions, full screen (v0.8.9-rc1). A route rather than a sheet: the text can run
 * to 16,000 characters by default (200,000 at most) and a sheet fights the keyboard at that length.
 * Close and back ask before dropping a draft, and do nothing while a save runs.
 */
@Composable
fun ProjectInstructionsScreen(
    projectId: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProjectInstructionsViewModel = koinViewModel { parametersOf(projectId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnClose by rememberUpdatedState(onClose)
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.exitRequested) {
        if (state.exitRequested) currentOnClose()
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ProjectInstructionsEvent.Conflict -> {
                    val result = snackbarHostState.showSnackbar(
                        message = getString(Res.string.project_instructions_conflict),
                        actionLabel = getString(Res.string.project_instructions_view_theirs),
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.viewTheirs()
                }
                ProjectInstructionsEvent.SaveFailed ->
                    snackbarHostState.showSnackbar(getString(Res.string.project_instructions_save_failed))
            }
        }
    }

    ProjectInstructionsScreenContent(
        uiState = state,
        snackbarHostState = snackbarHostState,
        onBack = viewModel::onBackRequested,
        onConfirmDiscard = viewModel::confirmDiscard,
        onDismissDiscard = viewModel::dismissDiscard,
        onSave = viewModel::save,
        onRetry = viewModel::load,
        onDraftChange = viewModel::onDraftChange,
        onUseTheirs = viewModel::useTheirs,
        onKeepMine = viewModel::keepMine,
        modifier = modifier,
    )
}
