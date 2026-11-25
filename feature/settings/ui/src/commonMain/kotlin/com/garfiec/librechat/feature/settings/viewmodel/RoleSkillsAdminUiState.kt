package com.garfiec.librechat.feature.settings.viewmodel

import androidx.compose.runtime.Immutable

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
