package com.garfiec.librechat.feature.chat.viewmodel.delegate

/** Read-aloud for chat messages; Android wraps [TextToSpeechDelegate], iOS is `IosTts`. */
interface PlatformTts {
    fun readAloud(messageId: String)
    fun stopReading()
    /** Reads a completed reply aloud under [messageId] when auto-read is on. */
    fun maybeAutoReadResponse(messageId: String, responseText: String)
    fun release()
}
