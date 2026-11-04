package com.garfiec.librechat.feature.chat.viewmodel.delegate

import android.content.Context
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.common.result.suspendRunCatching
import com.garfiec.librechat.core.data.datastore.SettingsDataStore
import com.garfiec.librechat.core.data.repository.SpeechRepository
import com.garfiec.librechat.feature.chat.util.SERVER_TTS_SEGMENT_CHARS
import com.garfiec.librechat.feature.chat.util.splitForSpeech
import com.garfiec.librechat.feature.chat.viewmodel.TtsHandle
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

class TextToSpeechDelegate(
    private val handle: TtsHandle,
    private val appContext: Context,
    private val speechRepository: SpeechRepository,
    private val settingsDataStore: SettingsDataStore,
    private val ioDispatcher: CoroutineDispatcher,
    private val getMessageText: (String) -> String,
) {

    private var ttsEngine: TextToSpeech? = null
    private var ttsReady = false
    private var serverTtsPlayer: MediaPlayer? = null

    /** The read in progress (settings read, then device queueing or server segments); cancelled by a stop. */
    private var readJob: Job? = null

    /**
     * The device engine's id for the last piece of the current read. A long reply is queued as
     * several utterances (the engine refuses one over `getMaxSpeechInputLength()`), so only this
     * one finishing means the read is done.
     */
    @Volatile
    private var finalUtteranceId: String? = null

    private fun getOrInitTts(onReady: () -> Unit) {
        if (ttsReady && ttsEngine != null) {
            onReady()
            return
        }
        ttsEngine?.shutdown()
        ttsEngine = TextToSpeech(appContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                ttsEngine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) {
                        if (utteranceId != finalUtteranceId) return
                        handle.update { voice = voice.copy(currentlyReadingMessageId = null) }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        handle.update {
                            voice = voice.copy(currentlyReadingMessageId = null)
                            error = "Text-to-speech playback error"
                        }
                    }
                })
                onReady()
            } else {
                handle.update {
                    voice = voice.copy(currentlyReadingMessageId = null)
                    error = "Text-to-speech engine not available on this device"
                }
            }
        }
    }

    fun readAloud(messageId: String) {
        // If already reading this message, stop
        if (handle.state.currentlyReadingMessageId == messageId) {
            stopReading()
            return
        }

        // Stop any current playback first
        stopReading()

        val text = getMessageText(messageId)
        if (text.isBlank()) return

        handle.update { voice = voice.copy(currentlyReadingMessageId = messageId) }

        readJob = handle.scope.launch {
            val source = settingsDataStore.ttsSource.first()
            if (source == "server") {
                readAloudViaServer(text)
            } else {
                val rate = settingsDataStore.ttsSpeechRate.first()
                val pitch = settingsDataStore.ttsPitch.first()
                val voiceName = settingsDataStore.ttsVoiceName.first()
                readAloudViaDevice(messageId, text, rate, pitch, voiceName)
            }
        }
    }

    internal fun readAloudViaDevice(
        messageId: String,
        text: String,
        speechRate: Float,
        pitch: Float,
        voiceName: String,
    ) {
        getOrInitTts {
            val engine = ttsEngine ?: return@getOrInitTts
            engine.setSpeechRate(speechRate)
            engine.setPitch(pitch)
            if (voiceName.isNotBlank()) {
                engine.voices?.find { it.name == voiceName }?.let { voice ->
                    engine.setVoice(voice)
                }
            }
            // The engine refuses a single utterance over getMaxSpeechInputLength() (4,000 chars),
            // so a long reply is queued in pieces and reported done when the last one finishes.
            val pieces = splitForSpeech(text, maxOf(2, TextToSpeech.getMaxSpeechInputLength()))
            if (pieces.isEmpty()) {
                handle.update { voice = voice.copy(currentlyReadingMessageId = null) }
                return@getOrInitTts
            }
            finalUtteranceId = "$messageId#${pieces.lastIndex}"
            pieces.forEachIndexed { index, piece ->
                val mode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                engine.speak(piece, mode, Bundle(), "$messageId#$index")
            }
        }
    }

    /**
     * Reads [text] through the server, one segment per request, each played to its end before the
     * next is asked for. v0.8.9-rc1 synthesizes only the first `speech.tts.maxInputChars` (default
     * 20,000) characters of a request and drops the rest silently, so a long reply sent whole
     * stops partway. Older servers take the segments the same way.
     */
    internal suspend fun readAloudViaServer(text: String) {
        for (segment in splitForSpeech(text, SERVER_TTS_SEGMENT_CHARS)) {
            when (val result = speechRepository.synthesizeSpeech(segment)) {
                is Result.Success -> {
                    val played = suspendRunCatching { playServerAudio(result.data) }
                        .onFailure { e ->
                            handle.update {
                                voice = voice.copy(currentlyReadingMessageId = null)
                                error = "Server TTS playback failed: ${e.message}"
                            }
                        }
                        .getOrNull() ?: return
                    if (!played) {
                        handle.update {
                            voice = voice.copy(currentlyReadingMessageId = null)
                            error = "Server TTS playback failed"
                        }
                        return
                    }
                }
                is Result.Error -> {
                    handle.update {
                        voice = voice.copy(currentlyReadingMessageId = null)
                        error = result.message ?: "Server TTS request failed"
                    }
                    return
                }
                is Result.Loading -> Unit
            }
        }
        handle.update { voice = voice.copy(currentlyReadingMessageId = null) }
    }

    /** Plays one segment's audio to its end: true when it completed, false on a player error. */
    private suspend fun playServerAudio(audioBytes: ByteArray): Boolean {
        val ttsDir = File(appContext.cacheDir, "tts")
        val tempFile = withContext(ioDispatcher) {
            ttsDir.mkdirs()
            File.createTempFile("tts_", ".mp3", ttsDir).apply {
                deleteOnExit()
                writeBytes(audioBytes)
            }
        }
        var player: MediaPlayer? = null
        try {
            // Created on a Looper-less ioDispatcher thread, so its callbacks fire on the Main looper.
            // Assigned before prepare() so a failed prepare is still released below.
            val prepared = withContext(ioDispatcher) {
                MediaPlayer().also { player = it }.apply {
                    setDataSource(tempFile.absolutePath)
                    prepare()
                }
            }
            serverTtsPlayer = prepared
            return suspendCancellableCoroutine { continuation ->
                prepared.setOnCompletionListener { if (continuation.isActive) continuation.resume(true) }
                prepared.setOnErrorListener { _, _, _ ->
                    if (continuation.isActive) continuation.resume(false)
                    true
                }
                prepared.start()
            }
        } finally {
            val created = player
            if (created != null && serverTtsPlayer === created) serverTtsPlayer = null
            // release() and the file delete block, and must run even when a stop cancelled us.
            withContext(NonCancellable + ioDispatcher) {
                created?.release()
                tempFile.delete()
            }
        }
    }

    fun stopReading() {
        readJob?.cancel()
        readJob = null
        finalUtteranceId = null
        ttsEngine?.stop()
        // The cancelled read releases the player itself; stopping it here silences it at once.
        serverTtsPlayer?.let { runCatching { it.stop() } }
        serverTtsPlayer = null
        handle.update { voice = voice.copy(currentlyReadingMessageId = null) }
    }

    /**
     * Checks the auto-read preference and, if enabled, reads the completed AI
     * response aloud via TTS. Skips auto-read when the user has already started
     * typing a new message (inputText is not blank).
     *
     * Uses a synthetic message ID ("auto_read") because the real message ID
     * may not be available yet (Room observer hasn't emitted the final message).
     */
    fun maybeAutoReadResponse(responseText: String) {
        // Don't auto-read if the user has already started typing
        if (handle.state.inputText.isNotBlank()) return

        readJob = handle.scope.launch {
            val autoRead = settingsDataStore.autoReadEnabled.first()
            if (!autoRead) return@launch

            val syntheticId = "auto_read_${System.currentTimeMillis()}"
            handle.update { voice = voice.copy(currentlyReadingMessageId = syntheticId) }

            val source = settingsDataStore.ttsSource.first()
            if (source == "server") {
                readAloudViaServer(responseText)
            } else {
                val rate = settingsDataStore.ttsSpeechRate.first()
                val pitch = settingsDataStore.ttsPitch.first()
                val voiceName = settingsDataStore.ttsVoiceName.first()
                readAloudViaDevice(syntheticId, responseText, rate, pitch, voiceName)
            }
        }
    }

    fun release() {
        readJob?.cancel()
        readJob = null
        ttsEngine?.shutdown()
        ttsEngine = null
        ttsReady = false
        serverTtsPlayer?.release()
        serverTtsPlayer = null
    }
}
