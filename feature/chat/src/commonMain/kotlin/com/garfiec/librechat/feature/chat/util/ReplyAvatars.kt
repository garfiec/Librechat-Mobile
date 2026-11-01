package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.media.IconSource
import com.garfiec.librechat.core.model.media.resolveIconSource

/**
 * The avatar a reply draws: its `iconURL` (a spec's icon or a name like `groq`), else its endpoint's
 * brand, including a custom endpoint's served `providerId` from [endpointConfigs]. A message with no
 * endpoint of its own takes [fallbackEndpoint]. Null for the user's own messages.
 */
fun Message.replyAvatar(
    fallbackEndpoint: String?,
    serverUrl: String,
    endpointConfigs: Map<String, EndpointConfig>?,
): IconSource? = if (isCreatedByUser) {
    null
} else {
    resolveIconSource(iconURL, endpoint ?: fallbackEndpoint, serverUrl, endpointConfigs)
}

/**
 * Each node with its [MessageNode.avatar] resolved ([replyAvatar]). A node whose avatar is unchanged
 * is kept as is, and so is the list when none changed.
 */
fun List<MessageNode>.withReplyAvatars(
    fallbackEndpoint: String?,
    serverUrl: String,
    endpointConfigs: Map<String, EndpointConfig>?,
): List<MessageNode> {
    var changed = false
    val mapped = map { node ->
        val avatar = node.message.replyAvatar(fallbackEndpoint, serverUrl, endpointConfigs)
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
 * emissions of a streaming reply don't walk the whole path. Nodes and configs compare by identity:
 * a rebuilt path is a new list. One collector only.
 */
internal class ReplyAvatarMemo {
    private var nodes: List<MessageNode>? = null
    private var fallbackEndpoint: String? = null
    private var serverUrl: String? = null
    private var endpointConfigs: Map<String, EndpointConfig>? = null
    private var result: List<MessageNode> = emptyList()

    fun resolve(
        nodes: List<MessageNode>,
        fallbackEndpoint: String?,
        serverUrl: String,
        endpointConfigs: Map<String, EndpointConfig>?,
    ): List<MessageNode> {
        val same = nodes === this.nodes &&
            endpointConfigs === this.endpointConfigs &&
            fallbackEndpoint == this.fallbackEndpoint &&
            serverUrl == this.serverUrl
        if (!same) {
            result = nodes.withReplyAvatars(fallbackEndpoint, serverUrl, endpointConfigs)
            this.nodes = nodes
            this.fallbackEndpoint = fallbackEndpoint
            this.serverUrl = serverUrl
            this.endpointConfigs = endpointConfigs
        }
        return result
    }
}
