package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.model.Agent

/**
 * The endpoint whose key a send to [endpoint] / [model] uses: the endpoint itself, or on the agents
 * endpoint (where [model] is the agent id) the agent's provider. Null when the agent isn't known.
 */
internal fun keyEndpointOf(endpoint: String, model: String?, agents: List<Agent>): String? =
    if (endpoint == EndpointConstants.AGENTS) {
        agents.firstOrNull { it.id == model }?.provider?.takeIf { it.isNotBlank() }
    } else {
        endpoint.takeIf { it.isNotBlank() }
    }
