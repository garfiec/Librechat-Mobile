package com.garfiec.librechat.feature.settings.viewmodel

import com.garfiec.librechat.core.model.AppRelease

sealed interface WhatsNewUiState {
    data object Loading : WhatsNewUiState
    data object UpToDate : WhatsNewUiState
    data object Error : WhatsNewUiState

    /** [releases] newest first: every stable release between the installed build and the latest. */
    data class Content(val releases: List<AppRelease>) : WhatsNewUiState
}
