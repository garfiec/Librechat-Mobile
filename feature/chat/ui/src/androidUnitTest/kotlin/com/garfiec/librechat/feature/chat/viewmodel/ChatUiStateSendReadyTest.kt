package com.garfiec.librechat.feature.chat.viewmodel

import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.model.EndpointConfig
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for [ChatUiState.isSendReady], the pre-send gate used by [ChatViewModel]'s
 * `runWhenSendReady` helper to avoid the cold-start race where endpoint/config hasn't
 * arrived yet and firing startChat would produce a mislabeled 403.
 *
 * Pure-function tests — no ViewModel instantiation, no mockk overhead.
 */
class ChatUiStateSendReadyTest {

    private val anthropic = "anthropic"
    private val haiku = "claude-haiku-4-5-20251001"

    @Test
    fun `not ready when availableModels is empty`() {
        val state = ChatUiState(
            selection = ModelSelectionState(
                choice = ModelChoice.Raw(anthropic, haiku),
                availableModels = emptyMap(),
            ),
            gates = FeatureGatesState(agentsEnabled = true),
        )
        assertThat(state.isSendReady).isFalse()
    }

    @Test
    fun `ready when non-agents endpoint selected and models available`() {
        val state = ChatUiState(
            selection = ModelSelectionState(
                choice = ModelChoice.Raw(anthropic, haiku),
                availableModels = mapOf(anthropic to listOf(haiku)),
            ),
            gates = FeatureGatesState(agentsEnabled = true),
        )
        assertThat(state.isSendReady).isTrue()
    }

    @Test
    fun `not ready when agents endpoint selected but AGENTS USE denied`() {
        // This is the reporter's scenario: cold start with agents as default
        // selection, but role has loaded and denied AGENTS.USE. A send right now
        // would hit /api/agents/chat/agents and 403.
        val state = ChatUiState(
            selection = ModelSelectionState(
                choice = ModelChoice.Raw(EndpointConstants.AGENTS, null),
                availableModels = mapOf(anthropic to listOf(haiku)),
            ),
            gates = FeatureGatesState(agentsEnabled = false),
        )
        assertThat(state.isSendReady).isFalse()
    }

    @Test
    fun `ready when agents endpoint selected and AGENTS USE granted`() {
        val state = ChatUiState(
            selection = ModelSelectionState(
                choice = ModelChoice.Raw(EndpointConstants.AGENTS, null),
                availableModels = mapOf(anthropic to listOf(haiku)),
            ),
            gates = FeatureGatesState(agentsEnabled = true),
        )
        assertThat(state.isSendReady).isTrue()
    }

    @Test
    fun `ready on an agents-only server that lists no models`() {
        // A stock server with only the agents endpoint enabled returns an empty /api/models.
        // The endpoint config is the readiness signal there (#541).
        val state = ChatUiState(
            selection = ModelSelectionState(
                choice = ModelChoice.Raw(EndpointConstants.AGENTS, null),
                availableModels = emptyMap(),
                endpointConfigs = mapOf(EndpointConstants.AGENTS to EndpointConfig()),
            ),
            gates = FeatureGatesState(agentsEnabled = true),
        )
        assertThat(state.isSendReady).isTrue()
    }

    @Test
    fun `not ready on the agents endpoint before any config arrives`() {
        val state = ChatUiState(
            selection = ModelSelectionState(choice = ModelChoice.Raw(EndpointConstants.AGENTS, null)),
            gates = FeatureGatesState(agentsEnabled = true),
        )
        assertThat(state.isSendReady).isFalse()
    }

    @Test
    fun `not ready on the agents endpoint when the config does not offer agents`() {
        // A config without an `agents` entry means the endpoint is disabled; sending would fail.
        val state = ChatUiState(
            selection = ModelSelectionState(
                choice = ModelChoice.Raw(EndpointConstants.AGENTS, null),
                endpointConfigs = mapOf(anthropic to EndpointConfig()),
            ),
            gates = FeatureGatesState(agentsEnabled = true),
        )
        assertThat(state.isSendReady).isFalse()
    }

    @Test
    fun `endpoint configs alone do not make a non-agents endpoint ready`() {
        val state = ChatUiState(
            selection = ModelSelectionState(
                choice = ModelChoice.Raw(anthropic, haiku),
                endpointConfigs = mapOf(anthropic to EndpointConfig()),
            ),
            gates = FeatureGatesState(agentsEnabled = true),
        )
        assertThat(state.isSendReady).isFalse()
    }

    @Test
    fun `not ready when only agents endpoint is offered by server but AGENTS USE denied`() {
        // Edge: server exposes only the agents endpoint, role denies.
        // No viable send path — stays not-ready. runWhenSendReady will time out and toast.
        val state = ChatUiState(
            selection = ModelSelectionState(
                choice = ModelChoice.Raw(EndpointConstants.AGENTS, null),
                endpointConfigs = mapOf(EndpointConstants.AGENTS to EndpointConfig()),
            ),
            gates = FeatureGatesState(agentsEnabled = false),
        )
        assertThat(state.isSendReady).isFalse()
    }

    @Test
    fun `ready when non-agents endpoint selected and model list for that endpoint is empty`() {
        // isSendReady only cares that availableModels is non-empty and the selected
        // endpoint isn't a known-denied one. An empty model list on the current endpoint
        // is the user's concern (pick a model), not the race guard's.
        val state = ChatUiState(
            selection = ModelSelectionState(
                choice = ModelChoice.Raw(anthropic, null),
                availableModels = mapOf(
                    "openAI" to listOf("gpt-4o"),
                    anthropic to emptyList(),
                ),
            ),
            gates = FeatureGatesState(agentsEnabled = true),
        )
        assertThat(state.isSendReady).isTrue()
    }
}
