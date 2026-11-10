package com.garfiec.librechat.feature.chat.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.media.IconSource
import com.garfiec.librechat.feature.chat.util.replyIconSource

/**
 * The avatars of replies that have no message yet, resolved with the server URL and endpoint
 * configs like each node's [com.garfiec.librechat.feature.chat.util.MessageNode.avatar]. Only
 * [ChatViewModel]'s exposed state fills it; the backing state leaves it empty.
 */
@Immutable
data class ReplyAvatarsState(
    /** The streaming reply's: what its saved message will draw. */
    val streaming: IconSource? = null,
    /** Compare's second lane's, for its pane's messages and its streaming reply. Null while Compare is off. */
    val laneB: IconSource? = null,
)

internal fun ChatUiState.replyAvatars(serverUrl: String): ReplyAvatarsState = ReplyAvatarsState(
    streaming = replyAvatarFor(selectedEndpoint, selectedModel, selectedSpec, serverUrl, endpointConfigs),
    laneB = if (comparisonState.isEnabled) {
        replyAvatarFor(
            comparisonState.secondaryEndpoint ?: EndpointConstants.AGENTS,
            comparisonState.secondaryModel,
            laneBSpec,
            serverUrl,
            endpointConfigs,
        )
    } else {
        null
    },
)

/** A spec's reply icon, else an agent's avatar under the agents endpoint, else [endpoint]'s brand. */
private fun ChatUiState.replyAvatarFor(
    endpoint: String?,
    model: String?,
    specName: String?,
    serverUrl: String,
    endpointConfigs: Map<String, EndpointConfig>,
): IconSource {
    val spec = specName?.let { name -> modelSpecs?.list?.find { it.name == name } }
    if (spec != null) return spec.replyIconSource(endpoint, agents, serverUrl, endpointConfigs)
    val agentAvatar = agents.takeIf { endpoint == EndpointConstants.AGENTS && model != null }
        ?.find { it.id == model }?.avatarUrl?.takeIf { it.isNotBlank() }
    return replyIconSource(null, endpoint, serverUrl, endpointConfigs, agentAvatar)
}
