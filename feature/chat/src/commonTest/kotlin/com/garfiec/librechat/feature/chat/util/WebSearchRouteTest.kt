package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.data.datastore.WebSearchPreference
import com.garfiec.librechat.core.model.Preset
import com.garfiec.librechat.core.model.config.ModelSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WebSearchRouteTest {

    private val searchSpec =
        ModelSpec(name = "s", preset = Preset(endpoint = "openAI", model = "gpt-4.1"), webSearch = true)
    private val groundedSpec = ModelSpec(
        name = "g",
        preset = Preset(endpoint = "anthropic", model = "claude-sonnet-4-5", webSearch = true),
    )
    private val bothSpec = searchSpec.copy(name = "b", preset = groundedSpec.preset)
    private val plainSpec = ModelSpec(name = "p", preset = Preset(endpoint = "openAI", model = "gpt-4.1"))

    private fun route(
        on: Boolean = true,
        sendsEphemeralTools: Boolean = true,
        spec: ModelSpec? = null,
        comparing: Boolean = false,
        preference: WebSearchPreference = WebSearchPreference.LIBRECHAT_FIRST,
        librechatUsable: Boolean = true,
        builtInAvailable: Boolean = true,
        librechatChecking: Boolean = false,
    ) = webSearchRoute(on, sendsEphemeralTools, spec, comparing, preference, librechatUsable, builtInAvailable, librechatChecking)

    @Test
    fun `while LibreChat search is being checked a route that would pick it holds`() {
        assertEquals(WebSearchRoute.CHECKING, route(librechatUsable = false, librechatChecking = true))
        assertEquals(
            WebSearchRoute.CHECKING,
            route(comparing = true, librechatUsable = false, librechatChecking = true),
        )
        assertEquals(
            WebSearchRoute.CHECKING,
            route(spec = groundedSpec, librechatUsable = false, builtInAvailable = false, librechatChecking = true),
        )
    }

    @Test
    fun `a check in progress never holds a route that picks the built-in search`() {
        assertEquals(
            WebSearchRoute.BUILT_IN,
            route(preference = WebSearchPreference.BUILT_IN_FIRST, librechatUsable = false, librechatChecking = true),
        )
        assertEquals(WebSearchRoute.BUILT_IN, route(spec = groundedSpec, librechatUsable = false, librechatChecking = true))
        assertEquals(WebSearchRoute.NONE, route(on = false, librechatUsable = false, librechatChecking = true))
    }

    @Test
    fun `off sends nothing`() {
        assertEquals(WebSearchRoute.NONE, route(on = false))
    }

    @Test
    fun `a saved agent sends nothing as it runs its own tools`() {
        assertEquals(WebSearchRoute.NONE, route(sendsEphemeralTools = false, spec = searchSpec))
    }

    @Test
    fun `LibreChat search first when it is set up`() {
        assertEquals(WebSearchRoute.LIBRECHAT, route())
    }

    @Test
    fun `the built-in search when LibreChat search is not set up`() {
        assertEquals(WebSearchRoute.BUILT_IN, route(librechatUsable = false))
    }

    @Test
    fun `LibreChat search on an endpoint with no built-in search`() {
        assertEquals(WebSearchRoute.LIBRECHAT, route(preference = WebSearchPreference.BUILT_IN_FIRST, builtInAvailable = false))
    }

    @Test
    fun `nothing when neither search can run`() {
        assertEquals(WebSearchRoute.NONE, route(librechatUsable = false, builtInAvailable = false))
    }

    @Test
    fun `built-in first when the user prefers it`() {
        assertEquals(WebSearchRoute.BUILT_IN, route(preference = WebSearchPreference.BUILT_IN_FIRST))
    }

    @Test
    fun `a spec's webSearch is LibreChat search whatever is set up or preferred`() {
        assertEquals(
            WebSearchRoute.LIBRECHAT,
            route(spec = searchSpec, preference = WebSearchPreference.BUILT_IN_FIRST, librechatUsable = false),
        )
    }

    @Test
    fun `a spec preset's web_search is the built-in search whatever is preferred`() {
        assertEquals(WebSearchRoute.BUILT_IN, route(spec = groundedSpec))
    }

    @Test
    fun `a spec preset's web_search on an endpoint with no built-in search runs LibreChat search`() {
        assertEquals(WebSearchRoute.LIBRECHAT, route(spec = groundedSpec, builtInAvailable = false))
    }

    @Test
    fun `a spec preset's web_search with neither search sends nothing`() {
        assertEquals(WebSearchRoute.NONE, route(spec = groundedSpec, builtInAvailable = false, librechatUsable = false))
    }

    @Test
    fun `a spec with both runs LibreChat search`() {
        assertEquals(WebSearchRoute.LIBRECHAT, route(spec = bothSpec))
    }

    @Test
    fun `a spec that sets neither follows the preference`() {
        assertEquals(WebSearchRoute.BUILT_IN, route(spec = plainSpec, preference = WebSearchPreference.BUILT_IN_FIRST))
    }

    @Test
    fun `a comparison runs LibreChat search so both models search`() {
        assertEquals(WebSearchRoute.LIBRECHAT, route(comparing = true, preference = WebSearchPreference.BUILT_IN_FIRST))
    }

    @Test
    fun `a comparison without LibreChat search runs the built-in search on the main model`() {
        assertEquals(WebSearchRoute.BUILT_IN, route(comparing = true, librechatUsable = false))
    }

    @Test
    fun `a comparison on a spec keeps the spec's choice`() {
        assertEquals(WebSearchRoute.BUILT_IN, route(comparing = true, spec = groundedSpec))
    }

    @Test
    fun `providers with a built-in search`() {
        assertEquals(BuiltInSearchProvider.OPENAI, builtInSearchProvider("openAI", emptyList()))
        assertEquals(BuiltInSearchProvider.AZURE_OPENAI, builtInSearchProvider("azureOpenAI", emptyList()))
        assertEquals(BuiltInSearchProvider.ANTHROPIC, builtInSearchProvider("anthropic", emptyList()))
        assertEquals(BuiltInSearchProvider.GOOGLE, builtInSearchProvider("google", emptyList()))
        assertEquals(BuiltInSearchProvider.OPENROUTER, builtInSearchProvider("OpenRouter", emptyList()))
        assertEquals(BuiltInSearchProvider.OPENROUTER, builtInSearchProvider("my-openrouter", emptyList()))
    }

    @Test
    fun `other endpoints have none`() {
        assertNull(builtInSearchProvider("bedrock", emptyList()))
        assertNull(builtInSearchProvider("Ollama", emptyList()))
        assertNull(builtInSearchProvider("agents", emptyList()))
        assertNull(builtInSearchProvider(null, emptyList()))
    }

    @Test
    fun `none when the server drops web_search`() {
        assertNull(builtInSearchProvider("openAI", listOf("web_search")))
    }
}
