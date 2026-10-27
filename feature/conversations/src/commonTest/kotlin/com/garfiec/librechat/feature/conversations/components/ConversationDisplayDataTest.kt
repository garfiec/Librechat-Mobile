package com.garfiec.librechat.feature.conversations.components

import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.EndpointConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConversationDisplayDataTest {

    @Test
    fun iconURLPrefersConversationOverEndpointConfig() {
        val convo = Conversation(
            conversationId = "c1",
            endpoint = "OpenRouter",
            iconURL = "https://convo-icon.example.com/icon.png",
        )
        val configs = mapOf(
            "OpenRouter" to EndpointConfig(iconURL = "https://config-icon.example.com/icon.png"),
        )

        val data = convo.toDisplayData(configs, serverUrl = "")

        assertEquals("https://convo-icon.example.com/icon.png", data.endpointIconUrl)
    }

    @Test
    fun iconURLFallsBackToEndpointConfig() {
        val convo = Conversation(
            conversationId = "c1",
            endpoint = "OpenRouter",
            iconURL = null,
        )
        val configs = mapOf(
            "OpenRouter" to EndpointConfig(iconURL = "https://config-icon.example.com/icon.png"),
        )

        val data = convo.toDisplayData(configs, serverUrl = "")

        assertEquals("https://config-icon.example.com/icon.png", data.endpointIconUrl)
    }

    @Test
    fun iconURLNullWhenNeitherSet() {
        val convo = Conversation(
            conversationId = "c1",
            endpoint = "OpenRouter",
            iconURL = null,
        )
        val configs = mapOf(
            "OpenRouter" to EndpointConfig(iconURL = null),
        )

        val data = convo.toDisplayData(configs, serverUrl = "")

        assertNull(data.endpointIconUrl)
    }

    @Test
    fun iconURLNullWhenEndpointMissingFromConfig() {
        // Cold-start race: configs map empty for that key — composable falls back to glyph.
        val convo = Conversation(
            conversationId = "c1",
            endpoint = "OpenRouter",
            iconURL = null,
        )

        val data = convo.toDisplayData(endpointConfigs = emptyMap(), serverUrl = "")

        assertNull(data.endpointIconUrl)
    }
}
