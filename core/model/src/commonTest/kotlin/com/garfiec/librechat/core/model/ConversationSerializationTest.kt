package com.garfiec.librechat.core.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class ConversationSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun minimalConversationRoundTrip() {
        val original = Conversation(
            conversationId = "conv-001",
            title = "Test Chat",
        )
        val encoded = json.encodeToString(Conversation.serializer(), original)
        val decoded = json.decodeFromString(Conversation.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun fullyPopulatedConversationRoundTrip() {
        val original = Conversation(
            conversationId = "conv-002",
            title = "Full Chat",
            user = "user-123",
            endpoint = "openAI",
            endpointType = "agents",
            model = "gpt-4o",
            agentId = "agent-abc",
            assistantId = "asst-def",
            tags = listOf("work", "important"),
            isArchived = true,
            temperature = 0.7,
            topPSnake = 0.9,
            topPCamel = 0.8,
            topK = 40,
            frequencyPenalty = 0.5,
            presencePenalty = 0.3,
            maxOutputTokens = 4096,
            maxContextTokens = 128000,
            maxTokensSnake = 4096,
            maxTokensCamel = 2048,
            system = "You are a helpful assistant.",
            reasoningEffort = "high",
            effort = "high",
            thinkingLevel = "extended",
            stop = listOf("\n\n", "END"),
            iconURL = "https://example.com/icon.png",
            greeting = "Hello!",
            spec = "openAI",
            tools = listOf("web_search", "code_interpreter"),
            webSearch = true,
            createdAt = Instant.parse("2026-03-28T10:00:00Z"),
            updatedAt = Instant.parse("2026-03-28T11:00:00Z"),
        )
        val encoded = json.encodeToString(Conversation.serializer(), original)
        val decoded = json.decodeFromString(Conversation.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun conversationWithDefaultsRoundTrip() {
        val original = Conversation()
        val encoded = json.encodeToString(Conversation.serializer(), original)
        val decoded = json.decodeFromString(Conversation.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun conversationDeserializesSerialNames() {
        val serverJson = """
            {
                "conversationId": "conv-srv",
                "agent_id": "agent-1",
                "assistant_id": "asst-1",
                "top_p": 0.95,
                "frequency_penalty": 0.2,
                "presence_penalty": 0.1,
                "reasoning_effort": "medium",
                "web_search": false,
                "thinkingLevel": "basic"
            }
        """.trimIndent()
        val decoded = json.decodeFromString(Conversation.serializer(), serverJson)
        assertEquals("agent-1", decoded.agentId)
        assertEquals("asst-1", decoded.assistantId)
        assertEquals(0.95, decoded.topPSnake)
        assertEquals(0.2, decoded.frequencyPenalty)
        assertEquals(0.1, decoded.presencePenalty)
        assertEquals("medium", decoded.reasoningEffort)
        assertEquals(false, decoded.webSearch)
        assertEquals("basic", decoded.thinkingLevel)
    }

    /**
     * Upstream spells some keys per provider (`tConversationSchema`), and each provider's schema keeps
     * only its own: a conversation reopened without its spelling sends the default and the server
     * unsets the stored value.
     */
    @Test
    fun everyProviderSpellingOfTopPAndMaxTokensDecodes() {
        val decoded = json.decodeFromString(
            Conversation.serializer(),
            """{"top_p": 0.1, "topP": 0.2, "max_tokens": 100, "maxTokens": 200, "maxOutputTokens": 300}""",
        )
        assertEquals(0.1, decoded.topPSnake)
        assertEquals(0.2, decoded.topPCamel)
        assertEquals(100, decoded.maxTokensSnake)
        assertEquals(200, decoded.maxTokensCamel)
        assertEquals(300, decoded.maxOutputTokens)

        // The cached blob is a Preset: every spelling survives the round trip through it.
        val restored = Conversation().withParameters(decoded.parameterPreset())
        assertEquals(0.1, restored.topPSnake)
        assertEquals(0.2, restored.topPCamel)
        assertEquals(100, restored.maxTokensSnake)
        assertEquals(200, restored.maxTokensCamel)
        assertEquals(300, restored.maxOutputTokens)
    }

    @Test
    fun presetEncodesEachSpellingUnderItsOwnKey() {
        val encoded = json.encodeToString(
            Preset.serializer(),
            Preset(topPSnake = 0.1, topPCamel = 0.2, maxTokensSnake = 100, maxTokensCamel = 200, appendCurrentDatetime = true),
        )
        listOf("\"top_p\":0.1", "\"topP\":0.2", "\"max_tokens\":100", "\"maxTokens\":200", "\"append_current_datetime\":true")
            .forEach { assertEquals(true, encoded.contains(it), "Expected $it in $encoded") }
    }

    @Test
    fun endpointEnumSerializesToJsonString() {
        val original = Conversation(endpoint = "anthropic")
        val encoded = json.encodeToString(Conversation.serializer(), original)
        assertEquals(true, encoded.contains("\"anthropic\""), "Expected serialized name 'anthropic' in $encoded")
        val decoded = json.decodeFromString(Conversation.serializer(), encoded)
        assertEquals("anthropic", decoded.endpoint)
    }

    @Test
    fun conversationWithCustomEndpointRoundTrip() {
        val original = Conversation(
            conversationId = "conv-custom",
            endpoint = "OpenRouter",
            endpointType = "custom",
            model = "meta-llama/llama-3.3-70b-instruct:free",
        )
        val encoded = json.encodeToString(Conversation.serializer(), original)
        assertEquals(true, encoded.contains("\"OpenRouter\""), "Expected literal 'OpenRouter' in $encoded")
        assertEquals(true, encoded.contains("\"custom\""), "Expected 'custom' endpointType in $encoded")
        val decoded = json.decodeFromString(Conversation.serializer(), encoded)
        assertEquals("OpenRouter", decoded.endpoint)
        assertEquals("custom", decoded.endpointType)
    }

    @Test
    fun archivedAtDecodesAndIsNullWhenUnarchived() {
        // v0.8.8-rc2. The server nulls the stamp on unarchive and omits it entirely on older
        // servers, so both spellings of "not archived" have to reach the same value.
        val archived = json.decodeFromString(
            Conversation.serializer(),
            """{"conversationId":"c1","isArchived":true,"archivedAt":"2026-09-01T12:00:00.000Z"}""",
        )
        assertEquals(Instant.parse("2026-09-01T12:00:00.000Z"), archived.archivedAt)

        val explicitNull = json.decodeFromString(
            Conversation.serializer(),
            """{"conversationId":"c1","isArchived":false,"archivedAt":null}""",
        )
        assertEquals(null, explicitNull.archivedAt)

        val omitted = json.decodeFromString(Conversation.serializer(), """{"conversationId":"c1"}""")
        assertEquals(null, omitted.archivedAt)
    }
}
