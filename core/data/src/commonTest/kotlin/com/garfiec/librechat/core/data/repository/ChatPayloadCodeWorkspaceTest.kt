package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.model.code.CodeEnvironmentMode
import com.garfiec.librechat.core.model.code.CodeWorkspaceSelection
import com.garfiec.librechat.core.model.code.CodeWorkspaceSubmission
import com.garfiec.librechat.core.model.request.ChatRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ChatPayloadCodeWorkspaceTest {
    private val json = Json { encodeDefaults = false; explicitNulls = false }

    private fun body(request: ChatRequest) = json.encodeToJsonElement(ChatRequest.serializer(), request).jsonObject

    private val attached = CodeWorkspaceSubmission(
        mode = CodeEnvironmentMode.ATTACHED,
        workspaces = listOf(CodeWorkspaceSelection("garfie-mbp", "switchboard", agentIds = listOf("agent_dev"))),
    )

    @Test
    fun anAgentsSendCarriesTheDecision() {
        val wire = body(
            ChatPayloadBuilder.build(
                text = "hi", conversationId = null, endpoint = "agents", model = null, agentId = "agent_dev",
                codeWorkspace = attached,
            ),
        )
        assertEquals("attached", wire["codeEnvironmentMode"]!!.jsonPrimitive.content)
        assertEquals(
            """[{"environmentId":"garfie-mbp","workspaceId":"switchboard","agentIds":["agent_dev"]}]""",
            wire["codeWorkspaces"].toString(),
        )
    }

    @Test
    fun noWorkspaceSendsTheModeAlone() {
        val wire = body(
            ChatPayloadBuilder.build(
                text = "hi", conversationId = null, endpoint = "agents", model = null,
                codeWorkspace = CodeWorkspaceSubmission(mode = CodeEnvironmentMode.WITHOUT_ATTACHED),
            ),
        )
        assertEquals("without_attached", wire["codeEnvironmentMode"]!!.jsonPrimitive.content)
        assertFalse("codeWorkspaces" in wire)
    }

    @Test
    fun anotherEndpointOrNoDecisionSendsNeither() {
        for (request in listOf(
            ChatPayloadBuilder.build(text = "hi", conversationId = null, endpoint = "openAI", model = "gpt", codeWorkspace = attached),
            ChatPayloadBuilder.build(text = "hi", conversationId = null, endpoint = "agents", model = null),
            ChatPayloadBuilder.build(
                text = "hi", conversationId = "c", endpoint = "agents", model = null,
                codeWorkspace = CodeWorkspaceSubmission.NONE,
            ),
        )) {
            val wire = body(request)
            assertFalse("codeEnvironmentMode" in wire)
            assertFalse("codeWorkspaces" in wire)
        }
    }

    @Test
    fun theApprovalModeRidesBesideAHeldDecision() {
        val wire = body(
            ChatPayloadBuilder.build(
                text = "hi", conversationId = "c", endpoint = "agents", model = null,
                codeWorkspace = CodeWorkspaceSubmission(approvalMode = "acceptEdits"),
            ),
        )
        assertEquals("acceptEdits", wire["codeApprovalMode"]!!.jsonPrimitive.content)
        assertFalse("codeEnvironmentMode" in wire)
    }
}
