package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.core.model.config.ModelSpecs
import com.garfiec.librechat.core.model.config.displayLabel
import com.garfiec.librechat.core.model.endpoint.KeyState
import com.garfiec.librechat.core.model.media.IconSource
import com.garfiec.librechat.feature.chat.viewmodel.delegate.FavoritesDelegate
import com.garfiec.librechat.feature.chat.viewmodel.delegate.filterModelsByEndpoint

/** One row of the wide-screen Compare setup's short list. */
data class CompareQuickPick(
    val endpoint: String,
    /** The model id, or the agent id under [EndpointConstants.AGENTS]. */
    val model: String,
    val name: String,
    /** Where it comes from: the endpoint's label, or the agent caption. */
    val source: String,
    val starred: Boolean,
    /** The spec this row picks; null for a raw model or agent. */
    val spec: String? = null,
    /** A spec row's icon, unless the spec hides it in menus. */
    val icon: IconSource? = null,
)

/**
 * The short list beside the wide-screen Compare setup's search box. With no [query] it is what the
 * user starred (pin order kept), or, with nothing starred, the first models (then agents) the
 * server offers, so the pane is never an empty box. With a query it is the full selector's
 * matching over every agent and model, ranked as one list. Endpoints still waiting on the user's
 * API key are left out: the full list greys them out behind "Set API Key", one tap away behind
 * "Browse all models".
 *
 * [specs] (the picker's visible spec rows) lead the list, as in the full selector. Raw models and
 * agents follow only where [modelSpecs] lists them and a send to them would be accepted.
 */
@Suppress("LongParameterList")
fun compareQuickPicks(
    query: String,
    availableModels: Map<String, List<String>>,
    endpointConfigs: Map<String, EndpointConfig>,
    agents: List<Agent>,
    favoriteModelKeys: Set<String>,
    favoriteAgentIds: Set<String>,
    agentCaption: String,
    /** `interface.modelSelect`; off leaves only the specs. */
    modelSelect: Boolean,
    favoriteSpecNames: Set<String> = emptySet(),
    endpointKeyStates: Map<String, KeyState> = emptyMap(),
    specs: List<ModelSpec> = emptyList(),
    modelSpecs: ModelSpecs? = null,
    serverUrl: String = "",
    limit: Int = QUICK_PICK_LIMIT,
): List<CompareQuickPick> {
    val rawSendable = !modelSpecs.requiresSpec
    val models = filterModelsByEndpoint(availableModels, endpointConfigs)
        .filterKeys {
            rawSendable && modelSpecs.showsRawEndpoint(it, modelSelect) && !endpointKeyStates[it].blocksSelection()
        }
    val agentsById = agents.distinctBy { it.id }
        .takeIf { rawSendable && modelSpecs.showsRawEndpoint(EndpointConstants.AGENTS, modelSelect) }
        .orEmpty()
        .associateBy { it.id }
    fun sourceOf(endpoint: String) = endpointConfigs[endpoint]?.modelDisplayLabel ?: endpoint
    fun modelPick(endpoint: String, model: String) = CompareQuickPick(
        endpoint = endpoint,
        model = model,
        name = model,
        source = sourceOf(endpoint),
        starred = FavoritesDelegate.favoriteModelKey(endpoint, model) in favoriteModelKeys,
    )
    fun agentPick(agent: Agent) = CompareQuickPick(
        endpoint = EndpointConstants.AGENTS,
        model = agent.id,
        name = agent.name ?: agent.id,
        source = agentCaption,
        starred = agent.id in favoriteAgentIds,
    )

    // Pinned specs lead, in pin order, so the limit never cuts one in favour of an unpinned spec.
    val pinOrder = favoriteSpecNames.withIndex().associate { (index, name) -> name to index }
    val specPicks = specs.sortedBy { pinOrder[it.name] ?: Int.MAX_VALUE }.mapNotNull { spec ->
        val seed = spec.toSeed() ?: return@mapNotNull null
        if (endpointKeyStates[seed.endpoint].blocksSelection()) return@mapNotNull null
        CompareQuickPick(
            endpoint = seed.endpoint,
            model = seed.model,
            name = spec.displayLabel,
            source = if (seed.endpoint == EndpointConstants.AGENTS) agentCaption else sourceOf(seed.endpoint),
            starred = spec.name in favoriteSpecNames,
            spec = spec.name,
            icon = spec.menuIconSource(agents, serverUrl),
        )
    }

    val trimmed = query.trim()
    if (trimmed.isNotEmpty()) {
        // One ranking across specs, agents and models: ranked per group and joined, a run of weak
        // agent matches would fill the short list ahead of an exact model.
        val candidates = specPicks + agentsById.values.map(::agentPick) +
            models.flatMap { (endpoint, ids) -> ids.map { modelPick(endpoint, it) } }
        return searchByName(candidates, trimmed) { it.name }.take(limit)
    }

    val starred = favoriteAgentIds.mapNotNull { agentsById[it]?.let(::agentPick) } +
        starredModels(favoriteModelKeys, models).map { (endpoint, model) -> modelPick(endpoint, model) }
    // Agents after models, so an agents-only server still fills the list.
    val raw = starred.ifEmpty {
        models.flatMap { (endpoint, ids) -> ids.map { modelPick(endpoint, it) } } + agentsById.values.map(::agentPick)
    }
    return (specPicks + raw).take(limit)
}

private const val QUICK_PICK_LIMIT = 6
