package com.garfiec.librechat.core.model.code

import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.model.request.ChatRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CodeWorkspaceWireTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = false }

    @Test
    fun decodesTheListProjectedAgentFields() {
        val agent = json.decodeFromString<Agent>(
            """{"id":"a","tools":["execute_code"],"stateful_code_sessions":true,"code_environment_id":"mbp",
               "code_environment_ids":["vm"],"code_workspace_id":"repo","agent_ids":["b"],
               "edges":[{"from":"a","to":["c","d"]}],
               "subagents":{"enabled":true,"agent_ids":["e"],"graphs":[{"agent_ids":["f","g"]}]}}""",
        )
        assertEquals("mbp", agent.codeEnvironmentId)
        assertEquals(listOf("vm"), agent.codeEnvironmentIds)
        assertTrue(CodeWorkspaceAgents.routesCode(agent))
        assertEquals(listOf("b", "a", "c", "d"), CodeWorkspaceAgents.linkedAgentIds(agent))
        assertEquals(listOf("e", "f", "g"), CodeWorkspaceAgents.subagentIds(agent))
    }

    @Test
    fun decodesTheEndpointsConfigAndCapabilityVersions() {
        val endpoint = json.decodeFromString<EndpointConfig>(
            """{"capabilities":["execute_code"],"statefulCodeSessions":{"allowEnvironmentSelection":true,
               "maxEnvironmentChoices":32,"environments":[{"id":"mbp","name":"MBP","type":"attached",
               "configSchema":{"workspaces":{"allowCheckoutSelection":true},"limits":{}},"settings":{}}]}}""",
        )
        val env = endpoint.statefulCodeSessions!!.environments!!.single()
        assertTrue(env.isAttached)
        assertEquals(true, env.configSchema?.workspaces?.allowCheckoutSelection)
        val config = json.decodeFromString<StartupConfig>(
            """{"codeEnvironmentDecisionVersion":1,"codeWorkspaceInheritanceVersion":1}""",
        )
        assertEquals(1, config.codeEnvironmentDecisionVersion)
        assertEquals(1, config.codeWorkspaceInheritanceVersion)
    }

    @Test
    fun decodesASavedDecisionAndEncodesOneOnASend() {
        val conversation = json.decodeFromString<Conversation>(
            """{"conversationId":"c","codeEnvironmentMode":"attached",
               "codeWorkspaces":[{"environmentId":"mbp","workspaceId":"repo","checkout":"isolated"}]}""",
        )
        assertEquals(CodeEnvironmentMode.ATTACHED, conversation.codeEnvironmentMode)
        assertEquals(listOf(CodeWorkspaceSelection("mbp", "repo", "isolated")), conversation.codeWorkspaces)

        val withDecision = json.encodeToJsonElement(
            ChatRequest.serializer(),
            ChatRequest(
                text = "hi",
                parentMessageId = "p",
                endpoint = "agents",
                codeEnvironmentMode = "attached",
                codeWorkspaces = listOf(CodeWorkspaceSelection("mbp", "repo")),
            ),
        ).jsonObject
        assertEquals(
            """[{"environmentId":"mbp","workspaceId":"repo"}]""",
            withDecision["codeWorkspaces"].toString(),
        )
        val without = json.encodeToJsonElement(
            ChatRequest.serializer(),
            ChatRequest(text = "hi", parentMessageId = "p", endpoint = "agents"),
        ).jsonObject
        assertFalse("codeEnvironmentMode" in without)
        assertFalse("codeWorkspaces" in without)
    }
}
