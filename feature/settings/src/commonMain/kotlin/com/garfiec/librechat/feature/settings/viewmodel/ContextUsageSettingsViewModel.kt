package com.garfiec.librechat.feature.settings.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.data.datastore.ContextBarPlacement
import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.model.usage.ContextDetailPreset
import com.garfiec.librechat.core.model.usage.ContextDetailSections
import com.garfiec.librechat.core.model.usage.DEFAULT_COMPACT_NUDGE_THRESHOLD
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class ContextUsageSettingsUiState(
    val placement: ContextBarPlacement = ContextBarPlacement.OPTIONS_SHEET,
    val preset: ContextDetailPreset = ContextDetailPreset.DEFAULT,
    /** The user's own mix ("Custom") is in force instead of [preset]. */
    val advanced: Boolean = false,
    /** What the breakdown shows right now; drives the preview and the Advanced switches. */
    val sections: ContextDetailSections = ContextDetailPreset.DEFAULT.sections,
    /** 0 is Off. */
    val compactNudgeThreshold: Int = DEFAULT_COMPACT_NUDGE_THRESHOLD,
    /** False when the current server says it doesn't report cost; the Cost control then explains why it shows nothing. */
    val serverReportsCost: Boolean = true,
)

/**
 * Backs the Context usage page: where the gauge shows, how much of the breakdown it shows, and
 * when compacting is suggested. Preferences are device-wide; [ContextUsageSettingsUiState.serverReportsCost]
 * reflects only the server currently in use.
 */
class ContextUsageSettingsViewModel(
    private val settingsDataStore: SettingsDataStore,
    configRepository: ConfigRepository,
) : ViewModel() {

    private val detail = combine(
        settingsDataStore.contextDetailPreset,
        settingsDataStore.contextDetailAdvanced,
        settingsDataStore.effectiveContextSections,
    ) { preset, advanced, sections -> Triple(preset, advanced, sections) }

    val uiState: StateFlow<ContextUsageSettingsUiState> = combine(
        settingsDataStore.contextBarPlacement,
        detail,
        settingsDataStore.compactNudgeThreshold,
        configRepository.startupConfig,
    ) { placement, (preset, advanced, sections), threshold, config ->
        ContextUsageSettingsUiState(
            placement = placement,
            preset = preset,
            advanced = advanced,
            sections = sections,
            compactNudgeThreshold = threshold,
            // Unknown until the config loads; don't claim the server lacks cost before then.
            serverReportsCost = config == null || config.interfaceConfig?.contextCost == true,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ContextUsageSettingsUiState())

    fun setPlacement(placement: ContextBarPlacement) {
        viewModelScope.launch { settingsDataStore.setContextBarPlacement(placement) }
    }

    fun setPreset(preset: ContextDetailPreset) {
        viewModelScope.launch { settingsDataStore.setContextDetailPreset(preset) }
    }

    fun setAdvanced(advanced: Boolean) {
        viewModelScope.launch { settingsDataStore.setContextDetailAdvanced(advanced) }
    }

    /** Changes one part of the custom mix, starting from what is shown now. */
    fun updateSections(transform: (ContextDetailSections) -> ContextDetailSections) {
        val next = transform(uiState.value.sections)
        viewModelScope.launch { settingsDataStore.setContextDetailCustom(next) }
    }

    fun setCompactNudgeThreshold(threshold: Int) {
        viewModelScope.launch { settingsDataStore.setCompactNudgeThreshold(threshold) }
    }
}
