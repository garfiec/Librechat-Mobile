package com.garfiec.librechat.core.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class EndpointConfigSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun minimalEndpointConfigRoundTrip() {
        val original = EndpointConfig()
        val encoded = json.encodeToString(EndpointConfig.serializer(), original)
        val decoded = json.decodeFromString(EndpointConfig.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun fullyPopulatedEndpointConfigRoundTrip() {
        val original = EndpointConfig(
            type = "openAI",
            order = 1,
            iconURL = "https://example.com/icon.svg",
            modelDisplayLabel = "GPT-4o",
            name = "OpenAI",
            userProvide = true,
            userProvideURL = false,
            capabilities = listOf("image_input", "tools", "reasoning"),
            disableBuilder = false,
        )
        val encoded = json.encodeToString(EndpointConfig.serializer(), original)
        val decoded = json.decodeFromString(EndpointConfig.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun endpointConfigDeserializesFromServerJson() {
        val serverJson = """
            {
                "type": "anthropic",
                "order": 2,
                "modelDisplayLabel": "Claude",
                "capabilities": ["image_input", "tools"],
                "disableBuilder": true,
                "someNewField": 42
            }
        """.trimIndent()
        val decoded = json.decodeFromString(EndpointConfig.serializer(), serverJson)
        assertEquals("anthropic", decoded.type)
        assertEquals(2, decoded.order)
        assertEquals("Claude", decoded.modelDisplayLabel)
        assertEquals(listOf("image_input", "tools"), decoded.capabilities)
        assertEquals(true, decoded.disableBuilder)
    }

    @Test
    fun bedrockCredentialFlagsDecode() {
        // Shape of the `bedrock` entry `packages/api/src/endpoints/config/endpoints.ts` builds.
        val serverJson = """
            {
                "userProvide": true,
                "order": 7,
                "userProvideAccessKeyId": true,
                "userProvideSecretAccessKey": true,
                "userProvideSessionToken": false,
                "userProvideBearerToken": true
            }
        """.trimIndent()
        val decoded = json.decodeFromString(EndpointConfig.serializer(), serverJson)
        assertEquals(true, decoded.userProvideAccessKeyId)
        assertEquals(true, decoded.userProvideSecretAccessKey)
        assertEquals(false, decoded.userProvideSessionToken)
        assertEquals(true, decoded.userProvideBearerToken)
    }

    @Test
    fun bedrockCredentialFlagsDefaultToNullWhenAbsent() {
        val decoded = json.decodeFromString(EndpointConfig.serializer(), """{"userProvide": true}""")
        assertEquals(null, decoded.userProvideAccessKeyId)
        assertEquals(null, decoded.userProvideSecretAccessKey)
        assertEquals(null, decoded.userProvideSessionToken)
        assertEquals(null, decoded.userProvideBearerToken)
    }

    @Test
    fun agentsAllowedProvidersDecode() {
        val decoded = json.decodeFromString(
            EndpointConfig.serializer(),
            """{"allowedProviders": ["anthropic", "openAI"], "capabilities": ["tools"]}""",
        )
        assertEquals(listOf("anthropic", "openAI"), decoded.allowedProviders)
        assertEquals(emptyList(), json.decodeFromString(EndpointConfig.serializer(), "{}").allowedProviders)
    }
}
