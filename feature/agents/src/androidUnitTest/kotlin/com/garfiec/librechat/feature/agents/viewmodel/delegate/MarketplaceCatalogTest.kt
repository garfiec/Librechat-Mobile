package com.garfiec.librechat.feature.agents.viewmodel.delegate

import com.garfiec.librechat.core.common.ToolConstants
import com.garfiec.librechat.core.data.repository.ToolFavoritesRepository
import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.SkillSummary
import com.garfiec.librechat.core.model.mcp.McpTool
import com.garfiec.librechat.core.ui.websearch.WebSearchKeysState
import com.garfiec.librechat.feature.agents.AgentToolDisplayData
import com.garfiec.librechat.feature.agents.components.model.MarketplaceKind
import com.garfiec.librechat.feature.agents.viewmodel.AgentEditorStateHandle
import com.garfiec.librechat.feature.agents.viewmodel.AgentEditorUiState
import com.garfiec.librechat.feature.agents.viewmodel.applyAgentData
import com.garfiec.librechat.feature.agents.viewmodel.buildToolsList
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MarketplaceCatalogTest {

    private fun state() = AgentEditorUiState(
        isCodeInterpreterAvailable = true,
        isWebSearchAvailable = true,
        isSkillsAvailable = true,
        availableTools = listOf(
            AgentToolDisplayData(
                toolId = "wolfram",
                name = "Wolfram",
                description = "Computational knowledge",
                icon = null,
                isAvailable = true,
            ),
        ),
        mcpTools = listOf(
            McpTool(name = "create_issue", description = "Files a ticket", serverName = "jira"),
            McpTool(name = "search", description = "Finds a ticket", serverName = "jira"),
        ),
        availableSkills = listOf(
            SkillSummary(id = "skill-1", name = "code-review", description = "Reviews a diff"),
        ),
    )

    @Test
    fun `catalog mixes every kind into one list`() {
        val catalog = state().marketplaceCatalog()

        assertThat(catalog.map { it.kind }.toSet()).containsExactly(
            MarketplaceKind.BUILTIN,
            MarketplaceKind.TOOL,
            MarketplaceKind.MCP,
            MarketplaceKind.SKILL,
        )
    }

    @Test
    fun `builtins the role may not use are not listed`() {
        val catalog = state()
            .copy(canUseRunCode = false, canUseWebSearch = false, canUseFileSearch = false)
            .marketplaceCatalog()

        val builtinIds = catalog.filter { it.kind == MarketplaceKind.BUILTIN }.map { it.id }
        assertThat(builtinIds).containsNoneOf(ToolConstants.EXECUTE_CODE, ToolConstants.WEB_SEARCH, ToolConstants.FILE_SEARCH)
    }

    @Test
    fun `web search reads needs setup only while the user's own keys are missing`() {
        val missing = WebSearchKeysState(authenticated = false, userCategories = listOf("providers"))
        fun webSearch(keys: WebSearchKeysState?) =
            state().copy(webSearchKeys = keys).marketplaceCatalog().single { it.id == ToolConstants.WEB_SEARCH }

        assertThat(webSearch(missing).needsSetup).isTrue()
        assertThat(webSearch(missing.copy(authenticated = true)).needsSetup).isFalse()
        assertThat(webSearch(WebSearchKeysState(authenticated = true, userCategories = emptyList())).needsSetup).isFalse()
        assertThat(webSearch(null).needsSetup).isFalse()
    }

    @Test
    fun `file search follows its capability`() {
        val builtinIds = state().copy(isFileSearchAvailable = false).marketplaceCatalog()
            .filter { it.kind == MarketplaceKind.BUILTIN }.map { it.id }

        assertThat(builtinIds).doesNotContain(ToolConstants.FILE_SEARCH)
    }

    @Test
    fun `capabilities the server does not offer are not listed`() {
        val catalog = state()
            .copy(isCodeInterpreterAvailable = false, isWebSearchAvailable = false)
            .marketplaceCatalog()

        val builtinIds = catalog.filter { it.kind == MarketplaceKind.BUILTIN }.map { it.id }
        assertThat(builtinIds).doesNotContain(ToolConstants.EXECUTE_CODE)
        assertThat(builtinIds).doesNotContain(ToolConstants.WEB_SEARCH)
        assertThat(builtinIds).contains(ToolConstants.FILE_SEARCH)
    }

    @Test
    fun `every MCP tool on one server pins the same favorite`() {
        val mcp = state().marketplaceCatalog().filter { it.kind == MarketplaceKind.MCP }

        // The star means "pin this server", so two tools from `jira` must not produce two
        // different favorites — otherwise the same control means different things per row.
        assertThat(mcp.map { it.favoriteKey }.toSet()).containsExactly("mcp:jira")
        assertThat(mcp.map { it.itemKey }.toSet()).hasSize(2)
    }

    @Test
    fun `the same tool name on two MCP servers gives two distinct row keys`() {
        val catalog = state().copy(
            mcpTools = listOf(
                McpTool(name = "show_trip_budget", serverName = "trip-budget"),
                McpTool(name = "show_trip_budget", serverName = "trip-raw"),
            ),
        ).marketplaceCatalog()

        // The picker's LazyColumn keys rows by itemKey; a repeat crashes it.
        val keys = catalog.filter { it.kind == MarketplaceKind.MCP }.map { it.itemKey }
        assertThat(keys).containsExactly(
            "mcp:show_trip_budget_mcp_trip-budget",
            "mcp:show_trip_budget_mcp_trip-raw",
        )
        assertThat(catalog.map { it.itemKey }).containsNoDuplicates()
    }

    @Test
    fun `picking one of two same-named MCP tools selects and saves only that server's tool`() = runTest {
        val stateFlow = MutableStateFlow(
            state().copy(
                mcpTools = listOf(
                    McpTool(name = "show_trip_budget", serverName = "trip-budget"),
                    McpTool(name = "show_trip_budget", serverName = "trip-raw"),
                ),
            ),
        )
        val handle = AgentEditorStateHandle(stateFlow, backgroundScope)
        val favorites = mockk<ToolFavoritesRepository>(relaxed = true) {
            every { this@mockk.favorites } returns MutableStateFlow(emptySet())
            every { isSupported } returns MutableStateFlow(false)
        }
        val delegate = ToolsMarketplaceDelegate(
            stateHandle = handle,
            toolFavoritesRepository = favorites,
            capabilitiesDelegate = mockk(relaxed = true),
            setCapability = { _, _ -> },
            toggleTool = {},
            toggleMcpTool = { key ->
                handle.update {
                    copy(selectedMcpTools = if (key in selectedMcpTools) selectedMcpTools - key else selectedMcpTools + key)
                }
            },
        )
        val rows = stateFlow.value.marketplaceCatalog().filter { it.kind == MarketplaceKind.MCP }
        val raw = rows.single { it.serverName == "trip-raw" }

        delegate.toggleItem(raw)

        val picked = stateFlow.value
        assertThat(rows.filter { picked.isMarketplaceItemSelected(it) }).containsExactly(raw)
        assertThat(buildToolsList(picked)).containsExactly("show_trip_budget_mcp_trip-raw")

        delegate.toggleItem(raw)

        assertThat(rows.none { stateFlow.value.isMarketplaceItemSelected(it) }).isTrue()
    }

    @Test
    fun `a stored MCP key with a raw server name selects its row and saves normalized`() {
        val loaded = AgentEditorUiState(mcpTools = listOf(McpTool(name = "list_events", serverName = "Google Workspace")))
            .applyAgentData(Agent(id = "agent-1", tools = listOf("list_events_mcp_Google Workspace")))

        val row = loaded.marketplaceCatalog().single { it.kind == MarketplaceKind.MCP }
        assertThat(loaded.isMarketplaceItemSelected(row)).isTrue()
        assertThat(buildToolsList(loaded)).containsExactly("list_events_mcp_Google_Workspace")
    }

    @Test
    fun `search matches a built-in by its resolved label`() {
        val catalog = state().marketplaceCatalog()
        val labels = mapOf("builtin:${ToolConstants.WEB_SEARCH}" to "Web Search")

        val hits = filterMarketplace(catalog, "web sea", MarketplaceFilter.ALL, emptySet(), labels)

        assertThat(hits.map { it.id }).containsExactly(ToolConstants.WEB_SEARCH)
    }

    @Test
    fun `an MCP tool shows its server's title and is found by title or key`() {
        val catalog = state().copy(mcpServerTitles = mapOf("jira" to "Atlassian Jira")).marketplaceCatalog()

        assertThat(catalog.first { it.id == "create_issue" }.serverTitle).isEqualTo("Atlassian Jira")
        val byTitle = filterMarketplace(catalog, "atlassian", MarketplaceFilter.ALL, emptySet())
        val byKey = filterMarketplace(catalog, "jira", MarketplaceFilter.ALL, emptySet())
        assertThat(byTitle.map { it.id }).containsExactly("create_issue", "search")
        assertThat(byKey.map { it.id }).containsExactly("create_issue", "search")
    }

    @Test
    fun `pinned items float above their unpinned siblings`() {
        val catalog = state().marketplaceCatalog()

        val tools = filterMarketplace(
            catalog = catalog,
            query = "",
            filter = MarketplaceFilter.MCP,
            favoriteKeys = setOf("mcp:jira"),
        )

        // Both jira tools are pinned by the same key, so ordering falls to the name tiebreak.
        assertThat(tools.map { it.id }).containsExactly("create_issue", "search").inOrder()
    }

    @Test
    fun `favorites filter keeps only pinned rows`() {
        val catalog = state().marketplaceCatalog()

        val pinned = filterMarketplace(
            catalog = catalog,
            query = "",
            filter = MarketplaceFilter.FAVORITES,
            favoriteKeys = setOf("tool:wolfram"),
        )

        assertThat(pinned.map { it.id }).containsExactly("wolfram")
    }

    // ── capability gating (v0.8.8-rc1, web catalog.ts parity) ──────

    private fun askPlugin() = AgentToolDisplayData(
        toolId = ToolConstants.ASK_USER_QUESTION,
        name = "Ask User",
        description = "Pauses to ask",
        icon = null,
        isAvailable = true,
    )

    @Test
    fun `ask_user_question surfaces as a builtin-style row, not a plugin`() {
        val catalog = state()
            .copy(availableTools = state().availableTools + askPlugin())
            .marketplaceCatalog()

        val builtins = catalog.filter { it.kind == MarketplaceKind.BUILTIN }.map { it.id }
        val tools = catalog.filter { it.kind == MarketplaceKind.TOOL }.map { it.id }
        assertThat(builtins).contains(ToolConstants.ASK_USER_QUESTION)
        assertThat(tools).doesNotContain(ToolConstants.ASK_USER_QUESTION)
    }

    @Test
    fun `ask_user_question is hidden when its capability is disabled`() {
        // The server drops the tool at runtime when the capability is off, so offering the row
        // produces agents whose tool "does not exist".
        val catalog = state()
            .copy(
                availableTools = state().availableTools + askPlugin(),
                isAskUserQuestionAvailable = false,
            )
            .marketplaceCatalog()

        assertThat(catalog.map { it.id }).doesNotContain(ToolConstants.ASK_USER_QUESTION)
    }

    @Test
    fun `ask_user_question is hidden when the server does not list the plugin`() {
        // Plugin presence is the pre-feature-server gate: capability lists that predate the
        // feature fail open, but /api/agents/tools never lists a tool the server lacks.
        val catalog = state().marketplaceCatalog()

        assertThat(catalog.map { it.id }).doesNotContain(ToolConstants.ASK_USER_QUESTION)
    }

    @Test
    fun `generic plugins are gated on the tools capability`() {
        val catalog = state().copy(isGenericToolsAvailable = false).marketplaceCatalog()

        assertThat(catalog.filter { it.kind == MarketplaceKind.TOOL }).isEmpty()
        // The other kinds are their own gates, not casualties of this one.
        assertThat(catalog.filter { it.kind == MarketplaceKind.MCP }).isNotEmpty()
    }

    @Test
    fun `the ask row reads and toggles agent tools membership`() {
        val selected = state().copy(
            availableTools = state().availableTools + askPlugin(),
            selectedTools = listOf(ToolConstants.ASK_USER_QUESTION),
        )

        assertThat(selected.isBuiltinEnabled(ToolConstants.ASK_USER_QUESTION)).isTrue()
        assertThat(state().isBuiltinEnabled(ToolConstants.ASK_USER_QUESTION)).isFalse()
    }

    @Test
    fun `selection reads through to whichever list owns that kind`() {
        val selected = state().copy(
            webSearchEnabled = true,
            selectedTools = listOf("wolfram"),
            selectedMcpTools = setOf("search_mcp_jira"),
            selectedSkillIds = listOf("skill-1"),
        )
        val catalog = selected.marketplaceCatalog()

        val on = catalog.filter { selected.isMarketplaceItemSelected(it) }.map { it.id }
        assertThat(on).containsExactly(ToolConstants.WEB_SEARCH, "wolfram", "search", "skill-1")
    }
}
