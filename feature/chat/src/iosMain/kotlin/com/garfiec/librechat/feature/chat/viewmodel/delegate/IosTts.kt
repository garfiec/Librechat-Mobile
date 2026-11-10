package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.data.repository.SpeechRepository
import com.garfiec.librechat.core.ui.media.AudioClipPlayer
import com.garfiec.librechat.feature.chat.util.synthesizeInSegments
import com.garfiec.librechat.feature.chat.viewmodel.TtsHandle
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import platform.AVFAudio.AVSpeechBoundary
import platform.AVFAudio.AVSpeechSynthesisVoice
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechSynthesizerDelegateProtocol
import platform.AVFAudio.AVSpeechUtterance
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import platform.darwin.NSObject

private const val DEFAULT_SPEECH_RATE = 0.5f
private const val MIN_SPEECH_RATE = 0.0f
private const val MAX_SPEECH_RATE = 1.0f

/** How the device voice speaks a read: Settings' speed, pitch and voice. */
private class DeviceSpeech(val rate: Float, val pitch: Float, val voice: AVSpeechSynthesisVoice?)

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
            if (settingsDataStore.ttsSource.first() == "server") {
                readAloudViaServer(text, settingsDataStore.selectedVoiceId.first())
            } else {
                val speech = DeviceSpeech(
                    rate = settingsDataStore.ttsSpeechRate.first(),
                    pitch = settingsDataStore.ttsPitch.first(),
                    voice = deviceVoice(settingsDataStore.ttsVoiceName.first()),
                )
                queueUtterance(text, speech)
            }
        }
    }

    private fun queueUtterance(text: String, speech: DeviceSpeech) {
        val utterance = AVSpeechUtterance.speechUtteranceWithString(text)
        // AVSpeechUtterance rate: 0.0-1.0, default ~0.5
        // Android TTS rate: 0.1-4.0, default 1.0. Scale proportionally.
        utterance.rate = (speech.rate * DEFAULT_SPEECH_RATE).coerceIn(MIN_SPEECH_RATE, MAX_SPEECH_RATE)
        // AVSpeechUtterance pitch: 0.5-2.0, default 1.0 (same as Android)
        utterance.pitchMultiplier = speech.pitch.coerceIn(0.5f, 2.0f)
        utterance.voice = speech.voice
        finalUtterance = utterance
        synthesizer.speakUtterance(utterance)
    }

    /**
     * The device voice Settings stored by identifier (a name stored by an older build still resolves),
     * or the default voice when it is unset or gone.
     */
    private fun deviceVoice(stored: String): AVSpeechSynthesisVoice? =
        stored.takeIf { it.isNotBlank() }?.let { id ->
            AVSpeechSynthesisVoice.voiceWithIdentifier(id)
                ?: AVSpeechSynthesisVoice.speechVoices()
                    .filterIsInstance<AVSpeechSynthesisVoice>()
                    .find { it.name == id }
        } ?: AVSpeechSynthesisVoice.voiceWithLanguage(null)

    /**
     * Requests [text] from the server in the same capped segments as Android (the server drops
     * whatever is past `speech.tts.maxInputChars` without an error) and plays each segment's audio in
     * order, fetching the next while one plays.
     */
    private suspend fun readAloudViaServer(text: String, serverVoice: String?) = coroutineScope {
        val clips = Channel<ByteArray>(Channel.UNLIMITED)
        val playback = launch {
            for (clip in clips) {
                if (!serverAudio.play(clip)) {
                    clips.cancel()
                    endRead(error = "Couldn't play the server's audio")
                    return@launch
                }
            }
            endRead(error = null)
        }
        val failure = synthesizeInSegments(
            text,
            synthesize = { segment -> speechRepository.synthesizeSpeech(segment, voice = serverVoice) },
        ) { _, audio, _ -> clips.trySend(audio).isSuccess }
        clips.close()
        if (failure != null) {
            playback.cancel()
            serverAudio.stop()
            endRead(error = failure.message ?: "Server TTS request failed")
        }
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

    override fun maybeAutoReadResponse(responseText: String) {
        if (handle.state.inputText.isNotBlank()) return

        handle.scope.launch {
            val autoRead = settingsDataStore.autoReadEnabled.first()
            if (!autoRead) return@launch
            startRead("auto_read_${NSDate().timeIntervalSince1970().toLong()}", responseText)
        }
    }

    override fun release() {
        readJob?.cancel()
        readJob = null
        haltSpeech()
        synthesizer.delegate = null
    }
}
