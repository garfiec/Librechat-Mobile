package com.garfiec.librechat.feature.settings.viewmodel.delegate

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.common.result.suspendRunCatching
import com.garfiec.librechat.core.data.repository.ServerSttGate
import com.garfiec.librechat.core.data.repository.SpeechRepository
import com.garfiec.librechat.feature.settings.viewmodel.ChatSettingsUiState
import com.garfiec.librechat.feature.settings.viewmodel.DeviceVoiceInfo
import com.garfiec.librechat.feature.settings.viewmodel.SettingsStateHandle
import com.garfiec.librechat.feature.settings.viewmodel.SpeechFailure
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Loads the server and device voices and plays the voice preview, owning its MediaPlayer.
 */
class SpeechSettingsDelegate(
    private val stateHandle: SettingsStateHandle<ChatSettingsUiState>,
    private val context: Context,
    private val speechRepository: SpeechRepository,
    private val ioDispatcher: CoroutineDispatcher,
) : SpeechSettingsContract {

    private var currentMediaPlayer: MediaPlayer? = null
    private var serverPreviewJob: Job? = null
    private var deviceTtsEngine: TextToSpeech? = null

    // Loads + latches the server speech config (STT and TTS flags) with the shared "unknown vs
    // disabled" retry policy. Until it has resolved once (gate.loaded), serverSttEnabled and
    // serverTtsEnabled = false mean "unknown", not "disabled" — so opening the Voice page retries
    // rather than permanently hiding the Server engine or the Server voice source.
    private val serverSttGate = ServerSttGate(speechRepository)
    private var speechConfigJob: Job? = null

    override val speechConfigLoaded: Boolean get() = serverSttGate.loaded

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
        stateHandle.scope.launch {
            // TextToSpeech construction touches disk/IPC; build it off the Main thread.
            val engine = withContext(ioDispatcher) {
                lateinit var tts: TextToSpeech
                tts = TextToSpeech(context) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        // .voices enumeration is heavy; do it off Main before publishing.
                        stateHandle.scope.launch {
                            val voices = withContext(ioDispatcher) {
                                tts.voices?.map { voice ->
                                    DeviceVoiceInfo(
                                        id = voice.name,
                                        name = voice.name,
                                        locale = voice.locale.displayName,
                                    )
                                }?.sortedBy { it.name } ?: emptyList()
                            }
                            stateHandle.update { copy(availableDeviceVoices = voices) }
                        }
                    }
                }
                tts
            }
            deviceTtsEngine = engine
        }
    }

    override fun loadSpeechConfig() {
        // A page opened while the launch fetch is still in flight joins it rather than racing it.
        if (speechConfigJob?.isActive == true) return
        speechConfigJob = stateHandle.scope.launch {
            // A null result means a transient failure — leave the gate unlatched so opening the Voice
            // page retries rather than trusting it as a definitive "disabled".
            val config = serverSttGate.refreshConfig()
            val ttsEnabled = config?.ttsExternal ?: false
            stateHandle.update {
                copy(serverSttEnabled = config?.sttExternal ?: false, serverTtsEnabled = ttsEnabled)
            }
            // A server without `speech.tts` answers the voices route with a 500.
            if (ttsEnabled) loadVoices()
        }
    }

    override fun previewDeviceTts(text: String, rate: Float, pitch: Float, voiceName: String?) {
        stopTtsPreview()
        val tts = deviceTtsEngine ?: return
        tts.setSpeechRate(rate)
        tts.setPitch(pitch)
        val targetVoice = voiceName?.takeIf { it.isNotBlank() }?.let { name -> tts.voices?.find { it.name == name } }
        (targetVoice ?: tts.defaultVoice)?.let { tts.voice = it }
        stateHandle.update { copy(isTtsPreviewPlaying = true) }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { /* no-op */ }
            override fun onDone(utteranceId: String?) {
                stateHandle.update { copy(isTtsPreviewPlaying = false) }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                stateHandle.update { copy(isTtsPreviewPlaying = false) }
            }
        })
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_preview")
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
                    playAudioBytes(result.data)
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
        deviceTtsEngine?.stop()
        currentMediaPlayer?.release()
        currentMediaPlayer = null
        stateHandle.update { copy(isTtsPreviewPlaying = false) }
    }

    /**
     * Plays audio bytes via MediaPlayer. Attaches listeners before calling prepare()
     * and releases the player if prepare/start fails.
     */
    private suspend fun playAudioBytes(audioBytes: ByteArray) {
        suspendRunCatching {
            // Stop any currently playing audio
            currentMediaPlayer?.release()
            currentMediaPlayer = null

            // File write + MediaPlayer prepare are blocking; run them off the Main thread.
            val mediaPlayer = withContext(ioDispatcher) {
                val voiceTestDir = File(context.cacheDir, "voice_test").apply { mkdirs() }
                val tempFile = File.createTempFile("voice_test", ".mp3", voiceTestDir)
                tempFile.deleteOnExit()
                tempFile.writeBytes(audioBytes)

                val mp = MediaPlayer()
                // Attach listeners BEFORE calling prepare() to avoid resource leak
                mp.setDataSource(tempFile.absolutePath)
                // The MediaPlayer is built on a Looper-less ioDispatcher thread, so these
                // callbacks fire on the Main looper. release() + temp-file delete are blocking,
                // so hand them to ioDispatcher rather than running them on Main.
                mp.setOnCompletionListener { player ->
                    currentMediaPlayer = null
                    stateHandle.update { copy(isTtsPreviewPlaying = false) }
                    stateHandle.scope.launch(ioDispatcher) {
                        player.release()
                        tempFile.delete()
                    }
                }
                mp.setOnErrorListener { player, what, extra ->
                    Logger.e { "MediaPlayer error: what=$what, extra=$extra" }
                    currentMediaPlayer = null
                    stateHandle.update {
                        copy(error = SpeechFailure.PLAYBACK, isTtsPreviewPlaying = false)
                    }
                    stateHandle.scope.launch(ioDispatcher) {
                        player.release()
                        tempFile.delete()
                    }
                    true
                }
                var started = false
                try {
                    mp.prepare()
                    mp.start()
                    started = true
                } finally {
                    // Release MediaPlayer if prepare() or start() throws
                    if (!started) {
                        mp.release()
                        tempFile.delete()
                    }
                }
                mp
            }

            currentMediaPlayer = mediaPlayer
        }.onFailure { e ->
            Logger.e(e) { "Failed to play audio" }
            stateHandle.update {
                copy(
                    error = SpeechFailure.PLAYBACK,
                    isTtsPreviewPlaying = false,
                )
            }
        }
    }

    /**
     * Releases TTS engine and media player resources. Call from ViewModel.onCleared().
     */
    override fun release() {
        serverPreviewJob?.cancel()
        currentMediaPlayer?.release()
        currentMediaPlayer = null
        deviceTtsEngine?.shutdown()
        deviceTtsEngine = null
    }
}
