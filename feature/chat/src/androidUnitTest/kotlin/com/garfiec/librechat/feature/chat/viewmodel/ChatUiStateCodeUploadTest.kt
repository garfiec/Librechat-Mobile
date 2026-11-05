package com.garfiec.librechat.feature.chat.viewmodel

import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.response.EndpointFileConfig
import com.garfiec.librechat.core.model.response.FileUploadConfig
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** When the attach sheet offers "Upload to Code Environment" (web `useAttachItems`, legacy UX). */
class ChatUiStateCodeUploadTest {

    private val legacy = FileUploadConfig(legacyFileUploadUX = true)

    private fun state(
        endpoint: String = "openAI",
        model: String? = "gpt-4.1",
        config: FileUploadConfig? = legacy,
        agentsCapabilities: List<String> = listOf("execute_code"),
        agents: List<Agent> = emptyList(),
        runCode: Boolean = true,
    ) = ChatUiState(
        selection = ModelSelectionState(
            choice = ModelChoice.Raw(endpoint, model),
            agents = agents,
            endpointConfigs = mapOf(
                endpoint to EndpointConfig(),
                EndpointConstants.AGENTS to EndpointConfig(capabilities = agentsCapabilities),
            ),
        ),
        gates = FeatureGatesState(runCodeEnabled = runCode),
        account = AccountConfigState(fileUploadConfig = config),
    )

    @Test
    fun `offered under the legacy upload UX with the capability and RUN_CODE`() {
        assertThat(state().codeUploadOffered).isTrue()
    }

    @Test
    fun `hidden under the unified UX and before the file config loads`() {
        assertThat(state(config = FileUploadConfig()).codeUploadOffered).isFalse()
        assertThat(state(config = null).codeUploadOffered).isFalse()
        val overridden = FileUploadConfig(
            legacyFileUploadUX = true,
            endpoints = mapOf("openAI" to EndpointFileConfig(legacyFileUploadUX = false)),
        )
        assertThat(state(config = overridden).codeUploadOffered).isFalse()
    }

    @Test
    fun `hidden without the code capability or RUN_CODE`() {
        assertThat(state(agentsCapabilities = listOf("file_search")).codeUploadOffered).isFalse()
        assertThat(state(runCode = false).codeUploadOffered).isFalse()
    }

    @Test
    fun `a saved agent needs execute_code among its tools`() {
        val coder = Agent(id = "agent-1", tools = listOf("execute_code"))
        val writer = Agent(id = "agent-2", tools = listOf("file_search"))
        val agents = listOf(coder, writer)

        assertThat(state(EndpointConstants.AGENTS, coder.id, agents = agents).codeUploadOffered).isTrue()
        assertThat(state(EndpointConstants.AGENTS, writer.id, agents = agents).codeUploadOffered).isFalse()
        assertThat(state(EndpointConstants.AGENTS, "unknown", agents = agents).codeUploadOffered).isFalse()
    }
}
