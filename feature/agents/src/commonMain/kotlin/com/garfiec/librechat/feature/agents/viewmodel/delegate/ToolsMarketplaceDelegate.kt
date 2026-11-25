package com.garfiec.librechat.feature.agents.viewmodel.delegate

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.ToolConstants
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ToolFavoritesRepository
import com.garfiec.librechat.feature.agents.components.model.MarketplaceBuiltinLabel
import com.garfiec.librechat.feature.agents.components.model.MarketplaceItem
import com.garfiec.librechat.feature.agents.components.model.MarketplaceKind
import com.garfiec.librechat.feature.agents.util.mcpToolKey
import com.garfiec.librechat.feature.agents.viewmodel.AgentEditorStateHandle
import com.garfiec.librechat.feature.agents.viewmodel.AgentEditorUiState
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Owns the agent editor's unified tool picker: the one catalog that lists built-in capabilities,
 * plugin tools, MCP servers and skills together, and the favorites that pin entries in it.
 *
 * Upstream #13952 collapsed three separate dialogs into one because the split was an artifact of
 * how the features shipped, not of how anyone builds an agent — you decide what the agent can
 * *do*, and whether that turns out to be a capability, a plugin or an MCP server is an
 * implementation detail. The catalog is derived from reference data the editor already loads, so
 * nothing here fetches tools; only the favorites are its own.
 *
 * Toggling an item routes back into the delegates that already own each kind's selection, so the
 * dedicated sections and this picker can never disagree about what is enabled.
 */
class ToolsMarketplaceDelegate(
    private val stateHandle: AgentEditorStateHandle,
    private val toolFavoritesRepository: ToolFavoritesRepository,
    private val capabilitiesDelegate: AgentCapabilitiesDelegate,
    /** Flips a built-in capability. Routed out because each has its own side effects. */
    private val setCapability: (String, Boolean) -> Unit,
    /** Adds/removes a plugin tool id on the agent. */
    private val toggleTool: (String) -> Unit,
    /** Adds/removes one MCP tool by its [mcpToolKey]. */
    private val toggleMcpTool: (String) -> Unit,
) {

    init {
        stateHandle.scope.launch {
            combine(
                toolFavoritesRepository.favorites,
                toolFavoritesRepository.isSupported,
            ) { favorites, supported -> favorites.map { it.itemKey }.toSet() to supported }
                .collect { (keys, supported) ->
                    stateHandle.update {
                        copy(favoriteToolKeys = keys, areToolFavoritesSupported = supported)
                    }
                }
        }
    }

    fun openMarketplace() {
        stateHandle.update { copy(showToolsMarketplace = true) }
        // Refreshed on open rather than at editor construction: on a server without the routes
        // this is a wasted request, and the picker is the only place the pins are visible.
        stateHandle.scope.launch { toolFavoritesRepository.refresh() }
    }

    fun closeMarketplace() {
        stateHandle.update { copy(showToolsMarketplace = false, marketplaceQuery = "") }
    }

    fun onQueryChanged(query: String) {
        stateHandle.update { copy(marketplaceQuery = query) }
    }

    fun onFilterChanged(filter: MarketplaceFilter) {
        stateHandle.update { copy(marketplaceFilter = filter) }
    }

    /** Applies a catalog row's toggle to whichever delegate actually owns that kind's state. */
    fun toggleItem(item: MarketplaceItem) {
        val state = stateHandle.state
        when (item.kind) {
            // Builtin-style row, agent.tools mechanics: the ask tool has no capability toggle of
            // its own on the agent — membership in the tools list IS its enablement.
            MarketplaceKind.BUILTIN ->
                if (item.id == ToolConstants.ASK_USER_QUESTION) {
                    toggleTool(item.id)
                } else {
                    setCapability(item.id, !state.isBuiltinEnabled(item.id))
                }
            MarketplaceKind.TOOL -> toggleTool(item.id)
            MarketplaceKind.MCP -> toggleMcpTool(mcpToolKey(item.id, item.serverName))
            MarketplaceKind.SKILL -> capabilitiesDelegate.onSkillSelectionToggled(item.id)
        }
    }

    fun toggleFavorite(item: MarketplaceItem) {
        stateHandle.scope.launch {
            val result = toolFavoritesRepository.toggle(item.kind.favoriteType, item.favoriteId)
            if (result is Result.Error) {
                // Surfaced rather than swallowed: the optimistic star has already rolled back,
                // so without a message the tap looks like it simply did nothing.
                Logger.d { "Tool favorite toggle failed: ${result.message}" }
                stateHandle.update { copy(error = result.message ?: "Could not update favorites") }
            }
        }
    }
}

/**
 * The full catalog, derived from reference data the editor already holds.
 *
 * Built in the UI state rather than stored, so it can never go stale against the lists it is
 * derived from. Only builtins the server offers and the role may use are listed (web's
 * `BUILTIN_ROLE_PERMISSIONS`) — offering one the server drops would be a dead row.
 */
fun AgentEditorUiState.marketplaceCatalog(): List<MarketplaceItem> = buildList {
    if (isCodeInterpreterOffered) {
        add(builtin(ToolConstants.EXECUTE_CODE, MarketplaceBuiltinLabel.CODE_INTERPRETER))
    }
    if (isFileSearchOffered) {
        add(builtin(ToolConstants.FILE_SEARCH, MarketplaceBuiltinLabel.FILE_SEARCH))
    }
    if (isWebSearchOffered) {
        add(builtin(ToolConstants.WEB_SEARCH, MarketplaceBuiltinLabel.WEB_SEARCH).copy(needsSetup = webSearchNeedsKeys))
    }
    add(builtin(FILE_CONTEXT, MarketplaceBuiltinLabel.FILE_CONTEXT))

    // Native tool presented with the builtins (it ships with the app and pauses the run like a
    // first-class feature) while remaining an `agent.tools` entry mechanically. Availability is
    // its OWN capability, NOT the generic `tools` one — AND the server must still list the
    // plugin (which is also what hides it on pre-feature servers). Mirrors web buildCatalog.
    if (isAskUserQuestionAvailable &&
        availableTools.any { (it.toolId ?: it.name) == ToolConstants.ASK_USER_QUESTION }
    ) {
        add(builtin(ToolConstants.ASK_USER_QUESTION, MarketplaceBuiltinLabel.ASK_USER_QUESTION))
    }

    if (isGenericToolsAvailable) {
        availableTools.forEach { tool ->
            val id = tool.toolId ?: tool.name ?: return@forEach
            // Surfaced as a builtin-style row above — don't double-list as a plugin.
            if (id == ToolConstants.ASK_USER_QUESTION) return@forEach
            add(
                MarketplaceItem(
                    kind = MarketplaceKind.TOOL,
                    id = id,
                    name = tool.name ?: id,
                    description = tool.description,
                    iconUrl = tool.icon,
                ),
            )
        }
    }

    mcpTools.forEach { tool ->
        add(
            MarketplaceItem(
                kind = MarketplaceKind.MCP,
                id = tool.name,
                name = tool.name,
                description = tool.description,
                serverName = tool.serverName,
                serverTitle = tool.serverName?.let(mcpServerTitles::get),
            ),
        )
    }

    if (isSkillsAvailable) {
        availableSkills.forEach { skill ->
            add(
                MarketplaceItem(
                    kind = MarketplaceKind.SKILL,
                    id = skill.id,
                    name = skill.displayTitle ?: skill.name,
                    description = skill.description,
                ),
            )
        }
    }
}

private fun builtin(id: String, label: MarketplaceBuiltinLabel) = MarketplaceItem(
    kind = MarketplaceKind.BUILTIN,
    id = id,
    labelRes = label,
)

/** True when [item] is currently on the agent. */
fun AgentEditorUiState.isMarketplaceItemSelected(item: MarketplaceItem): Boolean =
    when (item.kind) {
        MarketplaceKind.BUILTIN -> isBuiltinEnabled(item.id)
        MarketplaceKind.TOOL -> item.id in selectedTools
        MarketplaceKind.MCP -> mcpToolKey(item.id, item.serverName) in selectedMcpTools
        MarketplaceKind.SKILL -> item.id in selectedSkillIds
    }

internal fun AgentEditorUiState.isBuiltinEnabled(id: String): Boolean = when (id) {
    ToolConstants.EXECUTE_CODE -> codeInterpreterEnabled
    ToolConstants.FILE_SEARCH -> fileSearchEnabled
    ToolConstants.WEB_SEARCH -> webSearchEnabled
    FILE_CONTEXT -> fileContextEnabled
    // Builtin-style presentation over agent.tools membership — see marketplaceCatalog.
    ToolConstants.ASK_USER_QUESTION -> ToolConstants.ASK_USER_QUESTION in selectedTools
    else -> false
}

/** The `tool_resource` marker the editor uses for whole-file context. */
private const val FILE_CONTEXT = "context"
