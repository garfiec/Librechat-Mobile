package com.garfiec.librechat.feature.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.feature.settings.viewmodel.McpServerEditorViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** The full-screen add/edit form for one MCP server, grouped as the web's form is. */
@Composable
fun McpServerEditorScreen(
    serverName: String?,
    onBack: () -> Unit,
    onSaveComplete: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: McpServerEditorViewModel = koinViewModel { parametersOf(serverName) },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnSaveComplete by rememberUpdatedState(onSaveComplete)
    val currentOnBack by rememberUpdatedState(onBack)

    LaunchedEffect(uiState.savedServerName) {
        if (uiState.savedServerName != null) currentOnSaveComplete()
    }
    LaunchedEffect(uiState.exitRequested) {
        if (uiState.exitRequested) currentOnBack()
    }
    McpServerEditorContent(
        uiState = uiState,
        onBackRequest = viewModel::onBackRequested,
        onSave = viewModel::save,
        onChange = viewModel::update,
        onReload = viewModel::reload,
        onDiscardChanges = viewModel::discardChanges,
        onDismissDiscardConfirmation = viewModel::dismissDiscardConfirmation,
        modifier = modifier,
    )
}
