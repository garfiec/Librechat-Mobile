package com.garfiec.librechat.feature.settings.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.AppInfo
import com.garfiec.librechat.core.data.update.AppInstallSource
import com.garfiec.librechat.core.data.update.AppUpdateRepository
import com.garfiec.librechat.core.data.update.InstallChannel
import com.garfiec.librechat.core.data.update.UpdateCheckState
import com.garfiec.librechat.core.model.AppRelease
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface WhatsNewUiState {
    data object Loading : WhatsNewUiState
    data object UpToDate : WhatsNewUiState
    data object Error : WhatsNewUiState

    /** [releases] newest first: every stable release between the installed build and the latest. */
    data class Content(val releases: List<AppRelease>) : WhatsNewUiState
}

class WhatsNewViewModel(
    private val repository: AppUpdateRepository,
    private val installSource: AppInstallSource,
    appInfo: AppInfo,
) : ViewModel() {

    val installedVersion: String = appInfo.versionName
    val installChannel: InstallChannel = installSource.channel

    val uiState: StateFlow<WhatsNewUiState> = repository.checkState
        .map { it.toUiState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), repository.checkState.value.toUiState())

    init {
        // Reached from the chat banner after a restart, the persisted tag is all there is — the
        // notes themselves were never fetched in this process.
        if (repository.checkState.value !is UpdateCheckState.Available) retry()
        viewModelScope.launch {
            repository.checkState.filterIsInstance<UpdateCheckState.Available>().collect {
                repository.markNotified(it.latest.tag)
            }
        }
    }

    fun retry() {
        viewModelScope.launch { repository.check() }
    }

    /** False when the installer can't be launched; the caller falls back to the download. */
    fun openInstaller(): Boolean = installSource.openInstaller()

    private fun UpdateCheckState.toUiState(): WhatsNewUiState = when (this) {
        UpdateCheckState.Idle, UpdateCheckState.Checking -> WhatsNewUiState.Loading
        UpdateCheckState.UpToDate -> WhatsNewUiState.UpToDate
        is UpdateCheckState.Failed -> WhatsNewUiState.Error
        is UpdateCheckState.Available -> WhatsNewUiState.Content(releases)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
