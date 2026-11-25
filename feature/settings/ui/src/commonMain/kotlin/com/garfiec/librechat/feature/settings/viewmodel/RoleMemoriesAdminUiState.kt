package com.garfiec.librechat.feature.settings.viewmodel

import androidx.compose.runtime.Immutable

/** A role's MEMORIES permission booleans (upstream `memoryPermissionsSchema`). */
@Immutable
data class MemoriesFlags(
    val use: Boolean,
    val create: Boolean,
    val update: Boolean,
    val read: Boolean,
    val optOut: Boolean,
)

@Immutable
data class RoleMemoriesAdminUiState(
    /** Roles offered in the picker: the server's list, custom roles included, once it answers. */
    val roles: List<String> = FALLBACK_ROLE_NAMES,
    val selectedRole: String = "USER",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val loaded: Boolean = false,
    val error: RolePermissionsAdminError? = null,
    /** The last save landed; cleared by the next change, role switch or save. */
    val saved: Boolean = false,
    val flags: MemoriesFlags = NO_FLAGS,
    /** The flags as the server last reported them, on load or save; null until a role has loaded. */
    val savedFlags: MemoriesFlags? = null,
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

    val hasUnsavedChanges: Boolean
        get() = loaded && savedFlags != null && flags != savedFlags
}

val NO_FLAGS = MemoriesFlags(use = false, create = false, update = false, read = false, optOut = false)
