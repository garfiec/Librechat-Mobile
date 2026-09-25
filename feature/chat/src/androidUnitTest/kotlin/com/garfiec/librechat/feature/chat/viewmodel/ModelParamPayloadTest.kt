package com.garfiec.librechat.feature.chat.viewmodel

import com.garfiec.librechat.core.ui.components.ModelParameters
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModelParamPayloadTest {

    private fun build(params: ModelParameters) = ModelParamPayload.build(
        endpoint = "anthropic",
        provider = null,
        model = null,
        extendedEffortSupported = false,
        params = params,
    )

    @Test
    fun `untouched params send nothing`() {
        assertTrue(build(ModelParameters.DEFAULT).isEmpty())
    }

    @Test
    fun `changed temperature is sent as a number`() {
        val result = build(ModelParameters.DEFAULT.copy(temperature = 0.7f))
        assertEquals(0.7, result["temperature"]?.jsonPrimitive?.double)
    }

    @Test
    fun `promptCacheTtl is sent as a string when set`() {
        val params = ModelParameters.DEFAULT.copy(dynamicValues = mapOf("promptCacheTtl" to "1h"))
        val result = build(params)
        assertEquals("1h", result["promptCacheTtl"]?.jsonPrimitive?.content)
    }

    @Test
    fun `promptCache opt-out is transmitted as a boolean`() {
        // Anthropic caches by default (registry default "true"), so turning it OFF is a real change and
        // must be sent — encoded as a JSON boolean, not the string "false".
        val params = ModelParameters.DEFAULT.copy(dynamicValues = mapOf("promptCache" to "false"))
        val result = build(params)
        assertEquals(false, result["promptCache"]?.jsonPrimitive?.boolean)
    }

    @Test
    fun `default-valued params are not over-sent`() {
        // Regression guard: the sheet seeds default-valued entries into dynamicValues on open; a value
        // equal to its default (the provider registry default for promptCache, the composer default for
        // top_p/topP) must NOT be transmitted on an untouched chat.
        val seeded = ModelParameters.DEFAULT.copy(
            dynamicValues = mapOf("promptCache" to "true", "top_p" to "1.0", "topP" to "1.0"),
        )
        val result = build(seeded)
        assertNull(result["promptCache"])
        assertNull(result["top_p"])
        assertNull(result["topP"])
    }

    @Test
    fun `an anthropic agent sends prompt caching for a model that supports it`() {
        // The registry filters per model, so the payload has to be built with the AGENT'S
        // model. Built with the agent id (which is what `selectedModel` holds on this endpoint)
        // no cache-capable model matches, and the two controls are dropped from the definitions —
        // which silently removes them from the body a user explicitly set.
        val params = ModelParameters.DEFAULT.copy(
            dynamicValues = mapOf("promptCache" to "false", "promptCacheTtl" to "1h"),
        )
        val result = ModelParamPayload.build(
            endpoint = "agents",
            provider = "anthropic",
            model = "claude-sonnet-4-5",
            extendedEffortSupported = false,
            params = params,
        )
        assertEquals(false, result["promptCache"]?.jsonPrimitive?.boolean)
        assertEquals("1h", result["promptCacheTtl"]?.jsonPrimitive?.content)
    }

    @Test
    fun `a dropped param is not sent`() {
        val params = ModelParameters.DEFAULT.copy(temperature = 0.7f)
        val result = ModelParamPayload.build(
            endpoint = "anthropic",
            provider = null,
            model = "claude-sonnet-4-5",
            extendedEffortSupported = false,
            params = params,
            dropParamsMap = mapOf("anthropic" to JsonArray(listOf(JsonPrimitive("temperature")))),
        )
        assertNull(result["temperature"])
    }
}
