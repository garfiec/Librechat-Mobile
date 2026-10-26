package com.garfiec.librechat.feature.chat.viewmodel

import com.garfiec.librechat.core.model.Preset
import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.core.model.config.ModelSpecs
import com.garfiec.librechat.core.ui.components.ModelParameters
import com.garfiec.librechat.feature.chat.util.parametersFor
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * What a spec chat puts on the wire: its preset's public parameters (the server applies none of
 * them without `enforce`) and its `artifacts` on the ephemeral agent (the server reads them nowhere
 * else).
 */
class ChatRequestBuilderSpecTest {

    private val deep = ModelSpec(
        name = "deep",
        preset = Preset(endpoint = "openAI", model = "gpt-4.1", temperature = 0.9),
        artifacts = JsonPrimitive(true),
    )
    private val specs = ModelSpecs(list = listOf(deep))

    private fun state(choice: ModelChoice, params: ModelParameters = ModelParameters.DEFAULT) = ChatUiState(
        selection = ModelSelectionState(choice = choice, modelSpecs = specs, modelParameters = params),
    )

    private val onDeep = ModelChoice.Spec("deep", "openAI", "gpt-4.1")

    @Test
    fun `a spec chat sends its preset's parameters`() {
        val payload = ChatRequestBuilder { state(onDeep, specs.parametersFor("deep")!!) }.buildModelParams()

        assertNotNull(payload)
        assertEquals(0.9, payload["temperature"]?.jsonPrimitive?.content?.toDouble())
    }

    @Test
    fun `an OpenAI reasoning spec sends its effort and token cap`() {
        val o3 = ModelSpecs(
            list = listOf(
                ModelSpec(
                    name = "deep-o3",
                    preset = Preset(endpoint = "openAI", model = "o3", reasoningEffort = "high", maxTokens = 32000),
                ),
            ),
        )
        val state = ChatUiState(
            selection = ModelSelectionState(
                choice = ModelChoice.Spec("deep-o3", "openAI", "o3"),
                modelSpecs = o3,
                modelParameters = o3.parametersFor("deep-o3")!!,
            ),
        )

        val payload = ChatRequestBuilder { state }.buildModelParams()

        assertNotNull(payload)
        assertEquals("high", payload["reasoning_effort"]?.jsonPrimitive?.content)
        assertEquals("32000", payload["max_tokens"]?.jsonPrimitive?.content)
    }

    @Test
    fun `an Anthropic spec that turns thinking off says so, and an untouched chat sends nothing`() {
        val haiku = ModelSpecs(
            list = listOf(
                ModelSpec(
                    name = "plain",
                    preset = Preset(endpoint = "anthropic", model = "claude-haiku-4-5", temperature = 0.2, thinking = false),
                ),
            ),
        )
        fun payload(params: ModelParameters) = ChatRequestBuilder {
            ChatUiState(
                selection = ModelSelectionState(
                    choice = ModelChoice.Spec("plain", "anthropic", "claude-haiku-4-5"),
                    modelSpecs = haiku,
                    modelParameters = params,
                ),
            )
        }.buildModelParams()

        // Anthropic thinks by default, so an off that isn't sent leaves the server refusing the temperature.
        assertEquals("false", payload(haiku.parametersFor("plain")!!)?.get("thinking")?.jsonPrimitive?.content)
        assertNull(payload(ModelParameters.DEFAULT))
    }

    @Test
    fun `a spec with artifacts turns them on for the run`() {
        val agent = ChatRequestBuilder { state(onDeep) }.buildEphemeralAgent()

        assertEquals("default", agent?.artifacts)
    }

    @Test
    fun `a raw pick sends no artifacts`() {
        assertNull(ChatRequestBuilder { state(ModelChoice.Raw("openAI", "gpt-4.1")) }.buildEphemeralAgent())
    }

    @Test
    fun `an agent runs on its own artifacts`() {
        val agentSpec = ModelChoice.Spec("deep", "agents", "agent_1")

        assertNull(ChatRequestBuilder { state(agentSpec) }.buildEphemeralAgent())
    }
}
