package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.garfiec.librechat.core.common.extensions.formatLocalizedNumber
import com.garfiec.librechat.core.common.speech.sttEngineSelectsRecognizer
import com.garfiec.librechat.core.common.speech.sttLanguageOptions
import com.garfiec.librechat.core.common.speech.sttSupportsLiveRecognition
import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.model.speech.SttEngine
import com.garfiec.librechat.core.ui.components.AdaptiveFilledTonalButton
import com.garfiec.librechat.core.ui.components.AdaptiveSectionHeader
import com.garfiec.librechat.core.ui.components.adaptiveSection
import com.garfiec.librechat.core.ui.datetime.LocalDateTimeFormat
import com.garfiec.librechat.feature.settings.navigation.AdvancedSection
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import com.garfiec.librechat.feature.settings.viewmodel.ChatSettingsUiState
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

internal class VoiceSettingsActions(
    val onAutoSend: (Boolean) -> Unit,
    val onStopWhenPause: (Boolean) -> Unit,
    val onSpeechLanguage: (String) -> Unit,
    val onReadAloud: (Boolean) -> Unit,
    val onSource: (String) -> Unit,
    val onServerVoice: (String) -> Unit,
    val onDeviceVoice: (String) -> Unit,
    val onSpeed: (Float) -> Unit,
    val onPitch: (Float) -> Unit,
    val onPreviewToggle: () -> Unit,
    val onOpenAdvanced: () -> Unit,
)

@Composable
internal fun VoiceSettingsContent(
    state: ChatSettingsUiState,
    actions: VoiceSettingsActions,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val deviceSource = state.ttsSource != SettingsDataStore.TTS_SOURCE_SERVER
    SettingsListPage(contentPadding = contentPadding, modifier = modifier) {
        item(key = "listening_header") {
            AdaptiveSectionHeader(stringResource(Res.string.section_listening))
        }
        adaptiveSection {
            row(key = "stt_auto_send") {
                SettingsSwitchRow(
                    title = stringResource(Res.string.auto_send_after_speech),
                    description = stringResource(Res.string.auto_send_after_speech_desc),
                    checked = state.sttAutoSend,
                    onCheckedChange = actions.onAutoSend,
                )
            }
            if (liveRecognizerHonorsPrefs(state.sttEngine)) {
                row(key = "stt_end_of_speech") {
                    SettingsSwitchRow(
                        title = stringResource(Res.string.stt_end_of_speech_toggle),
                        description = stringResource(Res.string.stt_end_of_speech_toggle_desc),
                        checked = state.sttEndOfSpeech,
                        onCheckedChange = actions.onStopWhenPause,
                    )
                }
            }
            row(key = "stt_language") {
                val autoDetect = stringResource(Res.string.stt_auto_detect)
                // The first option is the stored "Auto-detect", shown translated.
                val label = { option: String -> if (option == sttLanguageOptions.first()) autoDetect else option }
                SettingsChoiceRow(
                    title = stringResource(Res.string.stt_language_label),
                    value = state.sttLanguage.ifBlank { sttLanguageOptions.first() }.let(label),
                    options = sttLanguageOptions.map { it to label(it) },
                    onSelect = actions.onSpeechLanguage,
                )
            }
        }
        item(key = "speaking_header") {
            AdaptiveSectionHeader(stringResource(Res.string.section_speaking))
        }
        adaptiveSection {
            row(key = "auto_read") {
                SettingsSwitchRow(
                    title = stringResource(Res.string.auto_read_responses),
                    description = stringResource(Res.string.auto_read_responses_desc),
                    checked = state.autoReadEnabled,
                    onCheckedChange = actions.onReadAloud,
                )
            }
            // A stored server source stays offered, so the row never shows a value it can't list.
            if (state.serverTtsEnabled || !deviceSource) {
                row(key = "tts_source") {
                    val sources = listOf(
                        SettingsDataStore.TTS_SOURCE_DEVICE to stringResource(Res.string.tts_source_device),
                        SettingsDataStore.TTS_SOURCE_SERVER to stringResource(Res.string.tts_source_server),
                    )
                    SettingsChoiceRow(
                        title = stringResource(Res.string.tts_source_label),
                        value = sources[if (deviceSource) 0 else 1].second,
                        options = sources,
                        onSelect = actions.onSource,
                    )
                }
            }
            if (deviceSource) {
                if (state.availableDeviceVoices.isNotEmpty()) {
                    row(key = "tts_device_voice") { DeviceVoiceRow(state, actions.onDeviceVoice) }
                }
                row(key = "tts_speed") {
                    SpeechSlider(
                        label = stringResource(Res.string.tts_speech_rate, oneDecimal(state.ttsSpeechRate)),
                        value = state.ttsSpeechRate,
                        onValueChange = actions.onSpeed,
                    )
                }
                row(key = "tts_pitch") {
                    SpeechSlider(
                        label = stringResource(Res.string.tts_pitch, oneDecimal(state.ttsPitch)),
                        value = state.ttsPitch,
                        onValueChange = actions.onPitch,
                    )
                }
            } else {
                row(key = "tts_server_voice") { ServerVoiceRow(state, actions.onServerVoice) }
            }
            row(key = "tts_preview") {
                PreviewButton(playing = state.isTtsPreviewPlaying, onClick = actions.onPreviewToggle)
            }
        }
        item(key = "advanced_link") {
            AdvancedSettingsLink(
                title = stringResource(Res.string.advanced_voice_settings),
                summary = stringResource(Res.string.advanced_voice_settings_summary),
                onClick = actions.onOpenAdvanced,
            )
        }
    }
}

/**
 * Whether the live recognizer reads the on-device and stop-when-I-pause choices: the platform must
 * run one, and where the engine picks the recognizer (Android) the engine must be Built-in.
 */
internal fun liveRecognizerHonorsPrefs(sttEngine: String): Boolean =
    sttSupportsLiveRecognition() &&
        (SttEngine.fromStored(sttEngine) == SttEngine.BROWSER || !sttEngineSelectsRecognizer())

@Composable
private fun DeviceVoiceRow(state: ChatSettingsUiState, onSelect: (String) -> Unit) {
    val systemDefault = stringResource(Res.string.tts_system_default)
    val options = listOf("" to systemDefault) +
        state.availableDeviceVoices.map { it.id to "${it.name} (${it.locale})" }
    // An earlier build stored the voice's name, which playback still resolves.
    val stored = state.availableDeviceVoices
        .firstOrNull { it.name == state.ttsDeviceVoiceName }?.id ?: state.ttsDeviceVoiceName
    SettingsChoiceRow(
        title = stringResource(Res.string.tts_voice_label),
        value = options.firstOrNull { it.first == stored }?.second ?: systemDefault,
        options = options,
        onSelect = onSelect,
    )
}

@Composable
private fun ServerVoiceRow(state: ChatSettingsUiState, onSelect: (String) -> Unit) {
    if (state.availableVoices.isEmpty()) {
        SettingsRow(
            title = stringResource(Res.string.tts_voice_label),
            summary = stringResource(Res.string.no_server_tts_voices),
        )
        return
    }
    SettingsChoiceRow(
        title = stringResource(Res.string.tts_voice_label),
        value = state.selectedVoice?.name.orEmpty(),
        options = state.availableVoices.map { voice ->
            voice.id to listOfNotNull(voice.name, voice.provider).joinToString(" · ")
        },
        onSelect = onSelect,
    )
}

/** A labelled slider over 0.5–2.0 that saves once the drag ends, not on every step. */
@Composable
private fun SpeechSlider(label: String, value: Float, onValueChange: (Float) -> Unit) {
    var dragged by remember(value) { mutableFloatStateOf(value) }
    Column(modifier = Modifier.fillMaxWidth().padding(SettingsRowPadding)) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Slider(
            value = dragged,
            onValueChange = { dragged = it },
            onValueChangeFinished = { onValueChange(dragged) },
            valueRange = 0.5f..2.0f,
            steps = 5,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PreviewButton(playing: Boolean, onClick: () -> Unit) {
    val cd = stringResource(if (playing) Res.string.cd_stop_voice_preview else Res.string.cd_preview_voice)
    AdaptiveFilledTonalButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(SettingsRowPadding)
            .semantics { contentDescription = cd },
    ) {
        Icon(
            imageVector = if (playing) Icons.Default.Stop else Icons.Default.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(if (playing) Res.string.tts_stop_preview else Res.string.tts_preview_voice))
    }
}

/**
 * Compose resources substitutes only `%N$s` / `%N$d`, so a `%.1f` in the string would render as
 * written; the number is formatted here instead, with the app locale's decimal separator.
 */
@Composable
private fun oneDecimal(value: Float): String = oneDecimal(value, LocalDateTimeFormat.current.localeTag)

internal fun oneDecimal(value: Float, localeTag: String): String =
    formatLocalizedNumber(value.toDouble(), localeTag, maxFractionDigits = 1, minFractionDigits = 1)

@Composable
private fun speechFailureText(failure: SpeechFailure): String = stringResource(
    when (failure) {
        SpeechFailure.VOICE_PREVIEW -> Res.string.speech_error_voice_preview
        SpeechFailure.PLAYBACK -> Res.string.speech_error_playback
    },
)
