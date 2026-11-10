package com.garfiec.librechat.feature.chat.viewmodel

import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.Preset
import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.core.model.config.ModelSpecs
import com.garfiec.librechat.core.model.media.ProviderId
import com.garfiec.librechat.feature.chat.util.replyAvatar
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
    fun the_streaming_reply_on_a_spec_draws_what_its_saved_reply_will() {
        val specs = ModelSpecs(list = listOf(ModelSpec(name = "fast", iconURL = "groq")))
        val avatars = state(ModelChoice.Spec("fast", "My Router", "m"), specs).replyAvatars(server)
        val saved = Message(messageId = "r", conversationId = "c", endpoint = "My Router", iconURL = "groq")
        assertEquals(saved.replyAvatar(null, server, configs), avatars.streaming)
        assertEquals(ProviderId.OPENROUTER, avatars.streaming?.provider)
    }

    @Test
    fun the_streaming_reply_on_an_agent_takes_its_avatar() {
        val agents = listOf(Agent(id = "a1", avatar = buildJsonObject { put("filepath", "/images/a1.png") }))
        val avatars = state(ModelChoice.Raw(EndpointConstants.AGENTS, "a1"), agents = agents).replyAvatars(server)
        assertEquals("$server/images/a1.png", avatars.streaming?.imageUrl)
    }

    @Test
    fun an_agents_saved_reply_draws_what_its_streaming_reply_did() {
        val agents = listOf(Agent(id = "a1", avatar = buildJsonObject { put("filepath", "/images/a1.png") }))
        val chat = state(ModelChoice.Raw(EndpointConstants.AGENTS, "a1"), agents = agents)
        val saved = Message(messageId = "r", conversationId = "c", endpoint = EndpointConstants.AGENTS, model = "a1")

        assertEquals(
            chat.replyAvatars(server).streaming,
            saved.replyAvatar(EndpointConstants.AGENTS, server, configs, agents, fallbackAgentId = "a1"),
        )
    }

    @Test
    fun an_agent_specs_saved_reply_draws_what_its_streaming_reply_did() {
        val agents = listOf(Agent(id = "a1", avatar = buildJsonObject { put("filepath", "/images/a1.png") }))
        val spec = ModelSpec(name = "helper", preset = Preset(endpoint = "agents", agentId = "a1"))
        val specs = ModelSpecs(list = listOf(spec))
        val chat = state(ModelChoice.Spec("helper", EndpointConstants.AGENTS, "a1"), specs, agents = agents)
        // The server saves an agent spec without its own icon as `iconURL: "agents"`.
        val saved =
            Message(messageId = "r", conversationId = "c", endpoint = EndpointConstants.AGENTS, model = "a1", iconURL = "agents")

        assertEquals("$server/images/a1.png", chat.replyAvatars(server).streaming?.imageUrl)
        assertEquals(
            chat.replyAvatars(server).streaming?.imageUrl,
            saved.replyAvatar(EndpointConstants.AGENTS, server, configs, agents, fallbackAgentId = "a1")?.imageUrl,
        )
    }

    @Test
    fun an_agent_spec_named_icon_streams_the_agents_avatar_its_saved_reply_draws() {
        val agents = listOf(Agent(id = "a1", avatar = buildJsonObject { put("filepath", "/images/a1.png") }))
        val spec = ModelSpec(name = "helper", iconURL = "groq", preset = Preset(endpoint = "agents", agentId = "a1"))
        val specs = ModelSpecs(list = listOf(spec))
        val chat = state(ModelChoice.Spec("helper", EndpointConstants.AGENTS, "a1"), specs, agents = agents)
        val saved =
            Message(messageId = "r", conversationId = "c", endpoint = EndpointConstants.AGENTS, model = "a1", iconURL = "groq")

        assertEquals("$server/images/a1.png", chat.replyAvatars(server).streaming?.imageUrl)
        assertEquals(
            chat.replyAvatars(server).streaming,
            saved.replyAvatar(EndpointConstants.AGENTS, server, configs, agents, fallbackAgentId = "a1"),
        )
    }

    @Test
    fun lane_b_is_resolved_only_while_compare_is_on() {
        val off = state(ModelChoice.Raw("openAI", "gpt"), comparison = ComparisonState(secondaryEndpoint = "My Router"))
        assertNull(off.replyAvatars(server).laneB)

        val on = off.copy(comparisonState = off.comparisonState.copy(isEnabled = true))
        assertEquals(ProviderId.OPENROUTER, on.replyAvatars(server).laneB?.provider)
    }
}
