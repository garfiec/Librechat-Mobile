package com.garfiec.librechat.feature.settings.viewmodel.delegate

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ServerSttGate
import com.garfiec.librechat.core.data.repository.SpeechRepository
import com.garfiec.librechat.core.ui.media.AudioClipPlayer
import com.garfiec.librechat.core.ui.media.deviceUtterance
import com.garfiec.librechat.core.ui.media.findDeviceVoice
import com.garfiec.librechat.core.ui.media.resolveDeviceVoice
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

class IosSpeechSettingsDelegate(
    private val stateHandle: SettingsStateHandle<ChatSettingsUiState>,
    private val speechRepository: SpeechRepository,
) : SpeechSettingsContract {

    private val synthesizer = AVSpeechSynthesizer()

    private val serverAudio = AudioClipPlayer()
    private var serverPreviewJob: Job? = null

    // Latches the speech config; read here for its TTS flag only (see loadSpeechConfig).
    private val speechConfigGate = ServerSttGate(speechRepository)
    private var speechConfigJob: Job? = null

    override val speechConfigLoaded: Boolean get() = speechConfigGate.loaded

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

        // A stored voice that's gone leaves the utterance's voice unset.
        val voice = if (voiceName.isNullOrBlank()) resolveDeviceVoice(null) else findDeviceVoice(voiceName)
        synthesizer.speakUtterance(deviceUtterance(text, rate, pitch, voice))
    }

    override fun previewServerTts(text: String, voice: String?) {
        stopTtsPreview()
        stateHandle.update { copy(isTtsPreviewPlaying = true) }
        serverPreviewJob = stateHandle.scope.launch {
            val result = speechRepository.synthesizeSpeech(
                text = text,
                voice = voice,
            )
            when (result) {
                is Result.Success -> {
                    val played = serverAudio.play(result.data)
                    stateHandle.update {
                        copy(
                            isTtsPreviewPlaying = false,
                            error = if (played) error else SpeechFailure.PLAYBACK,
                        )
                    }
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

    override fun stopTtsPreview() {
        serverPreviewJob?.cancel()
        serverPreviewJob = null
        serverAudio.stop()
        if (synthesizer.isSpeaking()) {
            synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        }
        stateHandle.update { copy(isTtsPreviewPlaying = false) }
    }

    override fun release() {
        serverPreviewJob?.cancel()
        serverAudio.stop()
        if (synthesizer.isSpeaking()) {
            synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        }
        synthesizer.delegate = null
    }
}
