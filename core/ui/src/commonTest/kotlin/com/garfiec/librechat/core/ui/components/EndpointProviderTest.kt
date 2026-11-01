package com.garfiec.librechat.core.ui.components

import com.garfiec.librechat.core.model.media.ProviderId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EndpointProviderTest {

    @Test
    fun firstClassEndpointsDrawTheirBrand() {
        assertEquals(ProviderId.OPENAI, endpointProvider("openAI"))
        assertEquals(ProviderId.AZURE, endpointProvider("azureOpenAI"))
        assertEquals(ProviderId.ANTHROPIC, endpointProvider("anthropic"))
        assertEquals(ProviderId.GOOGLE, endpointProvider("google"))
        assertEquals(ProviderId.BEDROCK, endpointProvider("bedrock"))
    }

    @Test
    fun assistantsBorrowTheirPlatformsBrand() {
        assertEquals(ProviderId.OPENAI, endpointProvider("assistants"))
        assertEquals(ProviderId.AZURE, endpointProvider("azureAssistants"))
    }

    @Test
    fun aCustomNameResolvesByName() {
        assertEquals(ProviderId.GROQ, endpointProvider("groq"))
        assertEquals(ProviderId.TOGETHER, endpointProvider("Together.AI"))
    }

    @Test
    fun agentsAndUnknownNamesHaveNoBrand() {
        assertNull(endpointProvider("agents"))
        assertNull(endpointProvider("My Endpoint"))
        assertNull(endpointProvider(null))
    }
}
