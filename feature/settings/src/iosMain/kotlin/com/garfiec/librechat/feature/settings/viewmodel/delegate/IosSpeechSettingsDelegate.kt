package com.garfiec.librechat.feature.settings.viewmodel.delegate

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ServerSttGate
import com.garfiec.librechat.core.data.repository.SpeechRepository
import com.garfiec.librechat.feature.settings.viewmodel.ChatSettingsUiState
import com.garfiec.librechat.feature.settings.viewmodel.DeviceVoiceInfo
import com.garfiec.librechat.feature.settings.viewmodel.SettingsStateHandle
import com.garfiec.librechat.feature.settings.viewmodel.SpeechFailure
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import platform.AVFAudio.AVSpeechBoundary
import platform.AVFAudio.AVSpeechSynthesisVoice
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechSynthesizerDelegateProtocol
import platform.AVFAudio.AVSpeechUtterance
import platform.darwin.NSObject

private const val DEFAULT_SPEECH_RATE = 0.5f
private const val MIN_SPEECH_RATE = 0.0f
private const val MAX_SPEECH_RATE = 1.0f

class IosSpeechSettingsDelegate(
    private val stateHandle: SettingsStateHandle<ChatSettingsUiState>,
    private val speechRepository: SpeechRepository,
) : SpeechSettingsContract {

    private val synthesizer = AVSpeechSynthesizer()

    // Latches the speech config; read here for its TTS flag only (see loadSpeechConfig).
    private val speechConfigGate = ServerSttGate(speechRepository)
    private var speechConfigJob: Job? = null

    private val synthesizerDelegate = object : NSObject(), AVSpeechSynthesizerDelegateProtocol {
        @ObjCSignatureOverride
        override fun speechSynthesizer(
            synthesizer: AVSpeechSynthesizer,
            didFinishSpeechUtterance: AVSpeechUtterance,
        ) {
            stateHandle.update { copy(isTtsPreviewPlaying = false) }
        }

        @ObjCSignatureOverride
        override fun speechSynthesizer(
            synthesizer: AVSpeechSynthesizer,
            didCancelSpeechUtterance: AVSpeechUtterance,
        ) {
            stateHandle.update { copy(isTtsPreviewPlaying = false) }
        }
    }

    init {
        synthesizer.delegate = synthesizerDelegate
    }

    override fun loadVoices() {
        stateHandle.scope.launch {
            when (val result = speechRepository.getVoices()) {
                is Result.Success -> {
                    stateHandle.update { copy(availableVoices = result.data) }
                }
                is Result.Error -> {
                    Logger.d(result.exception) { "Failed to load voices: ${result.message}" }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    override fun loadDeviceVoices() {
        val voices = AVSpeechSynthesisVoice.speechVoices()
            .filterIsInstance<AVSpeechSynthesisVoice>()
            .map { voice ->
                DeviceVoiceInfo(
                    id = voice.identifier,
                    name = voice.name,
                    locale = voice.language,
                )
            }
            .sortedBy { it.name }
        stateHandle.update { copy(availableDeviceVoices = voices) }
    }

    override fun loadSpeechConfig() {
        // iOS has no server-STT (External) recording path yet — IosVoiceInput always uses
        // SFSpeechRecognizer — so serverSttEnabled intentionally stays false. This keeps the
        // Server engine hidden on Advanced › Voice rather than offering an option that would
        // silently do nothing when selected. (Follow-up: implement iOS External via AVAudioRecorder
        // + speechRepository.transcribeAudio, then set it from sttExternal like Android does.)
        // The config is still fetched for server TTS, which iOS does support. A page opened while
        // the launch fetch is still in flight joins it rather than racing it.
        if (speechConfigJob?.isActive == true) return
        speechConfigJob = stateHandle.scope.launch {
            val ttsEnabled = speechConfigGate.refreshConfig()?.ttsExternal ?: false
            stateHandle.update { copy(serverTtsEnabled = ttsEnabled) }
            // A server without `speech.tts` answers the voices route with a 500.
            if (ttsEnabled) loadVoices()
        }
    }

    override fun previewDeviceTts(text: String, rate: Float, pitch: Float, voiceName: String?) {
        stopTtsPreview()
        stateHandle.update { copy(isTtsPreviewPlaying = true) }

        val utterance = AVSpeechUtterance.speechUtteranceWithString(text)
        utterance.rate = (rate * DEFAULT_SPEECH_RATE).coerceIn(MIN_SPEECH_RATE, MAX_SPEECH_RATE)
        utterance.pitchMultiplier = pitch.coerceIn(0.5f, 2.0f)

        if (!voiceName.isNullOrBlank()) {
            // Settings stores the voice's identifier; a name stored by an older build still resolves.
            val targetVoice = AVSpeechSynthesisVoice.voiceWithIdentifier(voiceName)
                ?: AVSpeechSynthesisVoice.speechVoices()
                    .filterIsInstance<AVSpeechSynthesisVoice>()
                    .find { it.name == voiceName }
            if (targetVoice != null) {
                utterance.voice = targetVoice
            }
        } else {
            utterance.voice = AVSpeechSynthesisVoice.voiceWithLanguage(null)
        }

        synthesizer.speakUtterance(utterance)
    }

    override fun previewServerTts(text: String, voice: String?) {
        stopTtsPreview()
        stateHandle.update { copy(isTtsPreviewPlaying = true) }
        stateHandle.scope.launch {
            val result = speechRepository.synthesizeSpeech(
                text = text,
                voice = voice,
            )
            when (result) {
                is Result.Success -> {
                    // Server returns audio bytes; fall back to device TTS for playback
                    previewDeviceTts(text, 1.0f, 1.0f, null)
                }
                is Result.Error -> {
                    stateHandle.update {
                        copy(
                            isTtsPreviewPlaying = false,
                            error = SpeechFailure.VOICE_PREVIEW,
                        )
                    }
                }
                is Result.Loading -> { /* no-op */ }
            }
        }
    }

    override fun retrySpeechConfig() {
        if (!speechConfigGate.loaded) {
            loadSpeechConfig()
        } else if (stateHandle.state.serverTtsEnabled && stateHandle.state.availableVoices.isEmpty()) {
            loadVoices()
        }
    }

    override fun stopTtsPreview() {
        if (synthesizer.isSpeaking()) {
            synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        }
        stateHandle.update { copy(isTtsPreviewPlaying = false) }
    }

    override fun release() {
        if (synthesizer.isSpeaking()) {
            synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        }
        synthesizer.delegate = null
    }
}
