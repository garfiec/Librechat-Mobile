package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.core.model.media.IconSource
import com.garfiec.librechat.core.model.media.iconValue
import com.garfiec.librechat.core.model.media.resolveIconSource

/**
 * The icon a spec shows wherever it is listed or opened: its `iconURL`, its preset's, its agent's
 * avatar, then its endpoint's glyph ([iconValue]), resolved against the server. Its
 * [IconSource.provider] is the brand art to draw, as web's `SpecIcon`; [endpointConfigs] lets that
 * include the spec endpoint's served `providerId`.
 */
fun ModelSpec.iconSource(
    agents: List<Agent>,
    serverUrl: String,
    endpointConfigs: Map<String, EndpointConfig>? = null,
): IconSource =
    resolveIconSource(iconValue(agentAvatar(agents)), toSeed()?.endpoint, serverUrl, endpointConfigs)

/** What a reply on this spec, sent to [endpoint], draws ([replyIconSource]): the same as its saved reply. */
fun ModelSpec.replyIconSource(
    endpoint: String?,
    agents: List<Agent>,
    serverUrl: String,
    endpointConfigs: Map<String, EndpointConfig>?,
): IconSource {
    val avatar = agentAvatar(agents)
    val replyAvatar = avatar?.takeIf { endpoint == EndpointConstants.AGENTS && it.isNotBlank() }
    return replyIconSource(iconValue(avatar), endpoint, serverUrl, endpointConfigs, replyAvatar)
}

private fun ModelSpec.agentAvatar(agents: List<Agent>): String? =
    preset?.agentId?.let { id -> agents.firstOrNull { it.id == id }?.avatarUrl }

/** [iconSource] for a picker row, or null when the spec hides its icon there (`showIconInMenu: false`). */
fun ModelSpec.menuIconSource(
    agents: List<Agent>,
    serverUrl: String,
    endpointConfigs: Map<String, EndpointConfig>? = null,
): IconSource? =
    iconSource(agents, serverUrl, endpointConfigs).takeUnless { showIconInMenu == false }
