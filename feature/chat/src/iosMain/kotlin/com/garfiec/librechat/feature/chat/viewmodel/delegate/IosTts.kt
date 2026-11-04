package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.data.repository.SpeechRepository
import com.garfiec.librechat.feature.chat.util.synthesizeInSegments
import com.garfiec.librechat.feature.chat.viewmodel.TtsHandle
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.coroutines.Job
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

class IosTts(
    private val handle: TtsHandle,
    private val speechRepository: SpeechRepository,
    private val settingsDataStore: SettingsDataStore,
    private val getMessageText: (String) -> String,
) : PlatformTts {

    private val synthesizer = AVSpeechSynthesizer()

    /** The read in flight. A server read is a loop of requests, which a Stop must end. */
    private var readJob: Job? = null

    /** The last utterance queued for the current read; only its end means the read is done. */
    private var finalUtterance: AVSpeechUtterance? = null

    /** True while a server read still has segments to request after [finalUtterance]. */
    private var moreSegmentsPending = false

    private val delegate = object : NSObject(), AVSpeechSynthesizerDelegateProtocol {
        @ObjCSignatureOverride
        override fun speechSynthesizer(
            synthesizer: AVSpeechSynthesizer,
            didFinishSpeechUtterance: AVSpeechUtterance,
        ) {
            if (didFinishSpeechUtterance === finalUtterance && !moreSegmentsPending) {
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
            val source = settingsDataStore.ttsSource.first()
            val rate = settingsDataStore.ttsSpeechRate.first()
            val pitch = settingsDataStore.ttsPitch.first()
            if (source == "server") {
                readAloudViaServer(text, rate, pitch)
            } else {
                moreSegmentsPending = false
                queueUtterance(text, rate, pitch)
            }
        }
    }

    private fun queueUtterance(text: String, speechRate: Float, pitch: Float) {
        val utterance = AVSpeechUtterance.speechUtteranceWithString(text)
        // AVSpeechUtterance rate: 0.0-1.0, default ~0.5
        // Android TTS rate: 0.1-4.0, default 1.0. Scale proportionally.
        utterance.rate = (speechRate * DEFAULT_SPEECH_RATE).coerceIn(MIN_SPEECH_RATE, MAX_SPEECH_RATE)
        // AVSpeechUtterance pitch: 0.5-2.0, default 1.0 (same as Android)
        utterance.pitchMultiplier = pitch.coerceIn(0.5f, 2.0f)
        utterance.voice = AVSpeechSynthesisVoice.voiceWithLanguage(null)
        finalUtterance = utterance
        synthesizer.speakUtterance(utterance)
    }

    /**
     * Requests [text] from the server in the same capped segments as Android (the server drops
     * whatever is past `speech.tts.maxInputChars` without an error), queuing each segment as its
     * request succeeds. The audio itself is not played: AVAudioPlayer needs platform plumbing this
     * path does not have yet, so each confirmed segment is spoken by the device voice.
     */
    private suspend fun readAloudViaServer(text: String, speechRate: Float, pitch: Float) {
        val failure = synthesizeInSegments(text, speechRepository::synthesizeSpeech) { segment, _, isLast ->
            moreSegmentsPending = !isLast
            queueUtterance(segment, speechRate, pitch)
            true
        }
        if (failure != null) {
            // Earlier segments may still be speaking; silence them so the idle button is true.
            haltSpeech()
            handle.update {
                voice = voice.copy(currentlyReadingMessageId = null)
                error = failure.message ?: "Server TTS request failed"
            }
        }
    }

    private fun haltSpeech() {
        moreSegmentsPending = false
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
