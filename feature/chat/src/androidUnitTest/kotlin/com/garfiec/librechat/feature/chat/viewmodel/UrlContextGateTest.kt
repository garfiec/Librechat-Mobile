package com.garfiec.librechat.feature.chat.viewmodel

import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.EndpointConfig
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The URL context toggle shows only where the server runs the tool: a Google parameter set, on a
 * model `supportsUrlContext` accepts. Elsewhere the server skips it with a debug log, so the toggle
 * would do nothing.
 */
class UrlContextGateTest {

    private fun gate(
        endpoint: String,
        model: String,
        agents: List<Agent> = emptyList(),
        configs: Map<String, EndpointConfig> = emptyMap(),
    ) = ChatUiState(
        selection = ModelSelectionState(choice = ModelChoice.Raw(endpoint, model), endpointConfigs = configs, agents = agents),
    ).urlContextProviderGate

    @Test
    fun googleShowsItOnGemini25AndLater() {
        assertTrue(gate("google", "gemini-2.5-pro"))
        assertTrue(gate("google", "gemini-3-flash"))
    }

    @Test
    fun googleHidesItOnModelsTheServerSkips() {
        assertFalse(gate("google", "gemini-2.0-flash"))
        assertFalse(gate("google", "gemini-2.5-flash-image"))
        assertFalse(gate("google", "gemini-2.5-flash-preview-tts"))
    }

    @Test
    fun aGoogleAgentIsJudgedOnItsOwnModel() {
        val agents = listOf(
            Agent(id = "a-new", provider = "google", model = "gemini-2.5-pro"),
            Agent(id = "a-old", provider = "google", model = "gemini-1.5-pro"),
        )
        assertTrue(gate(EndpointConstants.AGENTS, "a-new", agents))
        assertFalse(gate(EndpointConstants.AGENTS, "a-old", agents))
    }

    @Test
    fun otherProvidersNeverShowIt() {
        assertFalse(gate("openAI", "gemini-2.5-pro"))
        assertFalse(gate("Groq", "gemini-2.5-pro", configs = mapOf("Groq" to EndpointConfig(type = "custom"))))
    }
}
