package com.garfiec.librechat.feature.settings.viewmodel

import com.garfiec.librechat.core.model.update.UpdateCheckState

data class UpdateCheckUiState(
    val checkState: UpdateCheckState = UpdateCheckState.Idle,
    val autoCheckEnabled: Boolean = false,
)
