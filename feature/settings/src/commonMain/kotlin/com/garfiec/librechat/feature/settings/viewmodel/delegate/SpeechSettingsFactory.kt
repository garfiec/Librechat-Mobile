package com.garfiec.librechat.feature.settings.viewmodel.delegate

import com.garfiec.librechat.feature.settings.viewmodel.SettingsStateHandle
import com.garfiec.librechat.feature.settings.viewmodel.SettingsUiState

fun interface SpeechSettingsFactory {
    fun create(stateHandle: SettingsStateHandle<SettingsUiState>): SpeechSettingsContract
}
