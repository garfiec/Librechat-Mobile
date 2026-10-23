package com.garfiec.librechat.core.model.config

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/** A spec's `conversation_starters`, which `GET /api/config` passes through `sanitizeModelSpecs` untouched. */
class ModelSpecConversationStartersTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun startersDecodeInOrder() {
        val spec = json.decodeFromString(
            ModelSpec.serializer(),
            """{"name":"s","conversation_starters":["Plan my week","Explain this"]}""",
        )
        assertEquals(listOf("Plan my week", "Explain this"), spec.conversationStarters)
    }

    @Test
    fun anAbsentKeyIsNoStarters() {
        assertEquals(emptyList(), json.decodeFromString(ModelSpec.serializer(), """{"name":"s"}""").conversationStarters)
    }
}
