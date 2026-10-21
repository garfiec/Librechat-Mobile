package com.garfiec.librechat.feature.agents.viewmodel

import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.media.resolveAvatarUrl
import com.garfiec.librechat.feature.agents.AgentCardDisplayData
import com.garfiec.librechat.feature.agents.AgentContactDisplayData
import com.garfiec.librechat.feature.agents.AgentDetailDisplayData
import com.garfiec.librechat.feature.agents.util.agentDescriptionPreview
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** A list row as the marketplace card shows it. [baseUrl] resolves a relative avatar path. */
internal fun Agent.toCardDisplayData(baseUrl: String): AgentCardDisplayData {
    val contact = resolvedContact()
    return AgentCardDisplayData(
        id = id,
        name = name?.trim()?.takeIf { it.isNotEmpty() },
        // Admins write descriptions in the web editor, some as HTML; a card shows two lines of text.
        description = description?.agentDescriptionPreview(),
        avatarUrl = resolveAvatarUrl(avatarUrl, baseUrl),
        categoryValue = category?.trim()?.takeIf { it.isNotEmpty() },
        isPromoted = isPromoted,
        contactName = contact?.name ?: contact?.email,
    )
}

/** The agent page's copy of [this]. The description is kept as written: the page renders its HTML. */
internal fun Agent.toDetailDisplayData(baseUrl: String): AgentDetailDisplayData = AgentDetailDisplayData(
    id = id,
    name = name?.trim()?.takeIf { it.isNotEmpty() },
    description = description?.takeIf { it.isNotBlank() },
    avatarUrl = resolveAvatarUrl(avatarUrl, baseUrl),
    model = model?.trim()?.takeIf { it.isNotEmpty() },
    categoryValue = category?.trim()?.takeIf { it.isNotEmpty() },
    isPromoted = isPromoted,
    isPublic = isPublic == true,
    updatedAt = updatedAt,
    tools = tools.orEmpty().filter { it.isNotBlank() },
    conversationStarters = conversationStarters.map { it.trim() }.filter { it.isNotEmpty() },
    contact = resolvedContact(),
    linkBaseUrl = baseUrl.takeIf { it.isNotBlank() },
)

/**
 * The web link that opens a new chat with [agentId], as the web's own Copy link builds it
 * (upstream `RESOURCE_CONFIGS[AGENT].getResourceUrl`, registered in `scripts/mirrors.json`).
 * The web uses its origin; the server URL is that origin plus any path it is deployed under.
 */
internal fun agentChatLink(baseUrl: String, agentId: String): String = "${baseUrl.trimEnd('/')}/c/new?agent_id=$agentId"

/**
 * Who to reach about this agent: its own support contact, falling back to the owner's.
 *
 * The fallback is whole rather than field-by-field: an agent that names a support contact has
 * answered the question, and mixing its name with the owner's email would invent a person. An
 * entry with neither a name nor an email says nothing and resolves to null. The raw `author` id is
 * never a contact.
 */
internal fun Agent.resolvedContact(): AgentContactDisplayData? = supportContact.toContact() ?: ownerContact.toContact()

private fun JsonElement?.toContact(): AgentContactDisplayData? {
    val obj = this as? JsonObject ?: return null
    val name = (obj["name"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
    val email = (obj["email"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
    if (name == null && email == null) return null
    return AgentContactDisplayData(name = name, email = email)
}
