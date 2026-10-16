package com.garfiec.librechat.feature.chat.viewmodel

import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.model.response.EndpointFileConfig
import com.garfiec.librechat.core.model.response.FileUploadConfig
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** [ChatUiState.fileConfigEndpoint] and the [ChatUiState.fileUploadEnabled] gate built on it. */
class ChatUiStateFileConfigTest {

    private fun state(
        config: FileUploadConfig?,
        endpoint: String,
        agentProvider: String? = null,
        providerScoped: Boolean = true,
    ) = ChatUiState(
        selection = ModelSelectionState(selectedEndpoint = endpoint, selectedAgentProvider = agentProvider),
        account = AccountConfigState(fileUploadConfig = config),
        gates = FeatureGatesState(providerScopedUploadsSupported = providerScoped),
    )

    private val defaultDisabled = FileUploadConfig(
        endpoints = mapOf("default" to EndpointFileConfig(disabled = true)),
    )

    @Test
    fun `a saved agent resolves under its provider`() {
        assertThat(state(null, EndpointConstants.AGENTS, agentProvider = "openAI").fileConfigEndpoint)
            .isEqualTo("openAI")
        assertThat(state(null, EndpointConstants.AGENTS).fileConfigEndpoint).isEqualTo(EndpointConstants.AGENTS)
        // A provider left over from an agent the selection has moved off is ignored.
        assertThat(state(null, "google", agentProvider = "openAI").fileConfigEndpoint).isEqualTo("google")
    }

    @Test
    fun `a server before provider-scoped validation resolves a saved agent as agents`() {
        val pre = state(defaultDisabled, EndpointConstants.AGENTS, agentProvider = "openAI", providerScoped = false)
        assertThat(pre.fileConfigEndpoint).isEqualTo(EndpointConstants.AGENTS)
        assertThat(pre.fileUploadEnabled).isTrue()
    }

    @Test
    fun `default disabled hides attach for an endpoint without its own entry`() {
        assertThat(state(defaultDisabled, "openAI").fileUploadEnabled).isFalse()
        assertThat(state(defaultDisabled, EndpointConstants.AGENTS, agentProvider = "google").fileUploadEnabled)
            .isFalse()
    }

    @Test
    fun `default disabled leaves base-entry endpoints enabled`() {
        assertThat(state(defaultDisabled, EndpointConstants.AGENTS).fileUploadEnabled).isTrue()
        assertThat(state(defaultDisabled, "anthropic").fileUploadEnabled).isTrue()
    }

    @Test
    fun `an endpoint's own flag beats default`() {
        val config = FileUploadConfig(
            endpoints = mapOf(
                "default" to EndpointFileConfig(disabled = true),
                "openAI" to EndpointFileConfig(disabled = false),
                "agents" to EndpointFileConfig(disabled = true),
            ),
        )
        assertThat(state(config, "openAI").fileUploadEnabled).isTrue()
        assertThat(state(config, EndpointConstants.AGENTS).fileUploadEnabled).isFalse()
    }

    @Test
    fun `no config fails open`() {
        assertThat(state(null, "openAI").fileUploadEnabled).isTrue()
    }
}
