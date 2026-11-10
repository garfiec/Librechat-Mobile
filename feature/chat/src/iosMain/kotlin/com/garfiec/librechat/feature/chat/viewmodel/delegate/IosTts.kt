package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.data.repository.SpeechRepository
import com.garfiec.librechat.core.model.speech.TtsVoice
import com.garfiec.librechat.core.ui.media.AudioClipPlayer
import com.garfiec.librechat.core.ui.media.deviceUtterance
import com.garfiec.librechat.core.ui.media.resolveDeviceVoice
import com.garfiec.librechat.feature.chat.util.serverVoiceToUse
import com.garfiec.librechat.feature.chat.util.synthesizeInSegments
import com.garfiec.librechat.feature.chat.viewmodel.TtsHandle
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import platform.AVFAudio.AVSpeechBoundary
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechSynthesizerDelegateProtocol
import platform.AVFAudio.AVSpeechUtterance
import platform.darwin.NSObject

class IosTts(
    private val handle: TtsHandle,
    private val speechRepository: SpeechRepository,
    private val settingsDataStore: SettingsDataStore,
    private val getMessageText: (String) -> String,
) : PlatformTts {

    private val synthesizer = AVSpeechSynthesizer()

    private val serverAudio = AudioClipPlayer()

    /** The read in flight. A server read is a loop of requests, which a Stop must end. */
    private var readJob: Job? = null

    /** The utterance of the current device read; only its end means the read is done. */
    private var finalUtterance: AVSpeechUtterance? = null

    /** The server's voice list from the last successful read, so a read-aloud doesn't wait on it again. */
    private var serverVoices: List<TtsVoice>? = null

    private suspend fun offeredServerVoices(): Result<List<TtsVoice>> =
        serverVoices?.let { Result.Success(it) }
            ?: speechRepository.getVoices().also { if (it is Result.Success) serverVoices = it.data }

    private val delegate = object : NSObject(), AVSpeechSynthesizerDelegateProtocol {
        @ObjCSignatureOverride
        override fun speechSynthesizer(
            synthesizer: AVSpeechSynthesizer,
            didFinishSpeechUtterance: AVSpeechUtterance,
        ) {
            if (didFinishSpeechUtterance === finalUtterance) {
                finalUtterance = null
                handle.update { voice = voice.copy(currentlyReadingMessageId = null) }
            }
        }

        @ObjCSignatureOverride
        override fun speechSynthesizer(
            synthesizer: AVSpeechSynthesizer,
            didCancelSpeechUtterance: AVSpeechUtterance,
        ) {
            // A stop already cleared the state; a late cancel from an earlier read must not clear
            // the read that replaced it.
            if (didCancelSpeechUtterance === finalUtterance) {
                finalUtterance = null
                handle.update { voice = voice.copy(currentlyReadingMessageId = null) }
            }
        }
    }

    init {
        synthesizer.delegate = delegate
    }

    override fun readAloud(messageId: String) {
        if (handle.state.currentlyReadingMessageId == messageId) {
            stopReading()
            return
        }

        val text = getMessageText(messageId)
        if (text.isBlank()) {
            stopReading()
            return
        }
        startRead(messageId, text)
    }

    private fun startRead(readingId: String, text: String) {
        stopReading()
        handle.update { voice = voice.copy(currentlyReadingMessageId = readingId) }

        readJob = handle.scope.launch {
            if (settingsDataStore.ttsSource.first() == SettingsDataStore.TTS_SOURCE_SERVER) {
                readAloudViaServer(text)
            } else {
                queueUtterance(
                    deviceUtterance(
                        text,
                        rate = settingsDataStore.ttsSpeechRate.first(),
                        pitch = settingsDataStore.ttsPitch.first(),
                        voice = resolveDeviceVoice(settingsDataStore.ttsVoiceName.first()),
                    ),
                )
            }
        }
    }

    private fun queueUtterance(utterance: AVSpeechUtterance) {
        finalUtterance = utterance
        synthesizer.speakUtterance(utterance)
    }

    /**
     * Requests [text] from the server in the same capped segments as Android (the server drops
     * whatever is past `speech.tts.maxInputChars` without an error) and plays each segment's audio in
     * order, fetching only the next one while one plays. A failed request lets what arrived finish.
     */
    private suspend fun readAloudViaServer(text: String) = coroutineScope {
        val serverVoice = serverVoiceToUse(settingsDataStore.selectedVoiceId.first(), ::offeredServerVoices)
        val clips = Channel<ByteArray>(capacity = 1)
        val playback = launch {
            for (clip in clips) {
                if (!serverAudio.play(clip)) {
                    clips.close()
                    endRead(error = "Couldn't play the server's audio")
                    return@launch
                }
            }
            endRead(error = null)
        }
        val failure = synthesizeInSegments(
            text,
            synthesize = { segment -> speechRepository.synthesizeSpeech(segment, voice = serverVoice) },
        ) { _, audio, _ ->
            try {
                clips.send(audio)
                true
            } catch (_: ClosedSendChannelException) {
                false
            }
        }
        clips.close()
        playback.join()
        if (failure != null) endRead(error = failure.message ?: "Server TTS request failed")
    }

    private fun endRead(error: String?) {
        handle.update {
            voice = voice.copy(currentlyReadingMessageId = null)
            if (error != null) this.error = error
        }
    }

    private fun haltSpeech() {
        serverAudio.stop()
        finalUtterance = null
        if (synthesizer.isSpeaking()) {
            synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        }
    }

    override fun stopReading() {
        readJob?.cancel()
        readJob = null
        haltSpeech()
        handle.update { voice = voice.copy(currentlyReadingMessageId = null) }
    }

    override fun maybeAutoReadResponse(messageId: String, responseText: String) {
        handle.scope.launch {
            if (!settingsDataStore.autoReadEnabled.first()) return@launch
            startRead(messageId, responseText)
        }
    }

    override fun release() {
        readJob?.cancel()
        readJob = null
        haltSpeech()
        synthesizer.delegate = null
    }
}
