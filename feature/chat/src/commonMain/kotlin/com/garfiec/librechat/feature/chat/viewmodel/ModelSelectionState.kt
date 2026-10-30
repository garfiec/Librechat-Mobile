package com.garfiec.librechat.feature.chat.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.common.ToolConstants
import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.core.model.config.ModelSpecs
import com.garfiec.librechat.core.model.endpoint.KeyState
import com.garfiec.librechat.core.ui.components.ModelParameters
import com.garfiec.librechat.feature.chat.model.McpServerDisplayData
import com.garfiec.librechat.feature.chat.util.SpecTools
import com.garfiec.librechat.feature.chat.util.forcedMcpServers
import com.garfiec.librechat.feature.chat.util.forcedTools
import com.garfiec.librechat.feature.chat.util.presetTools
import com.garfiec.librechat.feature.chat.util.requiresSpec

/**
 * Endpoint/model selection, per-request tool + MCP config, model parameters, and the
 * model-selector sheet's open state. Written by [ChatViewModel], ModelSelectionDelegate,
 * PresetPromptDelegate, EndpointKeyStatusDelegate and QueueOrchestrationDelegate (the composer
 * swap around a queued edit).
 *
 * [choice] is written whole: through ModelSelectionDelegate's `applySelection`, or, where it must
 * land in the same emission as another field, by the preset load, the composer swap and the
 * seeder's spec-config update. ChatViewModel's reset carries the current one over unchanged.
 * Nothing stops another `copy(choice = …)`; keep it to those.
 */
@Immutable
data class ModelSelectionState(
    val choice: ModelChoice = ModelChoice.Raw(EndpointConstants.AGENTS, null),
    val modelSpecs: ModelSpecs? = null,
    val endpointConfigs: Map<String, EndpointConfig> = emptyMap(),
    /**
     * Per-endpoint user-provided-key state for endpoints with `userProvide=true`
     * (or `userProvideURL=true`). Drives the model selector's greyed-out group +
     * "Add key" action. Endpoints absent from this map are implicitly "doesn't
     * need a key" (built-ins) — [ModelSelectorSheet] fail-opens on `null` and
     * on [KeyState.Loading] so the cold-start window doesn't flash to greyed.
     */
    val endpointKeyStates: Map<String, KeyState> = emptyMap(),
    val availableModels: Map<String, List<String>> = emptyMap(),
    val agents: List<Agent> = emptyList(),
    /**
     * Whether [agents] is the user's agent list: the fetch succeeded, or the role denies agents.
     * Until then (and after a failed fetch) [agents] being empty or missing an agent says nothing,
     * so the picker keeps agent specs it cannot check.
     */
    val agentsLoaded: Boolean = false,
    /**
     * The selected agent's LLM provider, resolved by [ModelSelectionDelegate] from
     * `GET /api/agents/:id` whenever the agent selection changes. Null on every non-agents
     * endpoint, and null while the fetch is in flight or after it fails.
     *
     * It cannot be read off [agents]: the list projection omits `provider`. Anything routing on
     * it must treat null as "unknown" and fall back to whatever it did before agents had a
     * provider — never to a behaviour that only makes sense for a *known* provider.
     */
    val selectedAgentProvider: String? = null,
    /**
     * The selected agent's conversation starters, fetched alongside [selectedAgentProvider] (the
     * list projection omits them too). Null while unresolved or on a non-agents endpoint; empty
     * when the agent has none or the fetch failed.
     */
    val selectedAgentStarters: List<String>? = null,
    val modelParameters: ModelParameters = ModelParameters.DEFAULT,
    /** Single source of truth for whether the *standalone* model-selector sheet is open — the
     *  top-bar chip, the comparison dual-pane, and send-block auto-opens. Preflight failures and
     *  readiness timeouts flip this to true so the user sees the sheet with a send-block banner.
     *  The chat options sheet shows the selector as an in-sheet page off its own local state, so
     *  it never sets this. */
    val showModelSheet: Boolean = false,
    val enabledTools: Set<String> = emptySet(),
    val mcpServers: List<McpServerDisplayData> = emptyList(),
    val selectedMcpServerNames: Set<String> = emptySet(),
    /**
     * Tools added on top of the selected spec in this chat ([SpecTools]); another spec's count as
     * none. Under a spec the toolbar reads [effectiveTools] and [effectiveMcpServerNames] instead of
     * [enabledTools] and [selectedMcpServerNames], which keep the user's own toggles for later.
     */
    val specTools: SpecTools? = null,
    /**
     * This conversation's Skills toggle (`ephemeralAgent.skills`). Per conversation, unlike
     * [enabledTools]: owned by `SkillsToggleDelegate`, which reads and writes the slot the
     * conversation maps to. Only sent while `ChatUiState.isSkillsToolAvailable` holds.
     */
    val skillsToggle: Boolean = false,
    /**
     * Whether the detected backend is v0.8.5+ and therefore accepts the `xhigh`
     * and `max` values in the reasoning-effort and effort dropdowns. False on
     * older or unknown servers (per VERSION_GATES.md guideline #2).
     */
    val extendedEffortSupported: Boolean = false,
    /** Whether thinkingDisplay `updates` may be offered (v0.8.8); see `EndpointParameterRegistry.getDefinitions`. */
    val thinkingDisplayUpdatesSupported: Boolean = false,
) {
    val selectedEndpoint: String get() = choice.endpoint
    val selectedModel: String? get() = choice.model
    val selectedSpec: String? get() = choice.spec

    /** The served spec the selection runs on, or null for a raw pick or a spec the config doesn't list. */
    val activeSpec: ModelSpec?
        get() = selectedSpec?.let { name -> modelSpecs?.list?.firstOrNull { it.name == name } }

    /** Whether the spec hides the tool controls; then only its own tools are sent. */
    val toolsHiddenBySpec: Boolean get() = activeSpec?.hideBadgeRow == true

    /**
     * The tools the spec turns on, which can't be turned off here: its tool flags, and under
     * `enforce` its preset's too ([presetTools]). Empty off a spec.
     */
    val lockedTools: Set<String>
        get() {
            val spec = activeSpec ?: return emptySet()
            return if (modelSpecs.requiresSpec) spec.forcedTools + spec.presetTools else spec.forcedTools
        }

    /** The MCP servers the spec adds, likewise. */
    val lockedMcpServers: Set<String> get() = activeSpec?.forcedMcpServers.orEmpty()

    /** What a spec chat starts with on top of the spec's tools: its preset's ([presetTools]). */
    private val initialSpecTools: SpecTools?
        get() = selectedSpec?.let { SpecTools(it, added = activeSpec?.presetTools.orEmpty()) }

    /**
     * This spec's additions: [specTools] while they belong to it, else [initialSpecTools]. Under
     * `hideBadgeRow` the user's additions don't count.
     */
    private val activeSpecTools: SpecTools?
        get() {
            if (toolsHiddenBySpec) return initialSpecTools
            return specTools?.takeIf { it.spec == selectedSpec } ?: initialSpecTools
        }

    /**
     * The toolbar keys in use, web search and URL context included (not Skills). Under a spec
     * these are its own tools plus the chat's additions, never the user's toggles, as web starts a
     * spec chat from the spec's values.
     */
    val effectiveTools: Set<String>
        get() {
            if (selectedSpec == null) {
                var tools = enabledTools
                if (modelParameters.webSearch) tools = tools + ToolConstants.WEB_SEARCH
                if (modelParameters.urlContext) tools = tools + ToolConstants.URL_CONTEXT
                return tools
            }
            return lockedTools + activeSpecTools?.added.orEmpty()
        }

    /** The MCP servers in use, by the same rule as [effectiveTools]. */
    val effectiveMcpServerNames: Set<String>
        get() = if (selectedSpec == null) {
            selectedMcpServerNames
        } else {
            lockedMcpServers + activeSpecTools?.addedMcp.orEmpty()
        }

    /**
     * The parameters the chat shows: [modelParameters], with web search and URL context taken from
     * [effectiveTools] under a spec, where the parameters keep the user's own values. Its
     * `webSearch` is the control's state, not the request's: a send reads the built-in search from
     * `ChatUiState.webSearchRoute` instead.
     */
    val sendParameters: ModelParameters
        get() {
            if (selectedSpec == null) return modelParameters
            val tools = effectiveTools
            return modelParameters.copy(
                webSearch = ToolConstants.WEB_SEARCH in tools,
                urlContext = ToolConstants.URL_CONTEXT in tools,
            )
        }

    /**
     * [tool] switched on or off for this chat. Off a spec, null: the caller writes the user's own
     * toggle. Under one, the new [specTools] (unchanged for a tool the spec locks).
     */
    fun toggledSpecTool(tool: String): SpecTools? {
        val current = activeSpecTools ?: return null
        if (tool in lockedTools) return current
        val added = if (tool in current.added) current.added - tool else current.added + tool
        return current.copy(added = added)
    }

    /**
     * This selection with [parameters] from the sheet. Under a spec the sheet's URL context is the
     * toolbar's, so a change of it is the chat's addition ([toggledSpecTool]) and the parameters keep
     * the user's own values for when they leave the spec. The sheet has no web-search control (the
     * tools' one control drives both searches), so its value passes through unchanged.
     */
    fun withSheetParameters(parameters: ModelParameters): ModelSelectionState {
        if (selectedSpec == null) return copy(modelParameters = parameters)
        var next = this
        val sent = sendParameters
        if (parameters.urlContext != sent.urlContext) next = next.copy(specTools = next.toggledSpecTool(ToolConstants.URL_CONTEXT))
        return next.copy(
            modelParameters = parameters.copy(webSearch = modelParameters.webSearch, urlContext = modelParameters.urlContext),
        )
    }

    /**
     * This selection with [tool] set to [on] for this spec chat (a no-op for null, a tool already
     * there, or one the spec locks): how a loaded preset's `web_search` and `url_context` reach a
     * spec chat, whose parameters keep the user's own values.
     */
    fun withSpecTool(tool: String, on: Boolean?): ModelSelectionState {
        if (on == null || (tool in effectiveTools) == on) return this
        return copy(specTools = toggledSpecTool(tool))
    }

    /** [server] switched on or off for this chat, by the same rule as [toggledSpecTool]. */
    fun toggledSpecMcpServer(server: String): SpecTools? {
        val current = activeSpecTools ?: return null
        if (server in lockedMcpServers) return current
        val added = if (server in current.addedMcp) current.addedMcp - server else current.addedMcp + server
        return current.copy(addedMcp = added)
    }
}
