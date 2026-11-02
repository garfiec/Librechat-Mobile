package com.garfiec.librechat.core.model.config

import kotlin.test.Test
import kotlin.test.assertEquals

class UrlContextSupportTest {

    @Test
    fun matchesUpstreamsTable() {
        val cases = mapOf(
            "gemini-2.5-pro" to true,
            "gemini-2.5-flash-lite" to true,
            "gemini-3-pro-preview" to true,
            "models/gemini-3.1-flash" to true,
            "Gemini-2.5-Pro" to true,
            "gemini-2.0-flash" to false,
            "gemini-1.5-pro" to false,
            "gemini-2.5-flash-image" to false,
            "gemini-2.5-flash-preview-tts" to false,
            "gemini-live-2.5-flash" to false,
            "gemini-2.5-flash-native-audio" to false,
            "gemma-3-27b" to false,
            "" to false,
        )
        cases.forEach { (model, expected) -> assertEquals(expected, supportsUrlContext(model), model) }
    }
}
