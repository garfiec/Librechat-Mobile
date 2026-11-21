package com.garfiec.librechat.feature.chat.screen

import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.core.model.config.ModelSpecs
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Locks the F4 composer/header label rule that the Android + iOS chat screens share via
 * [rememberChatModelLabel]: under the agents endpoint the label is the agent's name (or a
 * neutral fallback), NEVER a raw model string; non-agent endpoints show the model name.
 */
class ChatModelLabelTest {

    private val fallback = "Agent"

    @Test
    fun selectedSpecUsesItsLabelInsteadOfTheUnderlyingModel() {
        val specs = ModelSpecs(list = listOf(
            ModelSpec(name = "first", label = "First Assistant"),
            ModelSpec(name = "second", label = "Second Assistant"),
        ))
        val result = chatModelLabel(
            selectedEndpoint = "custom",
            selectedModel = "internal.gateway.model",
            agents = emptyList(),
            agentFallbackLabel = fallback,
            selectedSpec = "second",
            modelSpecs = specs,
        )
        assertThat(result.displayModel).isEqualTo("Second Assistant")
    }

    @Test
    fun absentOrBlankSpecLabelFallsBackToSpecName() {
        for (label in listOf(null, "", "   ")) {
            val result = chatModelLabel(
                selectedEndpoint = "custom",
                selectedModel = "internal.gateway.model",
                agents = emptyList(),
                agentFallbackLabel = fallback,
                selectedSpec = "curated-spec",
                modelSpecs = ModelSpecs(list = listOf(ModelSpec(name = "curated-spec", label = label))),
            )
            assertThat(result.displayModel).isEqualTo("curated-spec")
        }
    }

    @Test
    fun rawSelectionAndUnknownSpecKeepTheModelLabel() {
        for (selectedSpec in listOf(null, "unknown")) {
            val result = chatModelLabel(
                selectedEndpoint = "custom",
                selectedModel = "raw-model",
                agents = emptyList(),
                agentFallbackLabel = fallback,
                selectedSpec = selectedSpec,
                modelSpecs = ModelSpecs(list = listOf(ModelSpec(name = "other", label = "Other Assistant"))),
            )
            assertThat(result.displayModel).isEqualTo("raw-model")
        }
    }

    @Test
    fun agentSpecUsesSpecLabelAndPreservesResolvedAgentName() {
        val result = chatModelLabel(
            selectedEndpoint = EndpointConstants.AGENTS,
            selectedModel = "agent_1",
            agents = listOf(Agent(id = "agent_1", name = "Research Bot")),
            agentFallbackLabel = fallback,
            selectedSpec = "curated-spec",
            modelSpecs = ModelSpecs(list = listOf(ModelSpec(name = "curated-spec", label = "Curated Assistant"))),
        )
        assertThat(result.agentName).isEqualTo("Research Bot")
        assertThat(result.displayModel).isEqualTo("Curated Assistant")
    }

    @Test
    fun agentsEndpointWithResolvedAgentUsesAgentName() {
        val result = chatModelLabel(
            selectedEndpoint = EndpointConstants.AGENTS,
            selectedModel = "agent_1",
            agents = listOf(Agent(id = "agent_1", name = "Research Bot")),
            agentFallbackLabel = fallback,
        )
        assertThat(result.agentName).isEqualTo("Research Bot")
        assertThat(result.displayModel).isEqualTo("Research Bot")
    }

    @Test
    fun agentsEndpointWithUnresolvedAgentFallsBackToNeutralLabelNeverRawId() {
        // The agent id isn't in the list yet (list not loaded / stale id): must show the
        // neutral fallback, NOT the raw id — the exact F4 mislabel ("Message gpt-3.5-turbo").
        val result = chatModelLabel(
            selectedEndpoint = EndpointConstants.AGENTS,
            selectedModel = "agent_missing",
            agents = emptyList(),
            agentFallbackLabel = fallback,
        )
        assertThat(result.agentName).isNull()
        assertThat(result.displayModel).isEqualTo(fallback)
        assertThat(result.displayModel).isNotEqualTo("agent_missing")
    }

    @Test
    fun agentsEndpointWithNullModelFallsBackToNeutralLabel() {
        val result = chatModelLabel(
            selectedEndpoint = EndpointConstants.AGENTS,
            selectedModel = null,
            agents = emptyList(),
            agentFallbackLabel = fallback,
        )
        assertThat(result.agentName).isNull()
        assertThat(result.displayModel).isEqualTo(fallback)
    }

    @Test
    fun nonAgentEndpointShowsRawModelName() {
        val result = chatModelLabel(
            selectedEndpoint = "openAI",
            selectedModel = "gpt-4o",
            agents = listOf(Agent(id = "agent_1", name = "Research Bot")),
            agentFallbackLabel = fallback,
        )
        // Not the agents endpoint → agentName must be null and the model shows verbatim.
        assertThat(result.agentName).isNull()
        assertThat(result.displayModel).isEqualTo("gpt-4o")
    }

    @Test
    fun nonAgentEndpointWithNullModelKeepsNullDisplay() {
        val result = chatModelLabel(
            selectedEndpoint = "openAI",
            selectedModel = null,
            agents = emptyList(),
            agentFallbackLabel = fallback,
        )
        assertThat(result.agentName).isNull()
        assertThat(result.displayModel).isNull()
    }

    @Test
    fun agentResolvedButNameNullFallsBackToNeutralLabel() {
        // Agent is in the list but has no display name → still must not surface the raw id.
        val result = chatModelLabel(
            selectedEndpoint = EndpointConstants.AGENTS,
            selectedModel = "agent_1",
            agents = listOf(Agent(id = "agent_1", name = null)),
            agentFallbackLabel = fallback,
        )
        assertThat(result.agentName).isNull()
        assertThat(result.displayModel).isEqualTo(fallback)
    }

    @Test
    fun modelSubtitleNamesASpecsModelButNotARawModelTwice() {
        assertThat(selectionModelSubtitle("openAI", "gpt-5.1", emptyList(), title = "Research Assistant"))
            .isEqualTo("gpt-5.1")
        assertThat(selectionModelSubtitle("openAI", "gpt-5.1", emptyList(), title = "gpt-5.1")).isNull()
        assertThat(selectionModelSubtitle("openAI", null, emptyList(), title = null)).isNull()
    }

    @Test
    fun modelSubtitleForAnAgentIsItsModelNeverItsId() {
        val agents = listOf(Agent(id = "agent_1", name = "Trip planner", model = "claude-opus-4-5"))
        assertThat(selectionModelSubtitle(EndpointConstants.AGENTS, "agent_1", agents, title = "Trip planner"))
            .isEqualTo("claude-opus-4-5")
        assertThat(selectionModelSubtitle(EndpointConstants.AGENTS, "agent_2", agents, title = "Agent")).isNull()
    }
}
