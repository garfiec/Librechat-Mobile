package com.garfiec.librechat.feature.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.DialogPreviewHost
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens

@PreviewNarrowScreens
@Composable
private fun TtsDeviceGlassPreview() = TtsDeviceHost(UiStyle.LIQUID_GLASS)

@PreviewNarrowScreens
@Composable
private fun SttGlassPreview() = SttHost(UiStyle.LIQUID_GLASS)

@Composable
private fun TtsDeviceHost(style: UiStyle, height: Dp = 640.dp) = DialogPreviewHost(style, height = height) {
    TtsDetailDialog(
        selectedEngine = "",
        selectedVoice = "",
        speechRate = 1f,
        pitch = 1f,
        deviceVoiceName = "",
        cachingEnabled = false,
        ttsSource = "device",
        availableEngines = emptyList(),
        availableVoices = emptyList(),
        availableDeviceVoices = listOf(DeviceVoiceInfo("en-us-x-sfg-local", "en-US")),
        onConfirm = { _, _, _, _, _, _, _ -> },
        onDismiss = {},
    )
}

@Composable
private fun SttHost(style: UiStyle, height: Dp = 640.dp) = DialogPreviewHost(style, height = height) {
    SttDetailDialog(
        selectedEngine = "",
        selectedLanguage = "en-US",
        selectedOnDevice = false,
        selectedEndOfSpeech = true,
        serverSttEnabled = true,
        availableLanguages = listOf("en-US", "de-DE", "fr-FR"),
        onConfirm = { _, _, _, _ -> },
        onDismiss = {},
    )
}
