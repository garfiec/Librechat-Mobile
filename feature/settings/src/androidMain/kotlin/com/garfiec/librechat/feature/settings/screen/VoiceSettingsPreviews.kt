package com.garfiec.librechat.feature.settings.screen

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.model.speech.TtsVoice
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.settings.viewmodel.ChatSettingsUiState
import com.garfiec.librechat.feature.settings.viewmodel.DeviceVoiceInfo

// The device source: voice, speed and pitch.
@PreviewNarrowScreens
@Composable
private fun VoiceDevicePreview() = VoicePreviewSurface(UiStyle.MATERIAL) { VoiceHost(DeviceState) }

@PreviewNarrowScreens
@Composable
private fun VoiceDeviceGlassPreview() = VoicePreviewSurface(UiStyle.LIQUID_GLASS) { VoiceHost(DeviceState) }

// The server source, mid-preview.
@PreviewNarrowScreens
@Composable
private fun VoiceServerPreview() = VoicePreviewSurface(UiStyle.MATERIAL) { VoiceHost(ServerState) }

@PreviewNarrowScreens
@Composable
private fun VoiceServerGlassPreview() = VoicePreviewSurface(UiStyle.LIQUID_GLASS) { VoiceHost(ServerState) }

private val DeviceState = ChatSettingsUiState(
    sttAutoSend = true,
    sttLanguage = "German",
    serverTtsEnabled = true,
    ttsSpeechRate = 1.25f,
    availableDeviceVoices = listOf(DeviceVoiceInfo("en-us-x-sfg-local", "en-us-x-sfg-local", "English (United States)")),
    ttsDeviceVoiceName = "en-us-x-sfg-local",
)

private val ServerState = ChatSettingsUiState(
    autoReadEnabled = true,
    serverTtsEnabled = true,
    ttsSource = SettingsDataStore.TTS_SOURCE_SERVER,
    availableVoices = listOf(TtsVoice(id = "alloy", name = "alloy", provider = "openai")),
    selectedVoice = TtsVoice(id = "alloy", name = "alloy", provider = "openai"),
    isTtsPreviewPlaying = true,
)

@Composable
private fun VoicePreviewSurface(style: UiStyle, content: @Composable () -> Unit) {
    LibreChatTheme(darkTheme = true, uiStyle = style) {
        Surface(color = MaterialTheme.colorScheme.surface) { content() }
    }
}

@Composable
private fun VoiceHost(state: ChatSettingsUiState) {
    VoiceSettingsContent(
        state = state,
        actions = VoiceSettingsActions(
            onAutoSend = {},
            onStopWhenPause = {},
            onSpeechLanguage = {},
            onReadAloud = {},
            onSource = {},
            onServerVoice = {},
            onDeviceVoice = {},
            onSpeed = {},
            onPitch = {},
            onPreviewToggle = {},
            onOpenAdvanced = {},
        ),
    )
}
