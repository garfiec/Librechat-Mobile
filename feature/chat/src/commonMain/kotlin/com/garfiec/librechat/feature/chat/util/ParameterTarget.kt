package com.garfiec.librechat.feature.chat.util

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.config.EndpointDropParams
import com.garfiec.librechat.core.model.config.paramSettingsKey
import kotlinx.serialization.json.JsonElement

/**
 * What every `EndpointParameterRegistry` lookup for the chat's selection takes, resolved once so the
 * parameters sheet, the send payload and a saved preset agree on one parameter set.
 *
 * Two pairs, because the registry and `dropParams` key differently. [settingsEndpoint] /
 * [settingsProvider] pick the parameter set ([paramSettingsKey]: a custom endpoint's
 * `customParams.defaultParamsEndpoint`, else its `type`); [endpoint] / [provider] are the names as
 * configured, which `dropParams` is keyed on. [provider] is the agent's on the agents endpoint, and
 * [model] the agent's model there, not the agent id the selection holds.
 */
@Immutable
data class ParameterTarget(
    val endpoint: String,
    val provider: String?,
    val model: String?,
    val settingsEndpoint: String,
    val settingsProvider: String?,
) {
    /** The key of the resolved parameter set: the agent's provider's on the agents endpoint. */
    val settingsKey: String?
        get() = if (endpoint == EndpointConstants.AGENTS) settingsProvider else settingsEndpoint

    /** The admin's `dropParams` for this selection, by the configured names. */
    fun dropParams(dropParamsMap: Map<String, JsonElement>?): List<String> =
        EndpointDropParams.resolve(dropParamsMap, endpoint, provider, model)
}

internal fun parameterTarget(
    selectedEndpoint: String,
    selectedModel: String?,
    agents: List<Agent>,
    endpointConfigs: Map<String, EndpointConfig>,
): ParameterTarget {
    val agent = if (selectedEndpoint == EndpointConstants.AGENTS) agents.firstOrNull { it.id == selectedModel } else null
    val provider = agent?.provider
    return ParameterTarget(
        endpoint = selectedEndpoint,
        provider = provider,
        model = agent?.model ?: selectedModel,
        settingsEndpoint = paramSettingsKey(selectedEndpoint, endpointConfigs),
        settingsProvider = provider?.let { paramSettingsKey(it, endpointConfigs) },
    )
}
