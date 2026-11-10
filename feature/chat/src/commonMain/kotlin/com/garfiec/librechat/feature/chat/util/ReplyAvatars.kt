package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.media.IconSource
import com.garfiec.librechat.core.model.media.iconEndpoint
import com.garfiec.librechat.core.model.media.iconImageUrl
import com.garfiec.librechat.core.model.media.isImageUrl
import com.garfiec.librechat.core.model.media.resolveIconSource

/**
 * The avatar a reply draws. A message with no endpoint of its own takes [fallbackEndpoint]. An agents
 * reply finds its agent among [agents] by its `model` (the agent id), else by [fallbackAgentId] (the
 * chat's agent), as web's `useMessageActions` does. Null for the user's own messages.
 */
fun Message.replyAvatar(
    fallbackEndpoint: String?,
    serverUrl: String,
    endpointConfigs: Map<String, EndpointConfig>?,
    agents: List<Agent> = emptyList(),
    fallbackAgentId: String? = null,
): IconSource? {
    if (isCreatedByUser) return null
    val replyEndpoint = endpoint ?: fallbackEndpoint
    val agentAvatar = if (replyEndpoint == EndpointConstants.AGENTS) {
        val agentId = model?.takeIf { it.isNotEmpty() } ?: fallbackAgentId
        agents.find { it.id == agentId }?.avatarUrl?.takeIf { it.isNotBlank() }
    } else {
        null
    }
    return replyIconSource(iconURL, replyEndpoint, serverUrl, endpointConfigs, agentAvatar)
}

/**
 * What a reply with [iconURL] on [endpoint] draws, as web's `MessageIcon`
 * (`client/src/components/Chat/Messages/MessageIcon.tsx`, registered as `message-icon-rule`): an image
 * [iconURL] is loaded; any other value counts only as an endpoint key ([iconEndpoint]), drawn with the
 * reply's [agentAvatar], else that endpoint's configured image `iconURL`, else its brand, including a
 * custom endpoint's served `providerId`. So a spec icon naming a provider (`groq`) draws the reply's
 * endpoint, unlike the picker, and an agent's reply keeps the avatar it streamed with.
 */
internal fun replyIconSource(
    iconURL: String?,
    endpoint: String?,
    serverUrl: String,
    endpointConfigs: Map<String, EndpointConfig>?,
    agentAvatar: String? = null,
): IconSource {
    if (isImageUrl(iconURL)) return resolveIconSource(iconURL, endpoint, serverUrl, endpointConfigs)
    val drawn = iconEndpoint(iconURL, endpoint, endpointConfigs)
    if (agentAvatar != null) return IconSource(drawn, iconImageUrl(agentAvatar, serverUrl))
    val endpointImage = drawn?.let { endpointConfigs?.get(it)?.iconURL }?.takeIf(::isImageUrl)
    return resolveIconSource(endpointImage, drawn, serverUrl, endpointConfigs)
}

/**
 * Each node with its [MessageNode.avatar] resolved ([replyAvatar]). A node whose avatar is unchanged
 * is kept as is, and so is the list when none changed.
 */
fun List<MessageNode>.withReplyAvatars(
    fallbackEndpoint: String?,
    serverUrl: String,
    endpointConfigs: Map<String, EndpointConfig>?,
    agents: List<Agent> = emptyList(),
    fallbackAgentId: String? = null,
): List<MessageNode> {
    var changed = false
    val mapped = map { node ->
        val avatar = node.message.replyAvatar(fallbackEndpoint, serverUrl, endpointConfigs, agents, fallbackAgentId)
        if (avatar == node.avatar) {
            node
        } else {
            changed = true
            node.copy(avatar = avatar)
        }
    }
    return if (changed) mapped else this
}

/**
 * [withReplyAvatars] that returns its last result while the inputs are the same, so the per-token
 * emissions of a streaming reply don't walk the whole path. Nodes, configs and agents compare by identity:
 * a rebuilt path is a new list. One collector only.
 */
internal class ReplyAvatarMemo {
    private var nodes: List<MessageNode>? = null
    private var fallbackEndpoint: String? = null
    private var serverUrl: String? = null
    private var endpointConfigs: Map<String, EndpointConfig>? = null
    private var agents: List<Agent>? = null
    private var fallbackAgentId: String? = null
    private var result: List<MessageNode> = emptyList()

    fun resolve(
        nodes: List<MessageNode>,
        fallbackEndpoint: String?,
        serverUrl: String,
        endpointConfigs: Map<String, EndpointConfig>?,
        agents: List<Agent> = emptyList(),
        fallbackAgentId: String? = null,
    ): List<MessageNode> {
        val same = nodes === this.nodes &&
            endpointConfigs === this.endpointConfigs &&
            agents === this.agents &&
            fallbackEndpoint == this.fallbackEndpoint &&
            fallbackAgentId == this.fallbackAgentId &&
            serverUrl == this.serverUrl
        if (!same) {
            result = nodes.withReplyAvatars(fallbackEndpoint, serverUrl, endpointConfigs, agents, fallbackAgentId)
            this.nodes = nodes
            this.fallbackEndpoint = fallbackEndpoint
            this.serverUrl = serverUrl
            this.endpointConfigs = endpointConfigs
            this.agents = agents
            this.fallbackAgentId = fallbackAgentId
        }
        return result
    }
}
