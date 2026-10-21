package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.Preset
import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.core.model.config.ModelSpecs
import com.garfiec.librechat.core.model.endpoint.KeyState
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CompareQuickPickTest {

    private val models = mapOf(
        "anthropic" to listOf("claude-opus-4-5", "claude-sonnet-4-5"),
        "openAI" to listOf("gpt-5", "gpt-5-mini"),
    )
    private val configs = mapOf(
        "anthropic" to EndpointConfig(modelDisplayLabel = "Anthropic"),
        "openAI" to EndpointConfig(),
    )
    private val agents = listOf(Agent(id = "agent_1", name = "Research helper"))

    private fun picks(
        query: String = "",
        favoriteModelKeys: Set<String> = emptySet(),
        favoriteAgentIds: Set<String> = emptySet(),
    ) = compareQuickPicks(query, models, configs, agents, favoriteModelKeys, favoriteAgentIds, agentCaption = "Agent")

    @Test
    fun `starred come first, agents then models, in pin order`() {
        val result = picks(
            favoriteModelKeys = setOf("openAI::gpt-5", "anthropic::claude-opus-4-5"),
            favoriteAgentIds = setOf("agent_1"),
        )

        assertThat(result.map { it.name }).containsExactly("Research helper", "gpt-5", "claude-opus-4-5").inOrder()
        assertThat(result.all { it.starred }).isTrue()
    }

    @Test
    fun `a star the server no longer offers is skipped`() {
        val result = picks(favoriteModelKeys = setOf("openAI::gpt-4", "openAI::gpt-5"))

        assertThat(result.map { it.model }).containsExactly("gpt-5")
    }

    @Test
    fun `with nothing starred the pane still offers models`() {
        val result = picks()

        assertThat(result).isNotEmpty()
        assertThat(result.first().name).isEqualTo("claude-opus-4-5")
        assertThat(result.first().source).isEqualTo("Anthropic")
    }

    @Test
    fun `a query searches every model and agent by name`() {
        val result = picks(query = "  MINI ")

        assertThat(result.map { it.model }).containsExactly("gpt-5-mini")
        assertThat(result.single().source).isEqualTo("openAI")
    }

    @Test
    fun `a query matches the way the full selector does`() {
        val result = picks(query = "opus45")

        assertThat(result.map { it.model }).contains("claude-opus-4-5")
    }

    @Test
    fun `an agents-only server with nothing starred still offers its agents`() {
        val result = compareQuickPicks(
            query = "",
            availableModels = emptyMap(),
            endpointConfigs = emptyMap(),
            agents = agents,
            favoriteModelKeys = emptySet(),
            favoriteAgentIds = emptySet(),
            agentCaption = "Agent",
        )

        assertThat(result.map { it.model }).containsExactly("agent_1")
    }

    @Test
    fun `a trailing space searches the same as without it`() {
        assertThat(searchByName(listOf("gpt-5", "claude-opus-4-5"), "gp ") { it })
            .isEqualTo(searchByName(listOf("gpt-5", "claude-opus-4-5"), "gp") { it })
    }

    @Test
    fun `an exact model outranks a list full of weaker agent matches`() {
        val weakAgents = (1..8).map { Agent(id = "agent_$it", name = "gpt-5 helper number $it") }

        val result = compareQuickPicks(
            query = "gpt-5-mini",
            availableModels = models,
            endpointConfigs = configs,
            agents = weakAgents,
            favoriteModelKeys = emptySet(),
            favoriteAgentIds = emptySet(),
            agentCaption = "Agent",
        )

        assertThat(result.map { it.model }).contains("gpt-5-mini")
        assertThat(result.first().source).isEqualTo("openAI")
    }

    @Test
    fun `an endpoint waiting on the user's key is left out`() {
        val result = compareQuickPicks(
            query = "",
            availableModels = models,
            endpointConfigs = configs,
            agents = agents,
            favoriteModelKeys = setOf("anthropic::claude-opus-4-5", "openAI::gpt-5"),
            favoriteAgentIds = emptySet(),
            agentCaption = "Agent",
            endpointKeyStates = mapOf("anthropic" to KeyState.Unset),
        )

        assertThat(result.map { it.model }).containsExactly("gpt-5")
    }

    @Test
    fun `a query matches agents by name and labels them as agents`() {
        val result = picks(query = "research")

        assertThat(result.single().model).isEqualTo("agent_1")
        assertThat(result.single().source).isEqualTo("Agent")
    }

    private val fast =
        ModelSpec(name = "fast", label = "Fast", preset = Preset(endpoint = "openAI", model = "gpt-5-mini"))

    @Test
    fun `spec rows lead the list and carry their name`() {
        val specs = ModelSpecs(list = listOf(fast))
        val result = compareQuickPicks(
            "", models, configs, agents, emptySet(), emptySet(), agentCaption = "Agent",
            specs = specs.list, modelSpecs = specs,
        )

        assertThat(result.first().name).isEqualTo("Fast")
        assertThat(result.first().spec).isEqualTo("fast")
        assertThat(result.drop(1).none { it.spec != null }).isTrue()
    }

    @Test
    fun `under enforce only spec rows are offered`() {
        val specs = ModelSpecs(list = listOf(fast), enforce = true)
        val result = compareQuickPicks(
            "", models, configs, agents, setOf("openAI::gpt-5"), setOf("agent_1"), agentCaption = "Agent",
            specs = specs.list, modelSpecs = specs,
        )

        assertThat(result.map { it.spec }).containsExactly("fast")
    }
}
