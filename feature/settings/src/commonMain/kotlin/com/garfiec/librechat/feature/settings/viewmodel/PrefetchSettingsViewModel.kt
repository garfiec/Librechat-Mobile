package com.garfiec.librechat.feature.settings.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.data.datastore.AppPreferences
import com.garfiec.librechat.core.data.datastore.PreferenceStore
import com.garfiec.librechat.core.data.prefetch.AttachmentWarmer
import com.garfiec.librechat.core.data.prefetch.PrefetchDepth
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class PrefetchSettingsUiState(
    val enabled: Boolean,
    val attachmentsEnabled: Boolean,
    val onMeteredEnabled: Boolean,
    val depth: Int,
    /** Whether this platform has an image cache worth warming; false hides the toggle. */
    val attachmentsSupported: Boolean = false,
)

/** Backs the Background prefetch page: whether, what and how much to prefetch. Reads and writes [PreferenceStore] only. */
class PrefetchSettingsViewModel(
    private val preferenceStore: PreferenceStore,
    attachmentWarmer: AttachmentWarmer,
) : ViewModel() {

    // Constant per platform.
    private val attachmentsSupported = attachmentWarmer.isSupported

    val uiState: StateFlow<PrefetchSettingsUiState> = combine(
        preferenceStore.observe(AppPreferences.PrefetchEnabled),
        preferenceStore.observe(AppPreferences.PrefetchAttachments),
        preferenceStore.observe(AppPreferences.PrefetchOnMetered),
        preferenceStore.observe(AppPreferences.PrefetchDepthSetting),
    ) { enabled, attachments, onMetered, depth ->
        PrefetchSettingsUiState(
            enabled = enabled,
            attachmentsEnabled = attachments,
            onMeteredEnabled = onMetered,
            depth = depth,
            attachmentsSupported = attachmentsSupported,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        // Seeded from the defaults, so the toggles don't paint off for a frame before the store answers.
        PrefetchSettingsUiState(
            enabled = preferenceStore.initial(AppPreferences.PrefetchEnabled),
            attachmentsEnabled = preferenceStore.initial(AppPreferences.PrefetchAttachments),
            onMeteredEnabled = preferenceStore.initial(AppPreferences.PrefetchOnMetered),
            depth = preferenceStore.initial(AppPreferences.PrefetchDepthSetting),
            attachmentsSupported = attachmentsSupported,
        ),
    )

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { preferenceStore.set(AppPreferences.PrefetchEnabled, enabled) }
    }

    fun setAttachmentsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferenceStore.set(AppPreferences.PrefetchAttachments, enabled) }
    }

    fun setOnMeteredEnabled(enabled: Boolean) {
        viewModelScope.launch { preferenceStore.set(AppPreferences.PrefetchOnMetered, enabled) }
    }

    fun setDepth(depth: Int) {
        viewModelScope.launch { preferenceStore.set(AppPreferences.PrefetchDepthSetting, depth.coerceIn(PrefetchDepth.RANGE)) }
    }
}
