package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.speech.SpeechConfig
import com.garfiec.librechat.core.model.speech.SpeechToTextResponse
import com.garfiec.librechat.core.model.speech.TtsVoice

interface SpeechRepository {
    /** [language] is a BCP-47 tag, or null to let the server's provider auto-detect. */
    suspend fun transcribeAudio(audioData: ByteArray, mimeType: String, language: String?): Result<SpeechToTextResponse>
    suspend fun synthesizeSpeech(text: String, voice: String? = null, model: String? = null): Result<ByteArray>
    suspend fun getVoices(): Result<List<TtsVoice>>
    suspend fun getSpeechConfig(): Result<SpeechConfig>

    /**
     * Whether the server has external (Whisper) STT configured — the `sttExternal` flag from
     * [getSpeechConfig]. Centralizes the config→boolean mapping shared by the chat and settings
     * speech delegates (each keeps its own retry/latch policy around this call).
     */
    suspend fun isServerSttEnabled(): Result<Boolean>
}
