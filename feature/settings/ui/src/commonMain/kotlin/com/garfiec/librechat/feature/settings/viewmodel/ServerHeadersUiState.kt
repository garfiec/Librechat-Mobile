package com.garfiec.librechat.feature.settings.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.server.HeaderRejection
import com.garfiec.librechat.core.model.server.HeaderWriteFailure
import com.garfiec.librechat.core.ui.components.CustomHeaderRow

/** A rejected header row: the index plus the machine-readable reason, mapped to text in the UI. */
@Immutable
data class ServerHeaderError(val index: Int, val reason: HeaderRejection)

@Immutable
data class ServerHeadersUiState(
    val serverUrl: String = "",
    val headers: List<CustomHeaderRow> = emptyList(),
    val error: ServerHeaderError? = null,
    val isSaving: Boolean = false,
    val isDirty: Boolean = false,
    /** One-shot: set on a successful save, cleared by [consumeSaved] once the confirmation is shown. */
    val saved: Boolean = false,
    /**
     * Why the last save didn't land, rendered *inside* the editor and cleared by the next edit or
     * save. Not a one-shot event: a refused save leaves the dialog open and unchanged, so a message
     * that plays once — behind the dialog's own scrim, at that — reads as a dead Save button.
     */
    val saveFailure: HeaderWriteFailure? = null,
    /**
     * The stored headers could not be read, so [headers] is whatever was already on screen rather
     * than what is persisted. Rendering an unreadable store as an empty editor would tell the user
     * their credential is gone — and a save from that state would then really erase it.
     */
    val loadFailed: Boolean = false,
)
