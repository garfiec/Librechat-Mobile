package com.garfiec.librechat.core.model.media

import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.Preset
import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.core.model.iconEndpoint
import com.garfiec.librechat.core.model.iconProvider
import com.garfiec.librechat.core.model.resolveEndpointIconUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IconUrlsTest {

    @Test
    fun imageFormsAreImages() {
        listOf(
            "https://host/a.png",
            "HTTPS://host/a",
            "http://host/a",
            "data:image/png;base64,AAAA",
            "//cdn.host/icon",
            "/images/abc.webp",
            "logo.PNG",
            "icon.svg?v=2",
            "pic.jpeg#frag",
        ).forEach { assertTrue(isImageUrl(it), it) }
    }

    @Test
    fun namesAndBlanksAreNot() {
        listOf("openAI", "groq", "agents", "", " ", null, "data:text/plain,hi", "png", "///x").forEach {
            assertFalse(isImageUrl(it), "$it")
        }
    }

    @Test
    fun resolveJoinsPathsAndKeepsNames() {
        val openAi = ProviderId.OPENAI
        assertEquals(IconSource("openAI", "https://srv/images/a.png", openAi), resolveIconSource("/images/a.png", "openAI", "https://srv"))
        assertEquals(IconSource("openAI", "https://cdn/x", openAi), resolveIconSource("//cdn/x", "openAI", "https://srv"))
        assertEquals(IconSource("groq", null, ProviderId.GROQ), resolveIconSource("groq", "openAI", "https://srv"))
        assertEquals(IconSource("openAI", null, openAi), resolveIconSource("", "openAI", "https://srv"))
        assertEquals(IconSource("agents", null), resolveIconSource(null, "agents", "https://srv"))
    }

    @Test
    fun resolveTakesTheServedProviderOfTheSpecEndpoint() {
        val configs = mapOf("Proxy" to EndpointConfig(providerId = "openrouter"))
        // The endpoint's own name and an unknown icon name fall to what the server resolved.
        assertEquals(IconSource("Proxy", null, ProviderId.OPENROUTER), resolveIconSource(null, "Proxy", SERVER, configs))
        assertEquals(IconSource("Fancy", null, ProviderId.OPENROUTER), resolveIconSource("Fancy", "Proxy", SERVER, configs))
        // A name that is a brand beats it.
        assertEquals(ProviderId.MISTRAL, resolveIconSource("mistral", "Proxy", SERVER, configs).provider)
        // An image keeps the endpoint's brand, for when it fails to load.
        assertEquals(
            IconSource("Proxy", "$SERVER/i.png", ProviderId.OPENROUTER),
            resolveIconSource("/i.png", "Proxy", SERVER, configs),
        )
        // A spec with no icon of its own borrows its endpoint's name: the served brand still wins.
        val named = mapOf("Mistral" to EndpointConfig(providerId = "openrouter"))
        assertEquals(ProviderId.OPENROUTER, resolveIconSource("Mistral", "Mistral", SERVER, named).provider)
        assertEquals(ProviderId.OPENROUTER, resolveIconSource(null, "Mistral", SERVER, named).provider)
        // A configured brand iconURL counts; a configured image doesn't suppress the served brand.
        val branded = mapOf(
            "B" to EndpointConfig(iconURL = "mistral"),
            "P" to EndpointConfig(iconURL = "/p.png", providerId = "groq"),
        )
        assertEquals(ProviderId.MISTRAL, resolveIconSource("B", "B", SERVER, branded).provider)
        assertEquals(ProviderId.GROQ, resolveIconSource(null, "P", SERVER, branded).provider)
        // Without the configs, nothing knows Proxy.
        assertNull(resolveIconSource(null, "Proxy", SERVER).provider)
    }

    @Test
    fun specIconChainFollowsWeb() {
        val agentPreset = Preset(endpoint = "agents", agentId = "a1")
        assertEquals("own", ModelSpec("s", iconURL = "own", preset = agentPreset.copy(iconURL = "p")).iconValue("av"))
        // An empty iconURL is unset, so the preset's applies.
        assertEquals("p", ModelSpec("s", iconURL = "", preset = agentPreset.copy(iconURL = "p")).iconValue("av"))
        assertEquals("av", ModelSpec("s", preset = agentPreset).iconValue("av"))
        assertEquals("agents", ModelSpec("s", preset = agentPreset).iconValue(null))
        // An avatar only stands in for an agent spec.
        assertEquals("openAI", ModelSpec("s", preset = Preset(endpoint = "openAI", model = "m")).iconValue("av"))
        assertNull(ModelSpec("s").iconValue())
    }

    @Test
    fun conversationIconFollowsWebEndpointIcon() {
        val configs = mapOf(
            "openAI" to EndpointConfig(iconURL = "https://cfg/openai.png"),
            "groq" to EndpointConfig(iconURL = "https://cfg/groq.png"),
        )
        val image = Conversation(endpoint = "openAI", iconURL = "https://convo/i.png")
        assertEquals("https://convo/i.png", image.resolveEndpointIconUrl(configs, SERVER))
        assertEquals("openAI", image.iconEndpoint(configs))

        // An endpoint key in iconURL: that endpoint's glyph and configured icon.
        val named = Conversation(endpoint = "openAI", iconURL = "groq")
        assertEquals("https://cfg/groq.png", named.resolveEndpointIconUrl(configs, SERVER))
        assertEquals("groq", named.iconEndpoint(configs))

        // A name the config doesn't list is ignored, as on web: the endpoint and its configured icon.
        val unlisted = Conversation(endpoint = "openAI", iconURL = "mistral")
        assertEquals("https://cfg/openai.png", unlisted.resolveEndpointIconUrl(configs, SERVER))
        assertEquals("openAI", unlisted.iconEndpoint(configs))

        val blank = Conversation(endpoint = "openAI", iconURL = "")
        assertEquals("https://cfg/openai.png", blank.resolveEndpointIconUrl(configs, SERVER))
        assertEquals("openAI", blank.iconEndpoint(configs))
    }

    @Test
    fun conversationProviderFollowsTheIconEndpoint() {
        val configs = mapOf(
            "Proxy" to EndpointConfig(providerId = "openrouter"),
            "Branded" to EndpointConfig(iconURL = "mistral"),
            "Pictured" to EndpointConfig(iconURL = "https://cfg/p.png", providerId = "groq"),
        )
        assertEquals(ProviderId.OPENROUTER, Conversation(endpoint = "Proxy").iconProvider(configs))
        assertEquals(ProviderId.OPENROUTER, Conversation(endpoint = "openAI", iconURL = "Proxy").iconProvider(configs))
        // A configured iconURL naming a brand is no image: null URL, the brand's art.
        val branded = Conversation(endpoint = "Branded")
        assertEquals(ProviderId.MISTRAL, branded.iconProvider(configs))
        assertNull(branded.resolveEndpointIconUrl(configs, SERVER))
        // An image suppresses the art.
        assertNull(Conversation(endpoint = "Pictured").iconProvider(configs))
        assertEquals(ProviderId.ANTHROPIC, Conversation(endpoint = "anthropic").iconProvider(configs))
        assertNull(Conversation(endpoint = "agents").iconProvider(configs))
    }

    @Test
    fun conversationIconPathsJoinTheServer() {
        fun icon(value: String) =
            Conversation(endpoint = "openAI", iconURL = value).resolveEndpointIconUrl(emptyMap(), SERVER)
        assertEquals("$SERVER/images/a.png", icon("/images/a.png"))
        assertEquals("$SERVER/logo.png", icon("logo.png"))
        assertEquals("https://cdn.example/x.png", icon("//cdn.example/x.png"))
        // The endpoint's configured icon joins the server too.
        val configs = mapOf("openAI" to EndpointConfig(iconURL = "/assets/openai.svg"))
        assertEquals("$SERVER/assets/openai.svg", Conversation(endpoint = "openAI").resolveEndpointIconUrl(configs, SERVER))
    }

    private companion object {
        const val SERVER = "https://srv"
    }
}
