package com.garfiec.librechat.feature.settings.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.data.prefetch.AttachmentWarmer
import com.garfiec.librechat.core.data.prefetch.PrefetchDepth
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class PrefetchSettingsUiState(
    val enabled: Boolean = false,
    val attachmentsEnabled: Boolean = false,
    val onMeteredEnabled: Boolean = false,
    val depth: Int = PrefetchDepth.DEFAULT,
    /** Whether this platform has an image cache worth warming; false hides the toggle. */
    val attachmentsSupported: Boolean = false,
)

/** Backs the Background prefetch page: whether, what and how much to prefetch. Reads and writes [SettingsDataStore] only. */
class PrefetchSettingsViewModel(
    private val settingsDataStore: SettingsDataStore,
    attachmentWarmer: AttachmentWarmer,
) : ViewModel() {

    // Constant per platform.
    private val attachmentsSupported = attachmentWarmer.isSupported

    val uiState: StateFlow<PrefetchSettingsUiState> = combine(
        settingsDataStore.prefetchEnabled,
        settingsDataStore.prefetchAttachmentsEnabled,
        settingsDataStore.prefetchOnMeteredEnabled,
        settingsDataStore.prefetchDepth,
    ) { enabled, attachments, onMetered, depth ->
        PrefetchSettingsUiState(
            enabled = enabled,
            attachmentsEnabled = attachments,
            onMeteredEnabled = onMetered,
            depth = depth,
            attachmentsSupported = attachmentsSupported,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PrefetchSettingsUiState(attachmentsSupported = attachmentsSupported))

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setPrefetchEnabled(enabled) }
    }

    fun setAttachmentsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setPrefetchAttachmentsEnabled(enabled) }
    }

    fun setOnMeteredEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsDataStore.setPrefetchOnMeteredEnabled(enabled) }
    }

    fun setDepth(depth: Int) {
        viewModelScope.launch { settingsDataStore.setPrefetchDepth(depth) }
    }
}
