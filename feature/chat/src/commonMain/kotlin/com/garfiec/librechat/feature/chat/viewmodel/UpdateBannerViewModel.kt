package com.garfiec.librechat.feature.chat.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.data.update.AppUpdateRepository
import com.garfiec.librechat.core.data.update.PendingUpdate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The new-release banner above an empty chat's composer. */
class UpdateBannerViewModel(
    private val repository: AppUpdateRepository,
) : ViewModel() {

    val pendingUpdate: StateFlow<PendingUpdate?> = repository.pendingUpdate
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** Both open and dismiss count as "told": the banner never repeats for that release. */
    fun acknowledge(update: PendingUpdate) {
        viewModelScope.launch { repository.markNotified(update.tag) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
