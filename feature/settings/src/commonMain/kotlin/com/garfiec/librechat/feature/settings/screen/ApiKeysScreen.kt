package com.garfiec.librechat.feature.settings.screen

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.feature.settings.viewmodel.ApiKeysViewModel
import org.koin.compose.viewmodel.koinViewModel

/** Lists user API keys with create (FAB), delete, and pull-to-refresh. */
@Composable
fun ApiKeysScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ApiKeysViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        val error = uiState.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = error)
        viewModel.dismissError()
    }

    ApiKeysContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::loadKeys,
        onShowCreateDialog = viewModel::showCreateDialog,
        onCreateKey = viewModel::createKey,
        onDismissCreateDialog = viewModel::dismissCreateDialog,
        onDeleteKey = viewModel::deleteKey,
        modifier = modifier,
    )
}
