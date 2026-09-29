package com.garfiec.librechat.feature.agents.components

import com.garfiec.librechat.core.ui.components.EndpointParameterRegistry
import com.garfiec.librechat.feature.agents.components.model.AgentAdvancedSettings
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test

/**
 * An agent saved with a value its model no longer offers — effort `minimal` from a model before a
 * switch to gpt-6-sol — loses it on the next save. The editor shows it as "Unset", and `extras` is
 * copied from the loaded agent, so without an explicit removal the stale value would be saved back.
 */
class AgentModelParametersBridgeTest {

    private val gpt6SolDefs = EndpointParameterRegistry.getDefinitions(
        endpoint = "agents",
        provider = "openAI",
        model = "gpt-6-sol",
        extendedEffortSupported = true,
    )
    private val gpt6SolRemoved = EndpointParameterRegistry.modelRemovedOptions(
        endpoint = "agents",
        provider = "openAI",
        model = "gpt-6-sol",
    )

    private fun resaved(effort: String): AgentAdvancedSettings {
        val loaded = AgentAdvancedSettings(extras = mapOf("reasoning_effort" to JsonPrimitive(effort)))
        return loaded.toModelParameters().toAgentAdvancedSettings(loaded, gpt6SolDefs, gpt6SolRemoved)
    }

    @Test
    fun `a stale effort is removed on save`() {
        assertThat(resaved("minimal").extras).doesNotContainKey("reasoning_effort")
    }

    @Test
    fun `an effort the model offers survives the save`() {
        assertThat(resaved("low").extras["reasoning_effort"]).isEqualTo(JsonPrimitive("low"))
    }

    @Test
    fun `a key outside the visible schema is still preserved`() {
        val loaded = AgentAdvancedSettings(extras = mapOf("server_only" to JsonPrimitive("x")))
        val saved = loaded.toModelParameters().toAgentAdvancedSettings(loaded, gpt6SolDefs, gpt6SolRemoved)
        assertThat(saved.extras["server_only"]).isEqualTo(JsonPrimitive("x"))
    }

    /**
     * The panel path, before the server version is detected: the options lack `max` (a version
     * gate), so any edit in the panel used to delete it. Only a per-model rule removes a value.
     */
    @Test
    fun `a version-gated effort survives a panel edit before the version is detected`() {
        val undetectedDefs = EndpointParameterRegistry.getDefinitions(
            endpoint = "agents",
            provider = "anthropic",
            model = "claude-opus-4-5",
            extendedEffortSupported = false,
        )
        val removed = EndpointParameterRegistry.modelRemovedOptions(
            endpoint = "agents",
            provider = "anthropic",
            model = "claude-opus-4-5",
        )
        val loaded = AgentAdvancedSettings(extras = mapOf("effort" to JsonPrimitive("max")))

        val saved = loaded.toModelParameters().toAgentAdvancedSettings(loaded, undetectedDefs, removed)

        assertThat(saved.extras["effort"]).isEqualTo(JsonPrimitive("max"))
    }
}
