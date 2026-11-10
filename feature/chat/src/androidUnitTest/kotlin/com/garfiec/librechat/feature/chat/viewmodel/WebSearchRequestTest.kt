package com.garfiec.librechat.feature.chat.viewmodel

import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.common.ToolConstants
import com.garfiec.librechat.core.data.datastore.WebSearchPreference
import com.garfiec.librechat.core.data.repository.ChatPayloadBuilder
import com.garfiec.librechat.core.model.CustomParams
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.Preset
import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.core.model.config.ModelSpecs
import com.garfiec.librechat.core.network.di.librechatJson
import com.garfiec.librechat.core.ui.components.ModelParameters
import com.garfiec.librechat.feature.chat.util.BuiltInSearchProvider
import com.garfiec.librechat.feature.chat.util.SpecTools
import com.garfiec.librechat.feature.chat.util.WebSearchKind
import com.garfiec.librechat.feature.chat.util.WebSearchRoute
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What one tap of the web-search control puts on the wire: LibreChat's search as
 * `ephemeralAgent.web_search`, or the provider's as the top-level `web_search`, never both. Read
 * from the body [ChatPayloadBuilder.toBody] encodes from the same [ChatRequestBuilder] outputs every
 * send path uses.
 */
class WebSearchRequestTest {

    private val grounded = ModelSpec(
        name = "grounded",
        preset = Preset(endpoint = "anthropic", model = "claude-sonnet-4-5", webSearch = true),
    )
    private val searching =
        ModelSpec(name = "searching", preset = Preset(endpoint = "openAI", model = "gpt-4.1"), webSearch = true)
    private val groundedCustom = ModelSpec(
        name = "grounded-custom",
        preset = Preset(endpoint = "Ollama", model = "llama3", webSearch = true),
    )
    private val specs = ModelSpecs(list = listOf(grounded, searching, groundedCustom))

    private fun state(
        choice: ModelChoice = ModelChoice.Raw("openAI", "gpt-4.1"),
        on: Boolean = true,
        librechatReady: Boolean = true,
        roleAllows: Boolean = true,
        interfaceAllows: Boolean = true,
        checking: Boolean = false,
        first: WebSearchPreference = WebSearchPreference.LIBRECHAT_FIRST,
        comparing: Boolean = false,
        specTools: SpecTools? = null,
        dropWebSearch: Boolean = false,
        endpointConfigs: Map<String, EndpointConfig> = emptyMap(),
        enforce: Boolean = false,
        keepsBuiltIn: Boolean = false,
    ) = ChatUiState(
        selection = ModelSelectionState(
            choice = choice,
            modelSpecs = specs.copy(enforce = enforce),
            endpointConfigs = endpointConfigs,
            modelParameters = ModelParameters.DEFAULT.copy(webSearch = on),
            specTools = specTools,
            keepsBuiltInSearch = keepsBuiltIn,
        ),
        gates = FeatureGatesState(
            webSearchEnabled = roleAllows && interfaceAllows,
            webSearchPermitted = roleAllows,
            librechatSearchReady = librechatReady,
            librechatSearchChecking = checking,
            dropParamsMap = if (dropWebSearch) mapOf(choice.endpoint to JsonArray(listOf(JsonPrimitive("web_search")))) else null,
        ),
        prefs = ChatPrefsState(webSearchFirst = first),
        comparisonState = ComparisonState(isEnabled = comparing),
    )

    private data class Wire(val librechat: Boolean, val builtIn: Boolean)

    private fun wire(state: ChatUiState): Wire {
        val body = body(state)
        return Wire(
            librechat = (body["ephemeralAgent"] as? JsonObject)?.get(ToolConstants.WEB_SEARCH) == JsonPrimitive(true),
            builtIn = body[ToolConstants.WEB_SEARCH] == JsonPrimitive(true),
        )
    }

    private fun body(state: ChatUiState): JsonObject {
        val builder = ChatRequestBuilder { state }
        val request = ChatPayloadBuilder.build(
            text = "hi",
            conversationId = null,
            endpoint = state.selectedEndpoint,
            model = state.selectedModel,
            webSearch = builder.builtInWebSearch(),
            ephemeralAgent = builder.buildEphemeralAgent(),
        )
        return ChatPayloadBuilder.toBody(librechatJson, request, builder.buildModelParams())
    }

    private val librechat = Wire(librechat = true, builtIn = false)
    private val builtIn = Wire(librechat = false, builtIn = true)
    private val nothing = Wire(librechat = false, builtIn = false)

    @Test
    fun `while the key check is out, a send that would run LibreChat search runs none`() {
        val state = state(librechatReady = false, checking = true)

        assertEquals(WebSearchRoute.CHECKING, state.webSearchRoute)
        assertEquals(nothing, wire(state))
        assertTrue(state.webSearchAvailable)
    }

    @Test
    fun `while the key check is out, built-in first still runs the built-in search`() {
        val state = state(librechatReady = false, checking = true, first = WebSearchPreference.BUILT_IN_FIRST)

        assertEquals(builtIn, wire(state))
    }

    @Test
    fun `interface webSearch off rules out LibreChat search only, as web's parameters panel does`() {
        val state = state(interfaceAllows = false)

        assertEquals(builtIn, wire(state))
        assertTrue(state.webSearchAvailable)
    }

    @Test
    fun `interface webSearch off on an endpoint with no built-in search hides the control`() {
        val state = state(choice = ModelChoice.Raw("Groq", "llama"), interfaceAllows = false)

        assertEquals(nothing, wire(state))
        assertFalse(state.webSearchAvailable)
    }

    @Test
    fun `openAI with LibreChat search set up runs it alone, off the Responses API`() {
        assertEquals(librechat, wire(state()))
    }

    @Test
    fun `openAI without LibreChat search runs the built-in one`() {
        assertEquals(builtIn, wire(state(librechatReady = false)))
    }

    @Test
    fun `anthropic and google run LibreChat search when it is set up`() {
        assertEquals(librechat, wire(state(choice = ModelChoice.Raw("anthropic", "claude-sonnet-4-5"))))
        assertEquals(librechat, wire(state(choice = ModelChoice.Raw("google", "gemini-2.5-pro"))))
    }

    @Test
    fun `a custom endpoint runs LibreChat search, never the Responses API`() {
        assertEquals(librechat, wire(state(choice = ModelChoice.Raw("Ollama", "llama3"))))
    }

    @Test
    fun `a custom endpoint without LibreChat search hides the control and sends nothing`() {
        val state = state(choice = ModelChoice.Raw("Ollama", "llama3"), librechatReady = false)

        assertEquals(nothing, wire(state))
        assertFalse(state.webSearchAvailable)
    }

    @Test
    fun `the web search chip hides where neither search can run`() {
        val on = state(choice = ModelChoice.Raw("Ollama", "llama3"), librechatReady = false)

        assertFalse(ToolConstants.WEB_SEARCH in on.effectiveEnabledTools)
        assertTrue(ToolConstants.WEB_SEARCH in state().effectiveEnabledTools)
    }

    @Test
    fun `OpenRouter has a built-in search`() {
        val base = state(choice = ModelChoice.Raw("OpenRouter", "x/y"), librechatReady = false)
        val custom = base.copy(
            selection = base.selection.copy(endpointConfigs = mapOf("OpenRouter" to EndpointConfig(type = "custom"))),
        )

        // A custom endpoint takes OpenAI's parameter list, which has the web_search row, as on web.
        assertEquals(builtIn, wire(custom))
    }

    @Test
    fun `a role without web search sees no control and sends nothing`() {
        val state = state(roleAllows = false)

        assertEquals(nothing, wire(state))
        assertFalse(state.webSearchAvailable)
    }

    @Test
    fun `a server that drops web_search leaves LibreChat search`() {
        assertEquals(nothing, wire(state(librechatReady = false, dropWebSearch = true)))
        assertEquals(librechat, wire(state(dropWebSearch = true, first = WebSearchPreference.BUILT_IN_FIRST)))
    }

    @Test
    fun `built-in first when the user prefers it`() {
        assertEquals(builtIn, wire(state(first = WebSearchPreference.BUILT_IN_FIRST)))
    }

    @Test
    fun `off sends neither`() {
        assertEquals(nothing, wire(state(on = false)))
    }

    @Test
    fun `a saved agent sends neither`() {
        assertEquals(nothing, wire(state(choice = ModelChoice.Raw(EndpointConstants.AGENTS, "agent_1"))))
        assertNull(body(state(choice = ModelChoice.Raw(EndpointConstants.AGENTS, "agent_1")))[ToolConstants.WEB_SEARCH])
    }

    @Test
    fun `a send without the built-in search says web_search false, so an admin default can't turn it on`() {
        assertEquals(JsonPrimitive(false), body(state(on = false))[ToolConstants.WEB_SEARCH])
        assertEquals(JsonPrimitive(false), body(state())[ToolConstants.WEB_SEARCH])
        assertEquals(librechat, wire(state()))
        val custom = state(
            choice = ModelChoice.Raw("Ollama", "llama3"),
            on = false,
            endpointConfigs = mapOf("Ollama" to EndpointConfig(type = "custom")),
        )
        assertEquals(JsonPrimitive(false), body(custom)[ToolConstants.WEB_SEARCH])
    }

    @Test
    fun `no web_search goes where the parameters have none or the server drops it`() {
        assertNull(body(state(choice = ModelChoice.Raw("bedrock", "anthropic.claude-3"), on = false))[ToolConstants.WEB_SEARCH])
        assertNull(body(state(on = false, dropWebSearch = true))[ToolConstants.WEB_SEARCH])
    }

    @Test
    fun `an anthropic spec with preset web_search runs Claude's search, without LibreChat's tool`() {
        val state = state(choice = ModelChoice.Spec("grounded", "anthropic", "claude-sonnet-4-5"), on = false)

        assertEquals(builtIn, wire(state))
        assertEquals(WebSearchKind(WebSearchRoute.BUILT_IN, BuiltInSearchProvider.ANTHROPIC), state.webSearchKind)
    }

    @Test
    fun `the spec's preset search can be turned off`() {
        val off = SpecTools("grounded", added = emptySet())
        val state = state(choice = ModelChoice.Spec("grounded", "anthropic", "claude-sonnet-4-5"), specTools = off)

        assertEquals(nothing, wire(state))
    }

    @Test
    fun `a spec's webSearch runs LibreChat search alone, even on openAI`() {
        val state =
            state(choice = ModelChoice.Spec("searching", "openAI", "gpt-4.1"), on = false, librechatReady = false)

        assertEquals(librechat, wire(state))
        assertTrue(state.webSearchAvailable)
    }

    @Test
    fun `a comparison runs LibreChat search whatever the preference`() {
        assertEquals(librechat, wire(state(comparing = true, first = WebSearchPreference.BUILT_IN_FIRST)))
    }

    @Test
    fun `agents capabilities without web_search rule out LibreChat search`() {
        val agents = mapOf(EndpointConstants.AGENTS to EndpointConfig(capabilities = listOf("execute_code")))

        assertEquals(builtIn, wire(state(endpointConfigs = agents)))
        val custom = state(choice = ModelChoice.Raw("Ollama", "llama3"), endpointConfigs = agents)
        assertEquals(nothing, wire(custom))
        assertFalse(custom.webSearchAvailable)
    }

    @Test
    fun `agents capabilities not stated leave LibreChat search, as the server falls back to defaults`() {
        val unstated = mapOf(EndpointConstants.AGENTS to EndpointConfig(capabilities = null))

        assertEquals(librechat, wire(state(endpointConfigs = unstated)))
        val stated = mapOf(EndpointConstants.AGENTS to EndpointConfig(capabilities = listOf("web_search")))
        assertEquals(librechat, wire(state(endpointConfigs = stated)))
    }

    @Test
    fun `an empty agents capabilities list rules out LibreChat search, as the server keeps it empty`() {
        val none = mapOf(EndpointConstants.AGENTS to EndpointConfig(capabilities = emptyList()))

        assertEquals(builtIn, wire(state(endpointConfigs = none)))
        val custom = state(choice = ModelChoice.Raw("Ollama", "llama3"), endpointConfigs = none)
        assertEquals(nothing, wire(custom))
        assertFalse(custom.webSearchAvailable)
    }

    @Test
    fun `a spec's webSearch without the capability or role shows no control and searches nothing`() {
        val spec = ModelChoice.Spec("searching", "openAI", "gpt-4.1")
        val none = mapOf(EndpointConstants.AGENTS to EndpointConfig(capabilities = emptyList()))

        val noCapability = state(choice = spec, on = false, endpointConfigs = none)
        assertEquals(nothing, wire(noCapability))
        assertFalse(noCapability.webSearchAvailable)
        assertFalse(ToolConstants.WEB_SEARCH in noCapability.effectiveEnabledTools)

        val noRole = state(choice = spec, on = false, roleAllows = false)
        assertEquals(nothing, wire(noRole))
        assertFalse(noRole.webSearchAvailable)
    }

    @Test
    fun `a custom endpoint the server serves as OpenRouter has its built-in search`() {
        val router = mapOf(
            "Router" to EndpointConfig(type = "custom", customParams = CustomParams(defaultParamsEndpoint = "openrouter")),
        )
        val state = state(choice = ModelChoice.Raw("Router", "x/y"), librechatReady = false, endpointConfigs = router)

        assertEquals(builtIn, wire(state))
        assertEquals(WebSearchKind(WebSearchRoute.BUILT_IN, BuiltInSearchProvider.OPENROUTER), state.webSearchKind)
    }

    @Test
    fun `an OpenRouter-named endpoint on Anthropic's parameters runs Claude's search`() {
        val configs = mapOf(
            "openrouter-x" to EndpointConfig(type = "custom", customParams = CustomParams(defaultParamsEndpoint = "anthropic")),
        )
        val state =
            state(choice = ModelChoice.Raw("openrouter-x", "claude"), librechatReady = false, endpointConfigs = configs)

        assertEquals(BuiltInSearchProvider.ANTHROPIC, state.webSearchKind.provider)
    }

    @Test
    fun `under enforce a spec preset's web_search on a custom endpoint sends the built-in search, not a second one`() {
        val custom = mapOf("Ollama" to EndpointConfig(type = "custom"))
        val state = state(
            choice = ModelChoice.Spec("grounded-custom", "Ollama", "llama3"),
            enforce = true,
            endpointConfigs = custom,
        )

        assertEquals(builtIn, wire(state))
        assertEquals(WebSearchKind(WebSearchRoute.BUILT_IN, null), state.webSearchKind)
        // The server strips it where dropParams names it, and without the role.
        val dropped = mapOf("Ollama" to JsonArray(listOf(JsonPrimitive("web_search"))))
        assertEquals(librechat, wire(state.copy(gates = state.gates.copy(dropParamsMap = dropped))))
        val noRole = state.gates.copy(webSearchPermitted = false, webSearchEnabled = false)
        assertEquals(nothing, wire(state.copy(gates = noRole)))
    }

    @Test
    fun `without enforce a spec preset's web_search on a custom endpoint still runs LibreChat search`() {
        val custom = mapOf("Ollama" to EndpointConfig(type = "custom"))
        val state = state(choice = ModelChoice.Spec("grounded-custom", "Ollama", "llama3"), endpointConfigs = custom)

        assertEquals(librechat, wire(state))
    }

    @Test
    fun `a chat whose stored built-in search is on keeps it over LibreChat first`() {
        assertEquals(builtIn, wire(state(keepsBuiltIn = true)))
        // On an endpoint with no built-in search it runs the other one, as rule 2 does.
        assertEquals(librechat, wire(state(choice = ModelChoice.Raw("Ollama", "llama3"), keepsBuiltIn = true)))
    }

    @Test
    fun `the caption names the search the control would run`() {
        assertEquals(WebSearchKind(WebSearchRoute.LIBRECHAT, BuiltInSearchProvider.OPENAI), state(on = false).webSearchKind)
        assertEquals(WebSearchRoute.BUILT_IN, state(on = false, librechatReady = false).webSearchKind.route)
        assertNull(state(choice = ModelChoice.Raw("Ollama", "llama3"), librechatReady = false).builtInSearchProvider)
    }
}
