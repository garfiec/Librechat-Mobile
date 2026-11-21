package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.feature.settings.navigation.AdvancedSection
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.viewmodel.ChatSettingsViewModel
import com.garfiec.librechat.feature.settings.viewmodel.SpeechFailure
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Settings › Voice: dictation (Listening) and reading replies aloud (Speaking). Engines live on Advanced. */
@Composable
fun VoiceSettingsPage(
    onOpenAdvancedSettings: (AdvancedSection) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    viewModel: ChatSettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // A config or voices fetch that failed at launch must not hide the server voice for the session.
    LaunchedEffect(Unit) {
        viewModel.retrySpeechConfig()
        viewModel.loadDeviceVoices()
    }
    val speechErrorText = uiState.error?.let { speechFailureText(it) }
    LaunchedEffect(uiState.error) {
        val text = speechErrorText ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = text)
        viewModel.dismissError()
    }
    val previewText = stringResource(Res.string.tts_preview_text)

    VoiceSettingsContent(
        state = uiState,
        actions = VoiceSettingsActions(
            onAutoSend = viewModel::setAutoSendAfterStt,
            onStopWhenPause = viewModel::setSttEndOfSpeech,
            onSpeechLanguage = viewModel::setSttLanguage,
            onReadAloud = viewModel::setAutoReadEnabled,
            onSource = viewModel::setTtsSource,
            onServerVoice = viewModel::selectServerVoice,
            onDeviceVoice = viewModel::selectDeviceVoice,
            onSpeed = viewModel::setTtsSpeechRate,
            onPitch = viewModel::setTtsPitch,
            onPreviewToggle = {
                if (uiState.isTtsPreviewPlaying) viewModel.stopTtsPreview() else viewModel.previewVoice(previewText)
            },
            onOpenAdvanced = { onOpenAdvancedSettings(AdvancedSection.VOICE) },
        ),
        modifier = modifier,
        contentPadding = contentPadding,
    )
}

@Composable
private fun speechFailureText(failure: SpeechFailure): String = stringResource(
    when (failure) {
        SpeechFailure.VOICE_PREVIEW -> Res.string.speech_error_voice_preview
        SpeechFailure.PLAYBACK -> Res.string.speech_error_playback
    },
)
