package com.garfiec.librechat.core.data.endpoint

import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.endpoint.EndpointClassifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EndpointClassifierTest {

    @Test
    fun customEndpointWithoutConfig_returnsCustom() {
        val dispatch = EndpointClassifier.classify("OpenRouter", emptyMap(), keyExpiry = null)
        assertEquals("custom", dispatch.endpointType)
        assertNull(dispatch.key)
        assertEquals("OpenRouter", dispatch.modelDisplayLabel)
    }

    @Test
    fun builtInEndpointWithColdStartConfig_usesFallback() {
        val dispatch = EndpointClassifier.classify("openAI", emptyMap(), keyExpiry = null)
        assertEquals("openAI", dispatch.endpointType)
        assertNull(dispatch.key)
    }

    @Test
    fun userProvidedEndpointSendsKey() {
        val configs = mapOf(
            "OpenRouter" to EndpointConfig(
                type = "custom",
                userProvide = true,
                modelDisplayLabel = "OpenRouter",
            ),
        )
        val dispatch = EndpointClassifier.classify("OpenRouter", configs, keyExpiry = null)
        assertEquals("never", dispatch.key)
        assertEquals("custom", dispatch.endpointType)
    }

    @Test
    fun envKeyEndpointOmitsKey() {
        val configs = mapOf(
            "anthropic" to EndpointConfig(
                type = "anthropic",
                userProvide = false,
            ),
        )
        // Even with a stored expiry, an endpoint that does NOT require user_provided keys
        // omits the field from the wire body.
        val dispatch = EndpointClassifier.classify("anthropic", configs, keyExpiry = "2026-05-01T00:00:00Z")
        assertNull(dispatch.key)
    }

    @Test
    fun customOverridingBuiltInNamePrefersConfigType() {
        val configs = mapOf(
            "openAI" to EndpointConfig(
                type = "custom",
                userProvide = true,
            ),
        )
        val dispatch = EndpointClassifier.classify("openAI", configs, keyExpiry = null)
        assertEquals("custom", dispatch.endpointType)
    }

    @Test
    fun modelDisplayLabelFallsBackToEndpointName() {
        val configs = mapOf(
            "OpenRouter" to EndpointConfig(
                type = "custom",
                modelDisplayLabel = null,
            ),
        )
        val dispatch = EndpointClassifier.classify("OpenRouter", configs, keyExpiry = null)
        assertEquals("OpenRouter", dispatch.modelDisplayLabel)
    }

    // --- PR-2 (#20): real-expiry pass-through cases ---

    @Test
    fun userProvidedWithStoredExpiry_passesIsoTimestampThrough() {
        val configs = mapOf(
            "OpenRouter" to EndpointConfig(
                type = "custom",
                userProvide = true,
            ),
        )
        val iso = "2026-05-01T12:00:00.000Z"
        val dispatch = EndpointClassifier.classify("OpenRouter", configs, keyExpiry = iso)
        assertEquals(iso, dispatch.key)
    }

    @Test
    fun userProvidedWithNullExpiry_fallsBackToNeverLiteral() {
        val configs = mapOf(
            "OpenRouter" to EndpointConfig(
                type = "custom",
                userProvide = true,
            ),
        )
        val dispatch = EndpointClassifier.classify("OpenRouter", configs, keyExpiry = null)
        assertEquals("never", dispatch.key)
    }

    @Test
    fun userProvidedWithNeverLiteral_passesNeverThrough() {
        // Backend may also return the literal string "never" via KeyExpiryResponse.
        val configs = mapOf(
            "OpenRouter" to EndpointConfig(
                type = "custom",
                userProvide = true,
            ),
        )
        val dispatch = EndpointClassifier.classify("OpenRouter", configs, keyExpiry = "never")
        assertEquals("never", dispatch.key)
    }

    @Test
    fun userProvideUrlOnlyEndpointStillSendsKey() {
        // PR-2 #20(d): some custom endpoints set userProvideURL=true with userProvide
        // null/false. They still require the key field on the wire.
        val configs = mapOf(
            "MyCustom" to EndpointConfig(
                type = "custom",
                userProvide = null,
                userProvideURL = true,
            ),
        )
        val dispatch = EndpointClassifier.classify("MyCustom", configs, keyExpiry = "2026-05-01T00:00:00Z")
        assertEquals("2026-05-01T00:00:00Z", dispatch.key)
    }
}
