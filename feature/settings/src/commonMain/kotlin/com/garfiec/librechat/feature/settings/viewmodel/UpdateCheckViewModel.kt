package com.garfiec.librechat.feature.settings.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.data.update.AppUpdateRepository
import com.garfiec.librechat.core.data.update.UpdateCheckState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UpdateCheckUiState(
    val checkState: UpdateCheckState = UpdateCheckState.Idle,
    val autoCheckEnabled: Boolean = false,
)

/** Settings → About's update rows. */
class UpdateCheckViewModel(
    private val repository: AppUpdateRepository,
) : ViewModel() {

    val isSupported: Boolean = repository.isSupported

    val uiState: StateFlow<UpdateCheckUiState> = combine(
        repository.checkState,
        repository.autoCheckEnabled,
    ) { checkState, autoCheck -> UpdateCheckUiState(checkState, autoCheck) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UpdateCheckUiState())

    /**
     * Only the result of the user's own tap counts as seen. A daily result already in [uiState] is
     * not: this ViewModel is created whenever General settings opens, About scrolled into view or
     * not, and marking that would silently take the chat banner away.
     */
    fun check() {
        if (uiState.value.checkState == UpdateCheckState.Checking) return
        viewModelScope.launch {
            val result = repository.check()
            if (result is UpdateCheckState.Available) repository.markNotified(result.latest.tag)
        }
    }

    fun setAutoCheckEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setAutoCheckEnabled(enabled) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
