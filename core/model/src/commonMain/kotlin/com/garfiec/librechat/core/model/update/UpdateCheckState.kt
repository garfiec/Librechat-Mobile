package com.garfiec.librechat.core.model.update

import com.garfiec.librechat.core.model.AppRelease

sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState
    data object Checking : UpdateCheckState
    data object UpToDate : UpdateCheckState

    /** [releases] holds every stable release newer than the installed build, newest first. */
    data class Available(val releases: List<AppRelease>) : UpdateCheckState {
        val latest: AppRelease get() = releases.first()
    }

    data class Failed(val message: String?) : UpdateCheckState
}
