package com.garfiec.librechat.feature.conversations.components

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.SAVED_TAG
import com.garfiec.librechat.core.model.config.ModelSpecs
import com.garfiec.librechat.core.model.config.labelFor
import com.garfiec.librechat.core.model.iconEndpoint
import com.garfiec.librechat.core.model.iconProvider
import com.garfiec.librechat.core.model.media.ProviderId
import com.garfiec.librechat.core.model.resolveEndpointIconUrl
import kotlin.time.Instant

@Immutable
data class ConversationDisplayData(
    val conversationId: String,
    val title: String,
    val endpoint: String?,
    val model: String?,
    // Only the *formatting* of this happens at render time — see LocalRelativeTimeReference.
    val updatedAt: Instant?,
    val isFavorite: Boolean,
    val endpointIconUrl: String? = null,
    val iconEndpoint: String? = endpoint,
    /**
     * The brand art the row draws when [endpointIconUrl] is null or fails to load; beats [iconEndpoint].
     * Null when the endpoint's configured icon is an image, as on web.
     */
    val iconProvider: ProviderId? = null,
)

fun Conversation.toDisplayData(
    endpointConfigs: Map<String, EndpointConfig>,
    serverUrl: String,
    agentNames: Map<String, String> = emptyMap(),
): ConversationDisplayData = ConversationDisplayData(
    conversationId = conversationId ?: "",
    title = title ?: "New Chat",
    endpoint = endpoint,
    model = rowModelLabel(null, agentNames),
    updatedAt = updatedAt,
    isFavorite = SAVED_TAG in tags,
    endpointIconUrl = resolveEndpointIconUrl(endpointConfigs, serverUrl),
    iconEndpoint = iconEndpoint(endpointConfigs),
    iconProvider = iconProvider(endpointConfigs),
)

private const val AGENT_ID_PREFIX = "agent_"

/**
 * The agent an agent chat talks to, or null for any other chat. `agent_id` names it; `model` is
 * unset, the agent id, or a plain model (after a fork) depending on how the chat was saved.
 */
fun Conversation.agentKey(): String? = (agentId ?: model)?.takeIf { it.startsWith(AGENT_ID_PREFIX) }

/** A row's model subtitle: the spec's label, else the agent's name (null until known), else the model. */
fun Conversation.rowModelLabel(modelSpecs: ModelSpecs?, agentNames: Map<String, String>): String? =
    modelSpecs?.labelFor(spec) ?: agentKey().let { key -> if (key == null) model else agentNames[key] }
