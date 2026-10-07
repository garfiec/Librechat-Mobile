package com.garfiec.librechat.feature.settings.viewmodel.delegate

import com.garfiec.librechat.feature.settings.viewmodel.ChatSettingsUiState
import com.garfiec.librechat.feature.settings.viewmodel.SettingsStateHandle

fun interface SpeechSettingsFactory {
    fun create(stateHandle: SettingsStateHandle<ChatSettingsUiState>): SpeechSettingsContract
}
