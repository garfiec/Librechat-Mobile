package com.garfiec.librechat.feature.chat.viewmodel

import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.ui.components.ModelParameters
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * What the send path actually puts on the wire for an agent, asserted through
 * [ChatRequestBuilder] rather than through [ModelParamPayload].
 *
 * The seam is the point: on the `agents` endpoint `selectedModel` is the AGENT ID, and the
 * registry's per-model rules (the Anthropic prompt-cache filter, Bedrock's variant dispatch,
 * Google's thinking-budget bounds) resolve against whatever `model` they are handed. The options
 * sheet passes the agent's real model, so this builder has to pass it too. Hand it the id and the
 * two halves disagree about which controls exist while each still passes its own tests — the sheet
 * renders a switch whose value the payload then drops.
 */
class ChatRequestBuilderModelParamsTest {

    private val anthropicAgent = Agent(
        id = "agent_abc123",
        provider = "anthropic",
        model = "claude-sonnet-4-5",
    )

    private fun builderFor(state: ChatUiState) = ChatRequestBuilder { state }

    private fun agentState(params: ModelParameters) = ChatUiState(
        selection = ModelSelectionState(
            selectedEndpoint = "agents",
            // What the endpoint really holds here: the agent id, not a model name.
            selectedModel = anthropicAgent.id,
            agents = listOf(anthropicAgent),
            modelParameters = params,
        ),
    )

    @Test
    fun `an agents send resolves per-model rules against the agent's model, not its id`() {
        val params = ModelParameters.DEFAULT.copy(
            dynamicValues = mapOf("promptCache" to "false", "promptCacheTtl" to "1h"),
        )

        val payload = builderFor(agentState(params)).buildModelParams()

        assertNotNull(payload, "prompt-cache settings the user changed must reach the request")
        assertEquals(false, payload["promptCache"]?.jsonPrimitive?.boolean)
        assertEquals("1h", payload["promptCacheTtl"]?.jsonPrimitive?.content)
    }

    @Test
    fun `an unchanged agents selection still sends nothing`() {
        assertEquals(null, builderFor(agentState(ModelParameters.DEFAULT)).buildModelParams())
    }
}
