package com.garfiec.librechat.feature.settings.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.model.permissions.Permission
import com.garfiec.librechat.core.model.permissions.PermissionType
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import com.garfiec.librechat.core.model.request.UpdateRoleSkillsRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Immutable
data class RoleSkillsAdminUiState(
    /** Roles offered in the picker: the server's list, custom roles included, once it answers. */
    val roles: List<String> = FALLBACK_ROLE_NAMES,
    val selectedRole: String = "USER",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val loaded: Boolean = false,
    val error: RolePermissionsAdminError? = null,
    /** The last save landed; cleared by the next change, role switch or save. */
    val saved: Boolean = false,
    // The role's SKILLS permission booleans.
    val use: Boolean = false,
    val create: Boolean = false,
    val share: Boolean = false,
    val sharePublic: Boolean = false,
    /** The flags as the server last reported them, on load or save; null until a role has loaded. */
    val savedFlags: SkillsFlags? = null,
    val showDiscardConfirm: Boolean = false,
    /**
     * The role picked while the switches held unsaved changes. The discard prompt is up for it;
     * Discard switches to it, Cancel keeps the current role and its changes. Null when the prompt
     * is for Back.
     */
    val pendingRole: String? = null,
    val exitRequested: Boolean = false,
    /** Back was asked for while a save ran; the screen leaves once that save lands. */
    val exitAfterSave: Boolean = false,
) {
    /** Off until a switch differs from what the server last reported: there is nothing to send. */
    val canSave: Boolean get() = loaded && !isSaving && !isLoading && hasUnsavedChanges

    /** The selected role's load failed, which [RoleSkillsAdminViewModel.retryLoad] re-runs. */
    val loadFailed: Boolean get() = error == RolePermissionsAdminError.LOAD

    val hasUnsavedChanges: Boolean
        get() = loaded && savedFlags != null && SkillsFlags(use, create, share, sharePublic) != savedFlags
}

@Immutable
data class SkillsFlags(val use: Boolean, val create: Boolean, val share: Boolean, val sharePublic: Boolean)

/**
 * Admin-only editor for a role's SKILLS permission booleans
 * (GET /api/roles/:role → PUT /api/roles/:role/skills). The screen is reachable
 * only when the current user is ADMIN (gated in Settings); the server also
 * enforces via manageRoles. There is no per-role skill-id allowlist — these are
 * the role-level USE/CREATE/SHARE/SHARE_PUBLIC permission flags.
 */
class RoleSkillsAdminViewModel(
    private val roleRepository: RoleRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RoleSkillsAdminUiState())
    val uiState: StateFlow<RoleSkillsAdminUiState> = _uiState.asStateFlow()

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
            use = false,
            create = false,
            share = false,
            sharePublic = false,
            savedFlags = null,
        )
        loadRole(role)
    }

    /**
     * Re-runs the selected role's failed load (the screen's Retry): picking the same role again is a
     * no-op, so without this a failed load leaves no switches and no way back. Ignored unless the
     * last load failed, so a save failure's unsaved switches are never reloaded over.
     */
    fun retryLoad() {
        val state = _uiState.value
        if (!state.loadFailed || state.isLoading || state.isSaving) return
        loadRole(state.selectedRole)
    }

    fun setUse(value: Boolean) { _uiState.value = _uiState.value.copy(use = value, saved = false) }
    fun setCreate(value: Boolean) { _uiState.value = _uiState.value.copy(create = value, saved = false) }
    fun setShare(value: Boolean) { _uiState.value = _uiState.value.copy(share = value, saved = false) }
    fun setSharePublic(value: Boolean) { _uiState.value = _uiState.value.copy(sharePublic = value, saved = false) }

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
                is Result.Success -> _uiState.value = _uiState.value.copy(isLoading = false, loaded = true)
                    .withFlags(result.data)
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
            val request = UpdateRoleSkillsRequest(
                use = state.use,
                create = state.create,
                share = state.share,
                sharePublic = state.sharePublic,
            )
            val result = roleRepository.updateRoleSkills(state.selectedRole, request)
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
                    // Rebase from the server's returned role so the toggles reflect
                    // exactly what was persisted (the server may normalize).
                    _uiState.value = _uiState.value.let {
                        it.copy(isSaving = false, saved = true, exitRequested = it.exitAfterSave, exitAfterSave = false)
                    }.withFlags(result.data)
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

/** The switches and the clean state both become [perms]' SKILLS flags. */
private fun RoleSkillsAdminUiState.withFlags(perms: UserRolePermissions): RoleSkillsAdminUiState {
    val flags = SkillsFlags(
        use = perms.hasAccessStrict(PermissionType.SKILLS, Permission.USE),
        create = perms.hasAccessStrict(PermissionType.SKILLS, Permission.CREATE),
        share = perms.hasAccessStrict(PermissionType.SKILLS, Permission.SHARE),
        sharePublic = perms.hasAccessStrict(PermissionType.SKILLS, Permission.SHARE_PUBLIC),
    )
    return copy(
        use = flags.use,
        create = flags.create,
        share = flags.share,
        sharePublic = flags.sharePublic,
        savedFlags = flags,
    )
}
