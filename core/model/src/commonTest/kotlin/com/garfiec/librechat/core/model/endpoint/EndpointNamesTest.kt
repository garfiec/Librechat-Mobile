package com.garfiec.librechat.core.model.endpoint

import com.garfiec.librechat.core.model.EndpointConfig
import kotlin.test.Test
import kotlin.test.assertEquals

class EndpointNamesTest {

    @Test
    fun aFirstClassKeyReadsAsWebNamesIt() {
        assertEquals("Anthropic", endpointDisplayName("anthropic", null))
        assertEquals("Azure OpenAI", endpointDisplayName("azureOpenAI", EndpointConfig()))
        assertEquals("AWS Bedrock", endpointDisplayName("bedrock", null))
    }

    @Test
    fun theServedLabelComesFirst() {
        assertEquals("Claude", endpointDisplayName("anthropic", EndpointConfig(modelDisplayLabel = "Claude")))
        assertEquals("Anthropic", endpointDisplayName("anthropic", EndpointConfig(modelDisplayLabel = " ")))
    }

    @Test
    fun aCustomEndpointKeepsItsName() {
        assertEquals("Team Gateway", endpointDisplayName("Team Gateway", EndpointConfig()))
        // Web's names match the key exactly.
        assertEquals("Ollama", endpointDisplayName("ollama", null))
        assertEquals("OLLAMA", endpointDisplayName("OLLAMA", null))
    }

    @Test
    fun agentsAndCustomAreLeftToTheScreensTranslations() {
        assertEquals("agents", endpointDisplayName("agents", null))
        assertEquals("custom", endpointDisplayName("custom", null))
    }
}
