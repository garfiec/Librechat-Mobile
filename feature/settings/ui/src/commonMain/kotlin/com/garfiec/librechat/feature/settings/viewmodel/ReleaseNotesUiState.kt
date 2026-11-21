package com.garfiec.librechat.feature.settings.viewmodel

import com.garfiec.librechat.core.model.AppRelease

sealed interface InstalledNotes {
    data object Loading : InstalledNotes
    data object NotFound : InstalledNotes
    data object Error : InstalledNotes
    data class Content(val release: AppRelease) : InstalledNotes
}

enum class OlderStatus { LOADING, MORE, END, ERROR }

data class ReleaseNotesUiState(
    val installed: InstalledNotes = InstalledNotes.Loading,
    /** Releases before the installed build, newest first. */
    val older: List<AppRelease> = emptyList(),
    val olderStatus: OlderStatus = OlderStatus.LOADING,
)
