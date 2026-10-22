package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.model.request.ChatRequest
import com.garfiec.librechat.core.model.request.EphemeralAgent
import com.garfiec.librechat.core.model.request.MAX_MANUAL_SKILLS
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatPayloadManualSkillsTest {

    private val json = Json { encodeDefaults = false; explicitNulls = false }

    private fun body(request: ChatRequest) = json.encodeToJsonElement(ChatRequest.serializer(), request).jsonObject

    @Test
    fun manualSkillsRideAtTheTopLevelAndForceTheEphemeralFlag() {
        val req = ChatPayloadBuilder.build(
            text = "hi", conversationId = "c-1", endpoint = "openAI", model = "gpt-4o",
            manualSkills = listOf("pdf-tools", "brand-voice"),
        )
        val wire = body(req)
        assertEquals(
            listOf("pdf-tools", "brand-voice"),
            (wire["manualSkills"] as JsonArray).map { it.jsonPrimitive.content },
        )
        // Without the flag an ephemeral run is scoped to no skills and the picks are never primed.
        assertEquals("true", wire["ephemeralAgent"]!!.jsonObject["skills"]!!.jsonPrimitive.content)
    }

    @Test
    fun forcingTheFlagKeepsTheOtherEphemeralTools() {
        val req = ChatPayloadBuilder.build(
            text = "hi", conversationId = "c-1", endpoint = "openAI", model = "gpt-4o",
            ephemeralAgent = EphemeralAgent(webSearch = true, mcp = listOf("files")),
            manualSkills = listOf("pdf-tools"),
        )
        assertEquals(EphemeralAgent(webSearch = true, mcp = listOf("files"), skills = true), req.ephemeralAgent)
    }

    @Test
    fun aPersistedAgentGetsTheNamesButNoEphemeralAgent() {
        val req = ChatPayloadBuilder.build(
            text = "hi", conversationId = "c-1", endpoint = "agents", model = "agent_1", agentId = "agent_1",
            manualSkills = listOf("pdf-tools"),
        )
        assertEquals(listOf("pdf-tools"), req.manualSkills)
        assertNull(req.ephemeralAgent)
    }

    @Test
    fun assistantsEndpointsTakeNoManualSkills() {
        for (endpoint in listOf("assistants", "azureAssistants")) {
            val req = ChatPayloadBuilder.build(
                text = "hi", conversationId = "c-1", endpoint = endpoint, model = "gpt-4o",
                manualSkills = listOf("pdf-tools"),
            )
            assertNull(req.manualSkills, endpoint)
            assertNull(req.ephemeralAgent, endpoint)
            assertFalse("manualSkills" in body(req), endpoint)
        }
    }

    @Test
    fun namesAreDedupedAndCappedLikeTheServer() {
        val names = listOf("a", "a", "") + (1..12).map { "skill-$it" }
        val req = ChatPayloadBuilder.build(
            text = "hi", conversationId = "c-1", endpoint = "openAI", model = "gpt-4o", manualSkills = names,
        )
        val sent = req.manualSkills!!
        assertEquals(MAX_MANUAL_SKILLS, sent.size)
        assertEquals("a", sent.first())
        assertEquals(sent.distinct(), sent)
        assertFalse("" in sent)
    }

    @Test
    fun noPicksSendsNoFieldAndLeavesTheEphemeralAgentAlone() {
        val req = ChatPayloadBuilder.build(
            text = "hi", conversationId = "c-1", endpoint = "openAI", model = "gpt-4o", manualSkills = emptyList(),
        )
        assertNull(req.manualSkills)
        assertNull(req.ephemeralAgent)
        assertFalse("manualSkills" in body(req))
    }

    @Test
    fun theToggleAloneSendsTheFlagWithoutNames() {
        val req = ChatPayloadBuilder.build(
            text = "hi", conversationId = "c-1", endpoint = "openAI", model = "gpt-4o",
            ephemeralAgent = EphemeralAgent(skills = true),
        )
        assertTrue(req.ephemeralAgent?.skills == true)
        assertFalse("manualSkills" in body(req))
    }
}
