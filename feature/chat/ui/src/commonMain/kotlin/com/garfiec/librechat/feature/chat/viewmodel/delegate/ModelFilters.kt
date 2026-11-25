package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.model.EndpointConfig

/**
 * Filters availableModels to the endpoints the user's server has enabled, dropping
 * endpoints with empty model lists. Single source of truth for "which models are
 * usable" — shared by [ModelSelectionDelegate] (seeding/validation) and the model
 * selector UI so they can't drift apart.
 *
 * Never yields an `agents` entry or a model listed twice: agents come from the agents list, and
 * either would put two rows under one key in the selector's LazyColumn (#541). The repository
 * already normalizes; this keeps every consumer safe from a map that did not come through it.
 */
fun filterModelsByEndpoint(
    rawModels: Map<String, List<String>>,
    endpointConfigs: Map<String, EndpointConfig>,
): Map<String, List<String>> =
    rawModels.filter { (endpoint, models) ->
        endpoint != EndpointConstants.AGENTS &&
            models.isNotEmpty() &&
            (endpointConfigs.isEmpty() || endpoint in endpointConfigs)
    }.mapValues { (_, models) -> models.distinct() }
