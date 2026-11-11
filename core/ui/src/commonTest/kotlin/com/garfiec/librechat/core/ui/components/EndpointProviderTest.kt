package com.garfiec.librechat.core.ui.components

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.garfiec.librechat.core.model.media.ProviderId
import com.garfiec.librechat.core.ui.theme.contrast
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test
    fun nearBlackMonogramsTakeTheInverseTileOnADarkSurface() {
        val scheme = darkColorScheme()
        listOf(ProviderId.XAI, ProviderId.VERCEL).forEach {
            assertEquals(MonogramColors(scheme.inverseSurface, scheme.inverseOnSurface), monogramColors(it, true, scheme), "$it")
        }
    }

    @Test
    fun nearBlackMonogramsKeepTheirTintOnALightSurface() {
        val scheme = lightColorScheme()
        listOf(ProviderId.XAI, ProviderId.VERCEL).forEach {
            val colors = monogramColors(it, false, scheme)
            assertEquals(Color.White, colors.letters, "$it")
            assertNotEquals(scheme.inverseSurface, colors.tile, "$it")
        }
    }

    @Test
    fun tintedMonogramsHoldWhiteLettersAndAnEdgeOnARaisedDarkSurface() {
        val raisedDark = Color(0xFF282A2F)
        val tinted = ProviderId.entries.map { it to monogramColors(it, true, darkColorScheme()) }
            .filter { (_, colors) -> colors.letters == Color.White }
        assertEquals(4, tinted.size)
        tinted.forEach { (provider, colors) ->
            assertTrue(contrast(colors.tile, Color.White) >= 5f, "$provider letters")
            assertTrue(contrast(colors.tile, raisedDark) >= 2.7f, "$provider edge")
        }
    }

    @Test
    fun untintedMonogramsStayNeutral() {
        val scheme = darkColorScheme()
        assertEquals(MonogramColors(scheme.surfaceVariant, scheme.onSurfaceVariant), monogramColors(ProviderId.GROQ, true, scheme))
    }
}
