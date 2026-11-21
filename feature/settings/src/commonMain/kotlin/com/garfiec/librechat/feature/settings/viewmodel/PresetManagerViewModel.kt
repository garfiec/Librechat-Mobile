package com.garfiec.librechat.feature.settings.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.PresetRepository
import com.garfiec.librechat.core.model.Preset
import com.garfiec.librechat.feature.settings.model.PresetManagerDisplayData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PresetManagerViewModel(
    private val presetRepository: PresetRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PresetManagerUiState())
    val uiState: StateFlow<PresetManagerUiState> = _uiState.asStateFlow()

    init {
        loadPresets()
    }

    private fun loadPresets() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            when (val result = presetRepository.getAll()) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        presets = result.data.map { it.toDisplayData() },
                        isLoading = false,
                    )
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.message ?: "Failed to load presets",
                    )
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            when (val result = presetRepository.getAll()) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        presets = result.data.map { it.toDisplayData() },
                        isRefreshing = false,
                    )
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isRefreshing = false,
                        error = result.message ?: "Failed to refresh presets",
                    )
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun deletePreset(presetId: String) {
        viewModelScope.launch {
            when (val result = presetRepository.delete(presetId)) {
                is Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        presets = _uiState.value.presets.filter { it.presetId != presetId },
                    )
                }
                is Result.Error -> {
                    _uiState.value = _uiState.value.copy(
                        error = result.message ?: "Failed to delete preset",
                    )
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

private fun Preset.toDisplayData() = PresetManagerDisplayData(
    presetId = presetId,
    title = title ?: "Untitled Preset",
    endpoint = endpoint,
    model = model,
)
