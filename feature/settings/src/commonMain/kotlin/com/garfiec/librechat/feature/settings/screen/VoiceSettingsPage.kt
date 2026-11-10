package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.common.speech.sttLanguageOptions
import com.garfiec.librechat.core.ui.components.AdaptiveDivider
import com.garfiec.librechat.core.ui.components.AdaptiveGroupedPage
import com.garfiec.librechat.core.ui.components.AdaptiveOutlinedButton
import com.garfiec.librechat.core.ui.components.AdaptiveSectionHeader
import com.garfiec.librechat.core.ui.components.adaptiveSection
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.viewmodel.ChatSettingsViewModel
import com.garfiec.librechat.feature.settings.viewmodel.SpeechFailure
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Settings › Voice: dictation and reading replies aloud, with the speech-to-text and text-to-speech dialogs. */
@Composable
fun VoiceSettingsPage(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    viewModel: ChatSettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Voice test, preview and playback failures land here.
    val speechErrorText = uiState.error?.let { speechFailureText(it) }
    LaunchedEffect(uiState.error) {
        val text = speechErrorText ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = text)
        viewModel.dismissError()
    }
    val voiceTestText = stringResource(Res.string.tts_preview_text)

    AdaptiveGroupedPage(modifier = modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = contentPadding) {
            item(key = "speech_header") {
                AdaptiveSectionHeader(stringResource(Res.string.section_speech))
            }
            adaptiveSection {
                row(key = "stt_auto_send") {
                    SettingsSwitchRow(
                        title = stringResource(Res.string.auto_send_after_speech),
                        description = stringResource(Res.string.auto_send_after_speech_desc),
                        checked = uiState.sttAutoSend,
                        onCheckedChange = viewModel::setAutoSendAfterStt,
                    )
                }
                row(key = "auto_read") {
                    SettingsSwitchRow(
                        title = stringResource(Res.string.auto_read_responses),
                        description = stringResource(Res.string.auto_read_responses_desc),
                        checked = uiState.autoReadEnabled,
                        onCheckedChange = viewModel::setAutoReadEnabled,
                    )
                }
                row(key = "speech_voice") {
                    SpeechVoiceSection(
                        selectedVoice = uiState.selectedVoice,
                        availableVoices = uiState.availableVoices,
                        ttsSource = uiState.ttsSource,
                        onVoiceSelect = viewModel::selectVoice,
                        onTestVoice = { viewModel.testVoice(voiceTestText) },
                    )
                }
                row(key = "speech_detail_buttons") {
                    SpeechDetailButtons(
                        onSttDetailClick = viewModel::showSttDetailDialog,
                        onTtsDetailClick = viewModel::showTtsDetailDialog,
                    )
                }
            }
            item(key = "bottom_gap") { Spacer(modifier = Modifier.height(32.dp)) }
        }

        if (uiState.showSttDetailDialog) {
            SttDetailDialog(
                selectedEngine = uiState.sttEngine,
                selectedLanguage = uiState.sttLanguage,
                selectedOnDevice = uiState.sttOnDevice,
                selectedEndOfSpeech = uiState.sttEndOfSpeech,
                serverSttEnabled = uiState.serverSttEnabled,
                availableLanguages = sttLanguageOptions,
                onConfirm = viewModel::saveSttSettings,
                onDismiss = viewModel::dismissSttDetailDialog,
            )
        }

        if (uiState.showTtsDetailDialog) {
            TtsDetailDialog(
                selectedEngine = uiState.ttsEngine,
                selectedVoice = uiState.ttsVoice,
                speechRate = uiState.ttsSpeechRate,
                pitch = uiState.ttsPitch,
                deviceVoiceName = uiState.ttsDeviceVoiceName,
                cachingEnabled = uiState.ttsCaching,
                ttsSource = uiState.ttsSource,
                serverTtsEnabled = uiState.serverTtsEnabled,
                availableEngines = listOf(TTS_DEFAULT_OPTION, "ElevenLabs", "OpenAI"),
                availableVoices = uiState.availableVoices.map { it.name },
                availableDeviceVoices = uiState.availableDeviceVoices,
                isPreviewPlaying = uiState.isTtsPreviewPlaying,
                onPreviewDevice = viewModel::previewDeviceTts,
                onPreviewServer = viewModel::previewServerTts,
                onStopPreview = viewModel::stopTtsPreview,
                onConfirm = viewModel::saveTtsSettings,
                onDismiss = viewModel::dismissTtsDetailDialog,
            )
        }
    }
}

@Composable
internal fun SpeechDetailButtons(
    onSttDetailClick: () -> Unit,
    onTtsDetailClick: () -> Unit,
) {
    Column {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SpeechDetailButton(stringResource(Res.string.speech_to_text_settings), onSttDetailClick)
            SpeechDetailButton(stringResource(Res.string.text_to_speech_settings), onTtsDetailClick)
        }
        AdaptiveDivider(modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun SpeechDetailButton(label: String, onClick: () -> Unit) {
    AdaptiveOutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, modifier = Modifier.weight(1f))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun speechFailureText(failure: SpeechFailure): String = stringResource(
    when (failure) {
        SpeechFailure.VOICE_TEST -> Res.string.speech_error_voice_test
        SpeechFailure.VOICE_PREVIEW -> Res.string.speech_error_voice_preview
        SpeechFailure.PLAYBACK -> Res.string.speech_error_playback
    },
)
