package com.garfiec.librechat.feature.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.viewmodel.RoleSkillsAdminUiState
import org.jetbrains.compose.resources.stringResource

@Composable
fun RoleSkillsAdminContent(
    uiState: RoleSkillsAdminUiState,
    onSelectRole: (String) -> Unit,
    onRetryLoad: () -> Unit,
    onUseChange: (Boolean) -> Unit,
    onCreateChange: (Boolean) -> Unit,
    onShareChange: (Boolean) -> Unit,
    onSharePublicChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onRequestBack: () -> Unit,
    onDiscard: () -> Unit,
    onDismissDiscard: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RolePermissionsEditor(
        title = stringResource(Res.string.role_skills_title),
        description = stringResource(Res.string.role_skills_description),
        section = stringResource(Res.string.role_skills_section),
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
                onUseChange,
                revokesAdminUse = revokesAdminUse(uiState.selectedRole, uiState.savedFlags?.use, uiState.use),
            ),
            RolePermissionToggle(stringResource(Res.string.role_skills_create), uiState.create, onCreateChange),
            RolePermissionToggle(stringResource(Res.string.role_skills_share), uiState.share, onShareChange),
            RolePermissionToggle(
                stringResource(Res.string.role_skills_share_public),
                uiState.sharePublic,
                onSharePublicChange,
            ),
        ),
        onSelectRole = onSelectRole,
        onRetryLoad = onRetryLoad,
        onSave = onSave,
        onRequestBack = onRequestBack,
        onDiscard = onDiscard,
        onDismissDiscard = onDismissDiscard,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    )
}
