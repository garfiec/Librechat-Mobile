package com.garfiec.librechat.core.model.config

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EndpointDropParamsTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun configOf(body: String) =
        json.decodeFromString(StartupConfig.serializer(), body).endpointsDropParamsMap

    @Test
    fun aListEntryAppliesToEveryModelOnThatEndpoint() {
        val map = configOf("""{"endpointsDropParamsMap":{"ollama":["stop"]}}""")
        assertEquals(listOf("stop"), EndpointDropParams.resolve(map, "ollama", null, "llama3"))
        assertEquals(listOf("stop"), EndpointDropParams.resolve(map, "ollama", null, null))
    }

    @Test
    fun azureMapsAProviderToAPerModelLookup() {
        // The one endpoint whose entry is an object rather than a list: Azure deployments differ
        // model by model, so reading this arm as a list would drop the whole entry.
        val map = configOf(
            """{"endpointsDropParamsMap":{"azureOpenAI":{"gpt-5":["temperature"],"gpt-4o":["topP"]}}}""",
        )
        assertEquals(listOf("temperature"), EndpointDropParams.resolve(map, "azureOpenAI", null, "gpt-5"))
        assertEquals(listOf("topP"), EndpointDropParams.resolve(map, "azureOpenAI", null, "gpt-4o"))
        assertEquals(emptyList(), EndpointDropParams.resolve(map, "azureOpenAI", null, "gpt-4.1"))
        assertEquals(emptyList(), EndpointDropParams.resolve(map, "azureOpenAI", null, null))
    }

    @Test
    fun theAgentsEndpointLooksUpTheAgentsOwnProvider() {
        // The request is made against the provider, so `agents` is never a key in this map.
        val map = configOf("""{"endpointsDropParamsMap":{"anthropic":["topK"]}}""")
        assertEquals(listOf("topK"), EndpointDropParams.resolve(map, "agents", "anthropic", "claude-sonnet-4-5"))
        assertEquals(emptyList(), EndpointDropParams.resolve(map, "agents", null, "claude-sonnet-4-5"))
    }

    @Test
    fun aCustomEndpointIsKeyedByItsOwnName() {
        val map = configOf("""{"endpointsDropParamsMap":{"OpenRouter":["presencePenalty"]}}""")
        assertEquals(listOf("presencePenalty"), EndpointDropParams.resolve(map, "OpenRouter", null, "any"))
        assertEquals(emptyList(), EndpointDropParams.resolve(map, "openrouter", null, "any"))
    }

    @Test
    fun anAbsentMapDropsNothing() {
        assertNull(configOf("""{"appTitle":"LibreChat"}"""))
        assertEquals(emptyList(), EndpointDropParams.resolve(null, "openAI", null, "gpt-4o"))
        assertEquals(emptyList(), EndpointDropParams.resolve(configOf("""{"endpointsDropParamsMap":{}}"""), "openAI", null, "gpt-4o"))
    }

    @Test
    fun aMalformedEntryDegradesToDroppingNothing() {
        val map = configOf("""{"endpointsDropParamsMap":{"openAI":"topP","google":[1,"topK"]}}""")
        assertEquals(emptyList(), EndpointDropParams.resolve(map, "openAI", null, "gpt-4o"))
        // A non-string element is skipped rather than failing the whole entry.
        assertEquals(listOf("topK"), EndpointDropParams.resolve(map, "google", null, "gemini-2.5-pro"))
    }
}
