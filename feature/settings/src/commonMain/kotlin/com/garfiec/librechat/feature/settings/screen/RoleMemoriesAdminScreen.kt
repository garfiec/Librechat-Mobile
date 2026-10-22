package com.garfiec.librechat.feature.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.viewmodel.RoleMemoriesAdminUiState
import com.garfiec.librechat.feature.settings.viewmodel.RoleMemoriesAdminViewModel
import org.jetbrains.compose.resources.stringResource
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

@Composable
internal fun RoleMemoriesAdminContent(
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
                showAdminWarning = true,
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
