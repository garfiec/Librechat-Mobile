package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.model.request.AddedConversation
import com.garfiec.librechat.core.model.request.ChatRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatPayloadBuilderTest {

    private val json = Json { encodeDefaults = false }

    @Test
    fun customEndpointWireFormatNeverFallsBackToAgents() {
        val req = ChatPayloadBuilder.build(
            text = "hello",
            conversationId = null,
            endpoint = "Deepseek",
            endpointType = "custom",
            key = "never",
            model = "deepseek-chat",
        )

        assertEquals("Deepseek", req.endpoint)
        assertEquals("custom", req.endpointType)
        assertEquals("never", req.key)

        val encoded = json.encodeToString(ChatRequest.serializer(), req)
        assertTrue("\"endpoint\":\"Deepseek\"" in encoded, "endpoint must be literal Deepseek")
        assertFalse("\"endpoint\":\"agents\"" in encoded, "endpoint must NOT fall back to agents")
    }

    @Test
    fun projectIdRidesOnlyWhenGiven() {
        val filed = ChatPayloadBuilder.build(
            text = "hi", conversationId = null, endpoint = "openAI", model = "gpt-4o", chatProjectId = "proj-1",
        )
        assertTrue("\"chatProjectId\":\"proj-1\"" in json.encodeToString(ChatRequest.serializer(), filed))

        // Absent, not null: on a later turn a present field would move the chat.
        val plain = ChatPayloadBuilder.build(text = "hi", conversationId = "c-1", endpoint = "openAI", model = "gpt-4o")
        assertFalse("chatProjectId" in Json { explicitNulls = false }.encodeToString(ChatRequest.serializer(), plain))
    }

    @Test
    fun secondModelSettingsRideFlatAndOnlyWhenSet() {
        val wire = Json { encodeDefaults = false; explicitNulls = false }
        val withSpec = ChatPayloadBuilder.build(
            text = "hi", conversationId = "c-1", endpoint = "openAI", model = "gpt-4o",
            addedConvo = AddedConversation(
                endpoint = "anthropic", model = "claude", spec = "research",
                temperature = 0.2, topP = 0.9, maxTokens = 512,
            ),
        )
        val added = wire.encodeToJsonElement(ChatRequest.serializer(), withSpec).jsonObject["addedConvo"]!!.jsonObject
        // The names `added.ts` copies, at the top level of the added convo.
        assertEquals(0.2, added["temperature"]?.jsonPrimitive?.double)
        assertEquals(0.9, added["top_p"]?.jsonPrimitive?.double)
        assertEquals(512, added["max_tokens"]?.jsonPrimitive?.int)

        val raw = ChatPayloadBuilder.build(
            text = "hi", conversationId = "c-1", endpoint = "openAI", model = "gpt-4o",
            addedConvo = AddedConversation(endpoint = "anthropic", model = "claude"),
        )
        val rawAdded = wire.encodeToJsonElement(ChatRequest.serializer(), raw).jsonObject["addedConvo"]!!.jsonObject
        assertEquals(setOf("endpoint", "model"), rawAdded.keys)
    }

    @Test
    fun builtInUserProvidedEndpointSendsKey() {
        val req = ChatPayloadBuilder.build(
            text = "hi",
            conversationId = null,
            endpoint = "openAI",
            endpointType = "openAI",
            key = "never",
            model = "gpt-4o",
        )
        assertEquals("openAI", req.endpoint)
        assertEquals("openAI", req.endpointType)
        assertEquals("never", req.key)
    }

    @Test
    fun temporaryChatFlagOmittedByDefault() {
        val req = ChatPayloadBuilder.build(
            text = "hi",
            conversationId = null,
            endpoint = "openAI",
            model = "gpt-4o",
        )
        assertNull(req.isTemporary)
        val encoded = json.encodeToString(ChatRequest.serializer(), req)
        assertFalse("\"isTemporary\"" in encoded, "isTemporary must be omitted when not temporary")
    }

    @Test
    fun temporaryChatFlagSentWhenEnabled() {
        val req = ChatPayloadBuilder.build(
            text = "hi",
            conversationId = null,
            endpoint = "openAI",
            model = "gpt-4o",
            isTemporary = true,
        )
        assertEquals(true, req.isTemporary)
        val encoded = json.encodeToString(ChatRequest.serializer(), req)
        assertTrue("\"isTemporary\":true" in encoded, "isTemporary must serialize true when set")
    }

    @Test
    fun builtInEnvKeyEndpointOmitsKey() {
        val req = ChatPayloadBuilder.build(
            text = "hi",
            conversationId = null,
            endpoint = "anthropic",
            endpointType = "anthropic",
            key = null,
            model = "claude-sonnet-4-6",
        )
        assertEquals("anthropic", req.endpoint)
        assertNull(req.key)

        val encoded = json.encodeToString(ChatRequest.serializer(), req)
        assertFalse("\"key\"" in encoded, "key must be omitted (not encoded as null) when null")
    }

    @Test
    fun userMessageIdSerializedAsTopLevelMessageId() {
        val req = ChatPayloadBuilder.build(
            text = "hi",
            conversationId = null,
            endpoint = "openAI",
            model = "gpt-4o",
            userMessageId = "client-minted-123",
        )
        assertEquals("client-minted-123", req.messageId)
        val encoded = json.encodeToString(ChatRequest.serializer(), req)
        assertTrue("\"messageId\":\"client-minted-123\"" in encoded, "messageId must serialize when set")
    }

    @Test
    fun messageIdOmittedWhenNotProvided() {
        val req = ChatPayloadBuilder.build(
            text = "hi",
            conversationId = null,
            endpoint = "openAI",
            model = "gpt-4o",
        )
        assertNull(req.messageId)
        val encoded = json.encodeToString(ChatRequest.serializer(), req)
        assertFalse("\"messageId\"" in encoded, "messageId must be omitted when not provided (regenerate/continue)")
    }

    @Test
    fun selected_spec_is_serialized_for_an_enforced_server() {
        val request = ChatPayloadBuilder.build(
            text = "hello",
            conversationId = null,
            endpoint = "custom",
            model = "shared-model",
            spec = "curated-spec",
        )
        val body = ChatPayloadBuilder.toBody(json, request, null)
        assertEquals("curated-spec", body["spec"]?.toString()?.trim('"'))
        val raw = request.copy(spec = null)
        assertFalse("spec" in ChatPayloadBuilder.toBody(json, raw, null))
    }
}
