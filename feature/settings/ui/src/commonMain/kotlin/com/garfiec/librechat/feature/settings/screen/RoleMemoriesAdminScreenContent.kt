package com.garfiec.librechat.feature.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.viewmodel.RoleMemoriesAdminUiState
import org.jetbrains.compose.resources.stringResource

@Composable
fun RoleMemoriesAdminContent(
    uiState: RoleMemoriesAdminUiState,
    onSelectRole: (String) -> Unit,
    onRetryLoad: () -> Unit,
    onUseChange: (Boolean) -> Unit,
    onCreateChange: (Boolean) -> Unit,
    onUpdateChange: (Boolean) -> Unit,
    onReadChange: (Boolean) -> Unit,
    onOptOutChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onRequestBack: () -> Unit,
    onDiscard: () -> Unit,
    onDismissDiscard: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val flags = uiState.flags
    RolePermissionsEditor(
        title = stringResource(Res.string.role_memories_title),
        description = stringResource(Res.string.role_memories_description),
        section = stringResource(Res.string.title_memories),
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
                stringResource(Res.string.role_memories_use),
                flags.use,
                onUseChange,
                revokesAdminUse = revokesAdminUse(uiState.selectedRole, uiState.savedFlags?.use, flags.use),
            ),
            RolePermissionToggle(stringResource(Res.string.role_memories_create), flags.create, onCreateChange),
            RolePermissionToggle(stringResource(Res.string.role_memories_update), flags.update, onUpdateChange),
            RolePermissionToggle(stringResource(Res.string.role_memories_read), flags.read, onReadChange),
            RolePermissionToggle(stringResource(Res.string.role_memories_opt_out), flags.optOut, onOptOutChange),
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
