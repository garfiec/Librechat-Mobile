package com.garfiec.librechat.feature.chat.viewmodel

import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.core.model.config.ModelSpecs
import com.garfiec.librechat.core.model.media.ProviderId
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReplyAvatarsStateTest {

    private val server = "https://chat.example"
    private val configs = mapOf("My Router" to EndpointConfig(providerId = "openrouter"))

    private fun state(
        choice: ModelChoice,
        specs: ModelSpecs? = null,
        comparison: ComparisonState = ComparisonState(),
        agents: List<Agent> = emptyList(),
    ) = ChatUiState(
        selection = ModelSelectionState(choice = choice, modelSpecs = specs, endpointConfigs = configs, agents = agents),
        comparisonState = comparison,
    )

    @Test
    fun the_streaming_reply_on_a_custom_endpoint_takes_its_served_provider() {
        val avatars = state(ModelChoice.Raw("My Router", "m")).replyAvatars(server)
        assertEquals(ProviderId.OPENROUTER, avatars.streaming?.provider)
    }

    @Test
    fun the_streaming_reply_on_a_spec_takes_the_spec_icon() {
        val specs = ModelSpecs(list = listOf(ModelSpec(name = "coder", iconURL = "/images/coder.png")))
        val avatars = state(ModelChoice.Spec("coder", "My Router", "m"), specs).replyAvatars(server)
        assertEquals("$server/images/coder.png", avatars.streaming?.imageUrl)
    }

    @Test
    fun the_streaming_reply_on_an_agent_takes_its_avatar() {
        val agents = listOf(Agent(id = "a1", avatar = buildJsonObject { put("filepath", "/images/a1.png") }))
        val avatars = state(ModelChoice.Raw(EndpointConstants.AGENTS, "a1"), agents = agents).replyAvatars(server)
        assertEquals("$server/images/a1.png", avatars.streaming?.imageUrl)
    }

    @Test
    fun lane_b_is_resolved_only_while_compare_is_on() {
        val off = state(ModelChoice.Raw("openAI", "gpt"), comparison = ComparisonState(secondaryEndpoint = "My Router"))
        assertNull(off.replyAvatars(server).laneB)

        val on = off.copy(comparisonState = off.comparisonState.copy(isEnabled = true))
        assertEquals(ProviderId.OPENROUTER, on.replyAvatars(server).laneB?.provider)
    }
}
