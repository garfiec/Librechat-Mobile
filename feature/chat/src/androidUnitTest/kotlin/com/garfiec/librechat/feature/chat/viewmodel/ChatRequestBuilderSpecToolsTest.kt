package com.garfiec.librechat.feature.chat.viewmodel

import com.garfiec.librechat.core.common.ToolConstants
import com.garfiec.librechat.core.model.Preset
import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.core.model.config.ModelSpecs
import com.garfiec.librechat.core.ui.components.ModelParameters
import com.garfiec.librechat.feature.chat.util.SpecTools
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The tools a spec chat sends: the spec's own (the server ORs them in anyway, and web sends them) plus
 * what the user added on top, never the user's global toggles; under `hideBadgeRow`, the spec's alone.
 */
class ChatRequestBuilderSpecToolsTest {

    private val tools = ModelSpec(
        name = "tools",
        preset = Preset(endpoint = "openAI", model = "gpt-4.1"),
        webSearch = true,
        mcpServers = listOf("docs"),
    )
    private val plain = ModelSpec(name = "plain", preset = Preset(endpoint = "openAI", model = "gpt-4o"))
    private val locked = tools.copy(name = "locked", hideBadgeRow = true, artifacts = JsonPrimitive(true))
    private val specs = ModelSpecs(list = listOf(tools, plain, locked))

    /**
     * The user's own toggles: code execution, their MCP server, and web search in the parameters.
     * LibreChat search is set up on the server.
     */
    private fun state(choice: ModelChoice, specTools: SpecTools? = null, webSearch: Boolean = false) = ChatUiState(
        selection = ModelSelectionState(
            choice = choice,
            modelSpecs = specs,
            enabledTools = setOf(ToolConstants.CODE_INTERPRETER),
            selectedMcpServerNames = setOf("mine"),
            modelParameters = ModelParameters.DEFAULT.copy(webSearch = webSearch),
            specTools = specTools,
        ),
        gates = FeatureGatesState(librechatSearchReady = true),
    )

    private val onTools = ModelChoice.Spec("tools", "openAI", "gpt-4.1")
    private val onPlain = ModelChoice.Spec("plain", "openAI", "gpt-4o")
    private val onLocked = ModelChoice.Spec("locked", "openAI", "gpt-4.1")

    @Test
    fun `a spec chat sends the spec's tools, not the user's`() {
        val agent = assertNotNull(ChatRequestBuilder { state(onTools) }.buildEphemeralAgent())

        assertEquals(true, agent.webSearch)
        assertEquals(listOf("docs"), agent.mcp)
        assertNull(agent.executeCode)
    }

    @Test
    fun `tools added on top of the spec are sent with its own`() {
        val added = SpecTools("tools", added = setOf(ToolConstants.FILE_SEARCH), addedMcp = setOf("jira"))

        val agent = assertNotNull(ChatRequestBuilder { state(onTools, added) }.buildEphemeralAgent())

        assertEquals(true, agent.fileSearch)
        assertEquals(true, agent.webSearch)
        assertEquals(setOf("docs", "jira"), agent.mcp?.toSet())
    }

    @Test
    fun `another spec's additions are not sent`() {
        val stale = SpecTools("tools", added = setOf(ToolConstants.FILE_SEARCH))

        assertNull(ChatRequestBuilder { state(onPlain, stale) }.buildEphemeralAgent())
    }

    @Test
    fun `the user's web search stays off the wire under a spec that has none`() {
        val state = state(onPlain, webSearch = true)

        assertNull(ChatRequestBuilder { state }.buildEphemeralAgent())
        assertFalse(state.selection.sendParameters.webSearch)
        assertTrue(state.modelParameters.webSearch)
    }

    @Test
    fun `the spec's web search is LibreChat's alone, not the provider's too`() {
        val builder = ChatRequestBuilder { state(onTools) }

        assertFalse(builder.builtInWebSearch())
        assertNull(builder.buildModelParams()?.get(ToolConstants.WEB_SEARCH))
        // The control still shows it on.
        assertTrue(state(onTools).selection.sendParameters.webSearch)
    }

    @Test
    fun `under hideBadgeRow only the spec's own tools and artifacts are sent`() {
        val added = SpecTools("locked", added = setOf(ToolConstants.FILE_SEARCH))
        val state = state(onLocked, added)

        val agent = assertNotNull(ChatRequestBuilder { state }.buildEphemeralAgent())

        assertEquals(true, agent.webSearch)
        assertEquals(listOf("docs"), agent.mcp)
        assertEquals("default", agent.artifacts)
        assertNull(agent.fileSearch)
        assertFalse(state.toolbarShown)
        assertFalse(state.chatInputGates.showEphemeralTools)
    }

    @Test
    fun `a raw pick sends the user's own toggles`() {
        val state = state(ModelChoice.Raw("openAI", "gpt-4o"), webSearch = true)

        val agent = assertNotNull(ChatRequestBuilder { state }.buildEphemeralAgent())

        assertEquals(true, agent.executeCode)
        assertEquals(true, agent.webSearch)
        assertEquals(listOf("mine"), agent.mcp)
    }
}
