package com.garfiec.librechat.feature.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.viewmodel.RoleSkillsAdminViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun RoleSkillsAdminScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RoleSkillsAdminViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    RolePermissionsEditor(
        title = stringResource(Res.string.role_skills_title),
        description = stringResource(Res.string.role_skills_description),
        state = RolePermissionsEditorState(
            roles = uiState.roles,
            selectedRole = uiState.selectedRole,
            isLoading = uiState.isLoading,
            loaded = uiState.loaded,
            isSaving = uiState.isSaving,
            canSave = uiState.canSave,
            hasUnsavedChanges = uiState.hasUnsavedChanges,
            showDiscardConfirm = uiState.showDiscardConfirm,
            exitRequested = uiState.exitRequested,
            error = uiState.error,
            saved = uiState.saved,
        ),
        toggles = listOf(
            RolePermissionToggle(
                stringResource(Res.string.role_skills_use),
                uiState.use,
                viewModel::setUse,
                showAdminWarning = true,
            ),
            RolePermissionToggle(stringResource(Res.string.role_skills_create), uiState.create, viewModel::setCreate),
            RolePermissionToggle(stringResource(Res.string.role_skills_share), uiState.share, viewModel::setShare),
            RolePermissionToggle(
                stringResource(Res.string.role_skills_share_public),
                uiState.sharePublic,
                viewModel::setSharePublic,
            ),
        ),
        onSelectRole = viewModel::selectRole,
        onRetryLoad = viewModel::retryLoad,
        onSave = viewModel::save,
        onRequestBack = viewModel::onBackRequested,
        onDiscard = viewModel::discardChanges,
        onDismissDiscard = viewModel::dismissDiscardConfirmation,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}
