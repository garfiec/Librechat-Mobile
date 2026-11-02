package com.garfiec.librechat.core.model.config

import com.garfiec.librechat.core.model.CustomParams
import com.garfiec.librechat.core.model.EndpointConfig
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class ParamSettingsKeyTest {

    @Test
    fun aCustomEndpointIsKeyedByItsTypeNotItsName() {
        val configs = mapOf("Groq" to EndpointConfig(type = "custom"))
        assertEquals("custom", paramSettingsKey("Groq", configs))
    }

    @Test
    fun defaultParamsEndpointWinsOverTheType() {
        val configs = mapOf(
            "Claude Proxy" to EndpointConfig(type = "custom", customParams = CustomParams(defaultParamsEndpoint = "anthropic")),
        )
        assertEquals("anthropic", paramSettingsKey("Claude Proxy", configs))
    }

    @Test
    fun aNativeEndpointOrAnUnknownOneKeepsItsName() {
        assertEquals("anthropic", paramSettingsKey("anthropic", mapOf("anthropic" to EndpointConfig())))
        assertEquals("Unlisted", paramSettingsKey("Unlisted", emptyMap()))
    }

    @Test
    fun blankOverridesFallThrough() {
        val configs = mapOf("X" to EndpointConfig(type = "", customParams = CustomParams(defaultParamsEndpoint = "")))
        assertEquals("X", paramSettingsKey("X", configs))
    }

    @Test
    fun anOverrideTheServerHasNoSchemaForFallsBackToTheType() {
        // The server's `isEndpointSchemaLookupKey` is an exact key test: `xai` and `Anthropic`
        // parse with the custom (OpenAI) schema, so the client must use that set too.
        for (override in listOf("xai", "Anthropic", "openai")) {
            val config = EndpointConfig(type = "custom", customParams = CustomParams(defaultParamsEndpoint = override))
            val configs = mapOf("X" to config)
            assertEquals("custom", paramSettingsKey("X", configs), override)
        }
    }

    @Test
    fun customParamsDecodeFromTheEndpointsRoute() {
        val config = Json { ignoreUnknownKeys = true }.decodeFromString(
            EndpointConfig.serializer(),
            """{"type":"custom","customParams":{"defaultParamsEndpoint":"openrouter","paramDefinitions":[]}}""",
        )
        assertEquals("openrouter", paramSettingsKey("OpenRouter", mapOf("OpenRouter" to config)))
    }
}
