package com.garfiec.librechat.feature.agents.viewmodel

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.feature.agents.components.model.AgentAdvancedSettings
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test

/** v0.8.9-rc1's agent routes 400 on a `model_parameters` key the provider's profile does not declare (#17220). */
class AgentInferenceProfileTest {

    private fun keep(params: Map<String, JsonElement>, provider: String, model: String = "m", endpoint: AgentInferenceEndpoint? = null) =
        keepAcceptedInferenceParameters(params, provider, model, endpoint).keys

    private val n = JsonPrimitive(1)

    @Test
    fun `each family keeps its own keys and drops another family's`() {
        val params =
            mapOf("temperature" to n, "reasoning_effort" to n, "topK" to n, "thinking" to n, "frequency_penalty" to n)
        assertThat(keep(params, "anthropic")).containsExactly("temperature", "topK", "thinking")
        assertThat(keep(params, "openAI")).containsExactly("temperature", "reasoning_effort", "frequency_penalty")
        assertThat(keep(params, "google")).containsExactly("temperature", "topK", "thinking")
    }

    @Test
    fun `aliases are validated under their profile key and keep their own spelling`() {
        assertThat(keep(mapOf("top_p" to n, "max_tokens" to n), "anthropic")).containsExactly("top_p", "max_tokens")
        assertThat(keep(mapOf("stop_sequences" to n, "top_k" to n), "google")).containsExactly("stop_sequences", "top_k")
    }

    @Test
    fun `sdk configuration and unknown keys are dropped`() {
        val params = mapOf("temperature" to n, "baseURL" to n, "apiKey" to n, "configuration" to n, "somethingNew" to n)
        assertThat(keep(params, "openAI")).containsExactly("temperature")
    }

    @Test
    fun `model is kept only when it is the agent's own`() {
        assertThat(keep(mapOf("model" to JsonPrimitive("gpt-5")), "openAI", model = "gpt-5")).containsExactly("model")
        assertThat(keep(mapOf("model" to JsonPrimitive("gpt-4")), "openAI", model = "gpt-5")).isEmpty()
    }

    @Test
    fun `a custom endpoint follows its default params endpoint and keeps declared extras`() {
        val endpoint = AgentInferenceEndpoint(defaultParamsEndpoint = "anthropic", declaredKeys = setOf("routing_hint"))
        val params = mapOf("topK" to n, "frequency_penalty" to n, "routing_hint" to n, "undeclared" to n)
        assertThat(keep(params, "MyGateway", endpoint = endpoint)).containsExactly("topK", "routing_hint")
    }

    @Test
    fun `a declared extra cannot smuggle in a reserved or built-in key`() {
        val endpoint = AgentInferenceEndpoint(declaredKeys = setOf("thinking", "lc_secret"))
        assertThat(keep(mapOf("thinking" to n, "lc_secret" to n), "MyGateway", endpoint = endpoint)).isEmpty()
    }

    @Test
    fun `openrouter adds the prompt cache controls`() {
        assertThat(keep(mapOf("promptCache" to n), "openrouter")).containsExactly("promptCache")
        assertThat(keep(mapOf("promptCache" to n), "openAI")).isEmpty()
    }

    @Test
    fun `a legacy bedrock envelope is read into flat preferences and dropped`() {
        val stored = buildJsonObject {
            put("thinking", buildJsonObject { put("type", "enabled"); put("budget_tokens", 2000) })
            put("output_config", buildJsonObject { put("effort", "high") })
        }
        val result = keepAcceptedInferenceParameters(
            mapOf("additionalModelRequestFields" to stored),
            "bedrock",
            "us.anthropic.claude-sonnet-4-5",
            null,
        )
        assertThat(result.keys).containsExactly("thinking", "effort")
        assertThat(result["effort"]).isEqualTo(JsonPrimitive("high"))

        val nonClaude = keepAcceptedInferenceParameters(
            mapOf("additionalModelRequestFields" to buildJsonObject { put("reasoning_config", "low") }),
            "bedrock",
            "amazon.nova-pro",
            null,
        )
        assertThat(nonClaude).containsExactly("reasoning_effort", JsonPrimitive("low"))
    }

    @Test
    fun `the save filters only on a server known to check`() {
        val settings =
            AgentAdvancedSettings(temperature = 0.5f, extras = mapOf("reasoning_effort" to JsonPrimitive("high")))
        val base =
            AgentEditorUiState(name = "a", provider = "anthropic", model = "claude-x", advancedSettings = settings)
        assertThat(base.toUpdateAgentRequest().modelParameters!!.keys).containsExactly("temperature", "reasoning_effort")
        val filtered = base.copy(filtersInferenceParameters = true).toUpdateAgentRequest().modelParameters as JsonObject
        assertThat(filtered.keys).containsExactly("temperature")
    }

    @Test
    fun `a custom endpoint not yet known is saved as it is`() {
        val settings = AgentAdvancedSettings(extras = mapOf("routing_hint" to JsonPrimitive("x")))
        val state = AgentEditorUiState(
            name = "a",
            provider = "MyGateway",
            model = "m",
            advancedSettings = settings,
            filtersInferenceParameters = true,
        )
        assertThat(state.toUpdateAgentRequest().modelParameters!!.keys).containsExactly("routing_hint")
    }

    @Test
    fun `a refused parameter is named from the 400's details`() {
        val body = """{"error":"Invalid request data","details":[
            {"code":"custom","path":["model_parameters","reasoning_effort"],"message":"This inference parameter is not permitted for the selected endpoint."},
            {"code":"custom","path":["model_parameters","verbosity"],"message":"This inference parameter is not permitted for the selected endpoint."}
        ]}"""
        val e = ApiException(statusCode = 400, message = "Invalid request data", body = body)
        assertThat(inferenceParameterRejection(e))
            .isEqualTo("This inference parameter is not permitted for the selected endpoint. (reasoning_effort, verbosity)")
        assertThat(inferenceParameterRejection(ApiException(statusCode = 400, message = "x", body = """{"error":"x"}"""))).isNull()
    }
}
