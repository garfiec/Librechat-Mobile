package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.core.model.media.IconSource
import com.garfiec.librechat.core.model.media.iconValue
import com.garfiec.librechat.core.model.media.resolveIconSource

/**
 * The icon a spec shows wherever it is listed or opened: its `iconURL`, its preset's, its agent's
 * avatar, then its endpoint's glyph ([iconValue]), resolved against the server.
 */
fun ModelSpec.iconSource(agents: List<Agent>, serverUrl: String): IconSource =
    resolveIconSource(iconValue(agentAvatar(agents)), toSeed()?.endpoint, serverUrl)

/**
 * [iconSource] when the spec has an icon of its own (its `iconURL`, its preset's, its agent's avatar),
 * null when it would only borrow its endpoint's glyph.
 */
fun ModelSpec.ownIconSource(agents: List<Agent>, serverUrl: String): IconSource? {
    val own = iconValue(agentAvatar(agents), withEndpoint = false) ?: return null
    return resolveIconSource(own, toSeed()?.endpoint, serverUrl)
}

private fun ModelSpec.agentAvatar(agents: List<Agent>): String? =
    preset?.agentId?.let { id -> agents.firstOrNull { it.id == id }?.avatarUrl }

/** [iconSource] for a picker row, or null when the spec hides its icon there (`showIconInMenu: false`). */
fun ModelSpec.menuIconSource(agents: List<Agent>, serverUrl: String): IconSource? =
    iconSource(agents, serverUrl).takeUnless { showIconInMenu == false }
