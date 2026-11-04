package com.garfiec.librechat.feature.agents.viewmodel

import com.garfiec.librechat.core.model.Agent
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Test

/**
 * Only handoff edges reach the handoff editor (upstream `isHandoffEdge`, #16744). A direct edge, or
 * the SDK's default-direct untyped one-to-many edge, is kept raw and sent back untouched.
 */
class AgentEditorHandoffEdgeTest {

    private fun edge(json: String): JsonElement = Json.parseToJsonElement(json)

    private val handoff = edge("""{"from":"agent_a","to":"agent_b","edgeType":"handoff"}""")
    private val untypedSingle = edge("""{"from":"agent_a","to":"agent_c"}""")
    private val direct = edge("""{"from":"agent_a","to":"agent_d","edgeType":"direct"}""")
    private val defaultDirect = edge("""{"from":"agent_a","to":["agent_e","agent_f"]}""")

    @Test
    fun `classification follows the SDK`() {
        assertThat(isHandoffEdge(handoff)).isTrue()
        assertThat(isHandoffEdge(untypedSingle)).isTrue()
        assertThat(isHandoffEdge(direct)).isFalse()
        assertThat(isHandoffEdge(defaultDirect)).isFalse()
        // A typed one-to-many edge is whatever its type says.
        assertThat(isHandoffEdge(edge("""{"from":"a","to":["b","c"],"edgeType":"handoff"}"""))).isTrue()
        // So is a conditional one: the default applies only to unconditional edges.
        assertThat(isHandoffEdge(edge("""{"from":"a","to":["b","c"],"condition":"x"}"""))).isTrue()
    }

    @Test
    fun `direct edges stay out of the editor and survive a save`() {
        val agent = Agent(id = "agent_a", edges = listOf(handoff, direct, untypedSingle, defaultDirect))

        val state = AgentEditorUiState().applyAgentData(agent)

        assertThat(state.handoffEdges.map { it.to.toString() }).containsExactly("\"agent_b\"", "\"agent_c\"").inOrder()
        assertThat(state.unparsedHandoffEdges).containsExactly(direct, defaultDirect).inOrder()
        val sent = state.toUpdateAgentRequest().edges.orEmpty()
        assertThat(sent).contains(direct)
        assertThat(sent).contains(defaultDirect)
        assertThat(sent).hasSize(4)
    }
}
