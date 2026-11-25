package com.garfiec.librechat.feature.settings.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.feature.settings.model.PresetManagerDisplayData

@Immutable
data class PresetManagerUiState(
    val presets: List<PresetManagerDisplayData> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
)
