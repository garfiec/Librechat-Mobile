package com.garfiec.librechat.core.model

import com.garfiec.librechat.core.model.reasoning.ReasoningCapabilitiesResponse
import com.garfiec.librechat.core.model.request.ChatRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReasoningOverrideSerializationTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun messageDecodesAnEnumOverride() {
        val message = json.decodeFromString<Message>(
            """{"messageId":"m","conversationId":"c","reasoningOverride":{"key":"effort","value":"high"}}""",
        )
        assertEquals(ReasoningOverride.level("effort", "high"), message.reasoningOverride)
        assertEquals("high", message.reasoningOverride?.enumValue)
    }

    @Test
    fun messageDecodesANumericBudgetAnotherClientSent() {
        val message = json.decodeFromString<Message>(
            """{"messageId":"m","conversationId":"c","reasoningOverride":{"key":"thinkingBudget","value":8192}}""",
        )
        assertEquals(ReasoningOverride("thinkingBudget", JsonPrimitive(8192)), message.reasoningOverride)
        assertNull(message.reasoningOverride?.enumValue)
    }

    @Test
    fun requestSendsTheOverrideAtTopLevelAndOmitsItWhenAbsent() {
        val withOverride = json.encodeToJsonElement(
            ChatRequest.serializer(),
            ChatRequest(
                text = "hi",
                parentMessageId = "p",
                endpoint = "openAI",
                reasoningOverride = ReasoningOverride.level("reasoning_effort", "low"),
            ),
        ).jsonObject
        assertEquals(
            """{"key":"reasoning_effort","value":"low"}""",
            withOverride["reasoningOverride"].toString(),
        )
        val without = json.encodeToJsonElement(
            ChatRequest.serializer(),
            ChatRequest(text = "hi", parentMessageId = "p", endpoint = "openAI"),
        ).jsonObject
        assertNull(without["reasoningOverride"])
    }

    @Test
    fun capabilitiesDecode() {
        val response = json.decodeFromString<ReasoningCapabilitiesResponse>(
            """{"capabilities":{"OpenRouter":{"openai/gpt-5":{"efforts":["low","high"],"mandatory":true},""" +
                """"meta/llama":{"efforts":[]}}},"expiresInMs":300000}""",
        )
        val models = response.capabilities.getValue("OpenRouter")
        assertEquals(listOf("low", "high"), models.getValue("openai/gpt-5").efforts)
        assertEquals(true, models.getValue("openai/gpt-5").mandatory)
        assertEquals(emptyList(), models.getValue("meta/llama").efforts)
        assertEquals(300_000L, response.expiresInMs)
    }
}
