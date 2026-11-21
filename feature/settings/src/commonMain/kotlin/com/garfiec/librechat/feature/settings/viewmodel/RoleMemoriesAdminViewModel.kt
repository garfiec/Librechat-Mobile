package com.garfiec.librechat.feature.settings.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.model.permissions.Permission
import com.garfiec.librechat.core.model.permissions.PermissionType
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import com.garfiec.librechat.core.model.request.UpdateRoleMemoriesRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Admin-only editor for a role's MEMORIES permission booleans
 * (GET /api/roles/:role → PUT /api/roles/:role/memories). The screen is reachable
 * only when the current user is ADMIN (gated in Settings); the server also
 * enforces via manageRoles. Same shape as [RoleSkillsAdminViewModel].
 */
class RoleMemoriesAdminViewModel(
    private val roleRepository: RoleRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RoleMemoriesAdminUiState())
    val uiState: StateFlow<RoleMemoriesAdminUiState> = _uiState.asStateFlow()

    /** Bumped by every role load; a load or save answering under an older one is stale. */
    private var loadGeneration = 0

    init {
        loadRole(_uiState.value.selectedRole)
        loadRoleNames()
    }

    private fun loadRoleNames() {
        viewModelScope.launch {
            roleRepository.pickerRoleNames()?.let { names -> _uiState.value = _uiState.value.copy(roles = names) }
        }
    }

    fun selectRole(role: String) {
        val state = _uiState.value
        if (role == state.selectedRole) return
        // Loading another role replaces the switches, so a draft would be lost unseen. A draft whose
        // save is already on its way is not lost: that save's answer is dropped as stale.
        if (state.hasUnsavedChanges && !state.isSaving) {
            _uiState.value = state.copy(pendingRole = role, showDiscardConfirm = true)
            return
        }
        switchToRole(role)
    }

    private fun switchToRole(role: String) {
        // The previous role's switches must not survive under the new role's name if its load fails.
        _uiState.value = _uiState.value.copy(
            selectedRole = role,
            loaded = false,
            saved = false,
            flags = NO_FLAGS,
            savedFlags = null,
        )
        loadRole(role)
    }

    /** Re-runs the selected role's failed load (the screen's Retry). */
    fun retryLoad() {
        val state = _uiState.value
        if (state.error != RolePermissionsAdminError.LOAD || state.isLoading || state.isSaving) return
        loadRole(state.selectedRole)
    }

    fun setUse(value: Boolean) = editFlags { it.copy(use = value) }
    fun setCreate(value: Boolean) = editFlags { it.copy(create = value) }
    fun setUpdate(value: Boolean) = editFlags { it.copy(update = value) }
    fun setRead(value: Boolean) = editFlags { it.copy(read = value) }
    fun setOptOut(value: Boolean) = editFlags { it.copy(optOut = value) }

    private fun editFlags(change: (MemoriesFlags) -> MemoriesFlags) {
        _uiState.value = _uiState.value.let { it.copy(flags = change(it.flags), saved = false) }
    }

    fun onBackRequested() {
        val state = _uiState.value
        _uiState.value = when {
            // The change is already on its way. Offering to discard it would be untrue, and leaving
            // would cancel the PUT with the screen's scope, so the server may or may not have it.
            // Leave once it answers; a failed save stays, so its error is seen.
            state.isSaving -> state.copy(exitAfterSave = true)
            state.hasUnsavedChanges -> state.copy(showDiscardConfirm = true, pendingRole = null)
            else -> state.copy(exitRequested = true)
        }
    }

    /** Discard on the prompt: switches to the picked role when the prompt was for one, else leaves. */
    fun discardChanges() {
        val pending = _uiState.value.pendingRole
        if (pending != null) {
            _uiState.value = _uiState.value.copy(showDiscardConfirm = false, pendingRole = null)
            switchToRole(pending)
        } else {
            _uiState.value = _uiState.value.copy(showDiscardConfirm = false, exitRequested = true)
        }
    }

    fun dismissDiscardConfirmation() {
        _uiState.value = _uiState.value.copy(showDiscardConfirm = false, pendingRole = null)
    }

    private fun loadRole(role: String) {
        val generation = ++loadGeneration
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = roleRepository.getRole(role)
            // A load started since (another role picked, or this one picked again after a switch)
            // owns the screen; this answer is stale even when it names the same role.
            if (generation != loadGeneration) return@launch
            when (result) {
                is Result.Success -> {
                    val flags = result.data.memoriesFlags()
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        loaded = true,
                        flags = flags,
                        savedFlags = flags,
                    )
                }
                is Result.Error ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = RolePermissionsAdminError.LOAD)
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        val generation = loadGeneration
        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true, error = null, saved = false)
            val request = UpdateRoleMemoriesRequest(
                use = state.flags.use,
                create = state.flags.create,
                update = state.flags.update,
                read = state.flags.read,
                optOut = state.flags.optOut,
            )
            val result = roleRepository.updateRoleMemories(state.selectedRole, request)
            if (generation != loadGeneration) {
                // The admin switched roles mid-save: the switches now belong to the other role's
                // load, so only the save flag is cleared. (The repository has already refreshed the
                // admin's own cached role if that is the one saved.)
                _uiState.value = _uiState.value.let {
                    it.copy(
                        isSaving = false,
                        exitRequested = it.exitAfterSave && result is Result.Success,
                        exitAfterSave = false,
                    )
                }
                return@launch
            }
            when (result) {
                is Result.Success -> {
                    // Rebase from the server's returned role so the switches reflect
                    // exactly what was persisted (the server may normalize).
                    val flags = result.data.memoriesFlags()
                    _uiState.value = _uiState.value.let {
                        it.copy(
                            isSaving = false,
                            saved = true,
                            flags = flags,
                            savedFlags = flags,
                            exitRequested = it.exitAfterSave,
                            exitAfterSave = false,
                        )
                    }
                }
                is Result.Error -> _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = RolePermissionsAdminError.SAVE,
                    exitAfterSave = false,
                )
                is Result.Loading -> { /* no-op */ }
            }
        }
    }
}

private fun UserRolePermissions.memoriesFlags() = MemoriesFlags(
    use = hasAccessStrict(PermissionType.MEMORIES, Permission.USE),
    create = hasAccessStrict(PermissionType.MEMORIES, Permission.CREATE),
    update = hasAccessStrict(PermissionType.MEMORIES, Permission.UPDATE),
    read = hasAccessStrict(PermissionType.MEMORIES, Permission.READ),
    optOut = hasAccessStrict(PermissionType.MEMORIES, Permission.OPT_OUT),
)
