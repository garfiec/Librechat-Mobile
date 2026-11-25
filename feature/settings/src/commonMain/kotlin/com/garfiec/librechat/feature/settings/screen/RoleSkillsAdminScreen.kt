package com.garfiec.librechat.feature.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.feature.settings.viewmodel.RoleSkillsAdminViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun RoleSkillsAdminScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RoleSkillsAdminViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    RoleSkillsAdminContent(
        uiState = uiState,
        onSelectRole = viewModel::selectRole,
        onRetryLoad = viewModel::retryLoad,
        onUseChange = viewModel::setUse,
        onCreateChange = viewModel::setCreate,
        onShareChange = viewModel::setShare,
        onSharePublicChange = viewModel::setSharePublic,
        onSave = viewModel::save,
        onRequestBack = viewModel::onBackRequested,
        onDiscard = viewModel::discardChanges,
        onDismissDiscard = viewModel::dismissDiscardConfirmation,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}
