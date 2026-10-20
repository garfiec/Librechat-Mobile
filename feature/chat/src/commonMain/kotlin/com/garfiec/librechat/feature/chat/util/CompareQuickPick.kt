package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.endpoint.KeyState
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
)

/**
 * The short list beside the wide-screen Compare setup's search box. With no [query] it is what the
 * user starred (pin order kept), or, with nothing starred, the first models (then agents) the
 * server offers, so the pane is never an empty box. With a query it is the full selector's
 * matching over every agent and model, ranked as one list. Endpoints still waiting on the user's
 * API key are left out: the full list greys them out behind "Set API Key", one tap away behind
 * "Browse all models".
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
    endpointKeyStates: Map<String, KeyState> = emptyMap(),
    limit: Int = QUICK_PICK_LIMIT,
): List<CompareQuickPick> {
    val models = filterModelsByEndpoint(availableModels, endpointConfigs)
        .filterKeys { !endpointKeyStates[it].blocksSelection() }
    val agentsById = agents.distinctBy { it.id }.associateBy { it.id }
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

    val trimmed = query.trim()
    if (trimmed.isNotEmpty()) {
        // One ranking across agents and models: ranked per group and joined, a run of weak agent
        // matches would fill the short list ahead of an exact model.
        val candidates = agentsById.values.map(::agentPick) +
            models.flatMap { (endpoint, ids) -> ids.map { modelPick(endpoint, it) } }
        return searchByName(candidates, trimmed) { it.name }.take(limit)
    }

    val starred = favoriteAgentIds.mapNotNull { agentsById[it]?.let(::agentPick) } +
        starredModels(favoriteModelKeys, models).map { (endpoint, model) -> modelPick(endpoint, model) }
    if (starred.isNotEmpty()) return starred.take(limit)
    // Agents after models, so an agents-only server still fills the list.
    val fallback = models.flatMap { (endpoint, ids) -> ids.map { modelPick(endpoint, it) } } +
        agentsById.values.map(::agentPick)
    return fallback.take(limit)
}

private const val QUICK_PICK_LIMIT = 6
