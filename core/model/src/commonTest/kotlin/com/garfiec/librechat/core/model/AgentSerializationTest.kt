package com.garfiec.librechat.core.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AgentSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun minimalAgentRoundTrip() {
        val original = Agent(id = "agent-001")
        val encoded = json.encodeToString(Agent.serializer(), original)
        val decoded = json.decodeFromString(Agent.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun agentWithStringAvatarRoundTrip() {
        val original = Agent(
            id = "agent-002",
            name = "Test Agent",
            avatar = JsonPrimitive("https://example.com/avatar.png"),
        )
        val encoded = json.encodeToString(Agent.serializer(), original)
        val decoded = json.decodeFromString(Agent.serializer(), encoded)
        assertEquals(original, decoded)
        assertEquals("https://example.com/avatar.png", decoded.avatarUrl)
    }

    @Test
    fun agentWithObjectAvatarRoundTrip() {
        val avatarObj = buildJsonObject {
            put("filepath", "/images/agent.png")
            put("source", "upload")
        }
        val original = Agent(
            id = "agent-003",
            name = "Object Avatar Agent",
            avatar = avatarObj,
        )
        val encoded = json.encodeToString(Agent.serializer(), original)
        val decoded = json.decodeFromString(Agent.serializer(), encoded)
        assertEquals(original, decoded)
        assertEquals("/images/agent.png", decoded.avatarUrl)
    }

    @Test
    fun agentAvatarUrlReturnsNullForNonHttpString() {
        val original = Agent(
            id = "agent-004",
            avatar = JsonPrimitive("not-a-url"),
        )
        assertNull(original.avatarUrl)
    }

    @Test
    fun fullyPopulatedAgentRoundTrip() {
        val original = Agent(
            id = "agent-005",
            mongoId = "65abc123",
            name = "Full Agent",
            description = "A test agent",
            instructions = "Be helpful",
            avatar = JsonPrimitive("https://example.com/agent.png"),
            provider = "openAI",
            model = "gpt-4o",
            modelParameters = buildJsonObject {
                put("temperature", 0.7)
                put("max_tokens", 4096)
            },
            artifacts = "true",
            accessLevel = 2,
            recursionLimit = 25,
            hideSequentialOutputs = true,
            endAfterTools = false,
            category = "productivity",
            author = "user-123",
            authorName = "Test User",
            isPromoted = true,
            isPublic = true,
            conversationStarters = listOf("Hello!", "What can you do?"),
            tools = listOf("web_search", "code_interpreter"),
            actions = listOf("action-1"),
            agentIds = listOf("sub-agent-1"),
            isCollaborative = false,
            projectIds = listOf("proj-1"),
            updatedAt = "2026-03-28T12:00:00Z",
            createdAt = "2026-03-28T10:00:00Z",
            supportContact = buildJsonObject {
                put("name", "Support")
                put("email", "support@example.com")
            },
            toolOptions = JsonObject(mapOf("web_search" to JsonPrimitive(true))),
            mcpServerNames = listOf("mcp-1"),
        )
        val encoded = json.encodeToString(Agent.serializer(), original)
        val decoded = json.decodeFromString(Agent.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun agentDeserializesSerialNames() {
        val serverJson = """
            {
                "id": "agent-srv",
                "_id": "mongo-id",
                "model_parameters": {"temperature": 0.5},
                "access_level": 1,
                "recursion_limit": 10,
                "hide_sequential_outputs": false,
                "end_after_tools": true,
                "is_promoted": false,
                "conversation_starters": ["Hi"],
                "agent_ids": [],
                "support_contact": null,
                "tool_options": null
            }
        """.trimIndent()
        val decoded = json.decodeFromString(Agent.serializer(), serverJson)
        assertEquals("agent-srv", decoded.id)
        assertEquals("mongo-id", decoded.mongoId)
        assertEquals(1, decoded.accessLevel)
        assertEquals(10, decoded.recursionLimit)
        assertEquals(false, decoded.hideSequentialOutputs)
        assertEquals(true, decoded.endAfterTools)
        assertEquals(listOf("Hi"), decoded.conversationStarters)
    }

    @Test
    fun webSearchToolAuthKeepsWhichKeysAreTheUsers() {
        val body = """{"authenticated":false,"authTypes":[["providers","user_provided"],""" +
            """["scrapers","user_provided"],["rerankers","system_defined"]],"rerankerType":"none"}"""

        val decoded = json.decodeFromString(ToolAuthResult.serializer(), body)

        assertEquals(false, decoded.authenticated)
        assertEquals(listOf("providers", "scrapers"), decoded.userProvidedCategories)
        assertEquals("none", decoded.rerankerType)
        assertNull(decoded.searchProvider)
        assertNull(decoded.scraperProvider)
    }
}
