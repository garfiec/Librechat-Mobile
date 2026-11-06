package com.garfiec.librechat.feature.chat.viewmodel

import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.model.Preset
import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.core.model.config.ModelSpecs
import com.garfiec.librechat.core.ui.components.ModelParameters
import com.garfiec.librechat.feature.chat.util.parametersLaneRanOn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Lane B's spec as `buildAddedConvo` sends it, and the parameters each lane ran on. */
class ComparisonStateTest {

    private val research =
        ModelSpec(name = "research", label = "Research", preset = Preset(endpoint = "openAI", model = "gpt-4o"))

    private fun picked(spec: String) =
        ComparisonState(isEnabled = true, secondaryEndpoint = "openAI", secondaryModel = "gpt-4o", secondarySpec = spec)

    @Test
    fun a_picked_lane_b_spec_moved_to_another_endpoint_is_not_sent() {
        val moved = research.copy(preset = Preset(endpoint = "azureOpenAI", model = "gpt-4o"))

        assertNull(picked("research").laneBSpec(ModelSpecs(list = listOf(moved)), emptyMap()))
    }

    @Test
    fun a_picked_lane_b_spec_the_server_no_longer_serves_is_still_sent() {
        assertEquals("research", picked("research").laneBSpec(ModelSpecs(list = emptyList()), emptyMap()))
    }

    @Test
    fun a_picked_lane_b_spec_on_its_endpoint_is_sent() {
        assertEquals("research", picked("research").laneBSpec(ModelSpecs(list = listOf(research)), emptyMap()))
    }

    @Test
    fun a_kept_lane_keeps_lane_a_instructions_only_where_lane_b_was_sent_them() {
        val laneA = ModelSelectionState(
            choice = ModelChoice.Raw("openAI", "gpt-4.1"),
            modelParameters = ModelParameters.DEFAULT.copy(temperature = 0.2f, customInstructions = "Be brief."),
        )
        val raw = ModelChoice.Raw("anthropic", "claude")

        assertEquals(ModelParameters.DEFAULT.copy(customInstructions = "Be brief."), laneA.parametersLaneRanOn(raw))
        // An agent lane runs its own instructions, and a saved-agent primary gives none.
        assertEquals(ModelParameters.DEFAULT, laneA.parametersLaneRanOn(ModelChoice.Raw(EndpointConstants.AGENTS, "agent_1")))
        val agentA = laneA.copy(choice = ModelChoice.Raw(EndpointConstants.AGENTS, "agent_1"))
        assertEquals(ModelParameters.DEFAULT, agentA.parametersLaneRanOn(raw))
    }
}
