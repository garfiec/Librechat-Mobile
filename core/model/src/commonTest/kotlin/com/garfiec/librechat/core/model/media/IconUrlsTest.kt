package com.garfiec.librechat.core.model.media

import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.Preset
import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.core.model.iconEndpoint
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
        assertEquals(IconSource("openAI", "https://srv/images/a.png"), resolveIconSource("/images/a.png", "openAI", "https://srv"))
        assertEquals(IconSource("openAI", "https://cdn/x"), resolveIconSource("//cdn/x", "openAI", "https://srv"))
        assertEquals(IconSource("groq", null), resolveIconSource("groq", "openAI", "https://srv"))
        assertEquals(IconSource("openAI", null), resolveIconSource("", "openAI", "https://srv"))
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
        assertEquals("openAI", image.iconEndpoint)

        // A spec's provider name: its glyph, and that endpoint's configured icon.
        val named = Conversation(endpoint = "openAI", iconURL = "groq")
        assertEquals("https://cfg/groq.png", named.resolveEndpointIconUrl(configs, SERVER))
        assertEquals("groq", named.iconEndpoint)

        // A name the config doesn't list keeps the endpoint's configured icon.
        val unlisted = Conversation(endpoint = "openAI", iconURL = "mistral")
        assertEquals("https://cfg/openai.png", unlisted.resolveEndpointIconUrl(configs, SERVER))

        val blank = Conversation(endpoint = "openAI", iconURL = "")
        assertEquals("https://cfg/openai.png", blank.resolveEndpointIconUrl(configs, SERVER))
        assertEquals("openAI", blank.iconEndpoint)
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
