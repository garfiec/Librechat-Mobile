package com.garfiec.librechat.feature.chat.viewmodel.delegate

interface PlatformTts {
    fun readAloud(messageId: String)
    fun stopReading()
    fun maybeAutoReadResponse(messageId: String, responseText: String)
    fun release()
}
