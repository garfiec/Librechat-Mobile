package com.garfiec.librechat.feature.conversations.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.config.ChatProjectLimits

@Immutable
data class ProjectInstructionsUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    /** The text the server holds, as last loaded. Dirty is measured against it. */
    val saved: String = "",
    val draft: String = "",
    /** The `contextRevision` the next save is based on. */
    val revision: Int? = null,
    val maxLength: Int = ChatProjectLimits.DEFAULT_MAX_INSTRUCTIONS_LENGTH,
    val isSaving: Boolean = false,
    /** After a conflict: the other device's text, reloaded, for "View theirs". */
    val theirs: String? = null,
    val showTheirs: Boolean = false,
    val discardPrompt: Boolean = false,
    /** The screen should close: saved, discarded, left clean, or the project is gone. */
    val exitRequested: Boolean = false,
) {
    val isDirty: Boolean get() = !isLoading && !loadFailed && draft.trim() != saved.trim()

    /**
     * A save with nothing changed is refused even when it would succeed: on a stale revision the
     * server answers it with a conflict, for an edit that changes nothing.
     */
    val canSave: Boolean get() = isDirty && !isSaving
}
