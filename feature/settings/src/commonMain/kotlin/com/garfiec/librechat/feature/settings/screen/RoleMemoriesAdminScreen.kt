package com.garfiec.librechat.feature.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.feature.settings.viewmodel.RoleMemoriesAdminViewModel
import org.koin.compose.viewmodel.koinViewModel

/** Admin: which memory capabilities each role has (PUT /api/roles/:role/memories). */
@Composable
fun RoleMemoriesAdminScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RoleMemoriesAdminViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    RoleMemoriesAdminContent(
        uiState = uiState,
        onSelectRole = viewModel::selectRole,
        onRetryLoad = viewModel::retryLoad,
        onUseChange = viewModel::setUse,
        onCreateChange = viewModel::setCreate,
        onUpdateChange = viewModel::setUpdate,
        onReadChange = viewModel::setRead,
        onOptOutChange = viewModel::setOptOut,
        onSave = viewModel::save,
        onRequestBack = viewModel::onBackRequested,
        onDiscard = viewModel::discardChanges,
        onDismissDiscard = viewModel::dismissDiscardConfirmation,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}
