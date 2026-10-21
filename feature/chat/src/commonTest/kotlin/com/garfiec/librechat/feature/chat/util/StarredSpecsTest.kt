package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.Preset
import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.core.model.config.ModelSpecs
import kotlin.test.Test
import kotlin.test.assertEquals

class StarredSpecsTest {

    private fun model(name: String, order: Double? = null, showInMenu: Boolean? = null) =
        ModelSpec(name = name, preset = Preset(endpoint = "openAI", model = "gpt-5"), order = order, showInMenu = showInMenu)

    private fun agent(name: String, agentId: String) = ModelSpec(name = name, preset = Preset(agentId = agentId))

    private fun starred(favorites: Set<String>, specs: ModelSpecs, agents: List<Agent> = emptyList(), agentsLoaded: Boolean = false) =
        starredSpecs(favorites, specs.visibleSpecs(agents, agentsLoaded)).map { it.name }

    @Test
    fun keepsPinOrderNotTheConfigOrder() {
        val specs = ModelSpecs(list = listOf(model("a", order = 1.0), model("b", order = 2.0), model("c", order = 3.0)))

        assertEquals(listOf("c", "a"), starred(linkedSetOf("c", "a"), specs))
    }

    @Test
    fun aPinTheConfigNoLongerHasShowsNothing() {
        val specs = ModelSpecs(list = listOf(model("a")))

        assertEquals(listOf("a"), starred(linkedSetOf("gone", "a"), specs))
    }

    @Test
    fun aSpecHiddenFromTheMenuShowsNothing() {
        val specs = ModelSpecs(list = listOf(model("hidden", showInMenu = false), model("shown")))

        assertEquals(listOf("shown"), starred(linkedSetOf("hidden", "shown"), specs))
    }

    @Test
    fun anAgentSpecTheUserCannotReachShowsNothing() {
        val specs = ModelSpecs(list = listOf(agent("mine", "agent_1"), agent("theirs", "agent_2")))
        val agents = listOf(Agent(id = "agent_1"))

        assertEquals(listOf("mine"), starred(linkedSetOf("theirs", "mine"), specs, agents, agentsLoaded = true))
        // Until the agents list loads, every agent spec shows, as in the spec block.
        assertEquals(listOf("theirs", "mine"), starred(linkedSetOf("theirs", "mine"), specs, agents, agentsLoaded = false))
    }

    @Test
    fun nothingPinnedIsEmpty() {
        assertEquals(emptyList(), starred(emptySet(), ModelSpecs(list = listOf(model("a")))))
    }
}
