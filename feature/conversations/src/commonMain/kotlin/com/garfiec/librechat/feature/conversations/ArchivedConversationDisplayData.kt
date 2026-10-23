package com.garfiec.librechat.feature.conversations

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.config.ModelSpecs
import com.garfiec.librechat.core.model.config.labelFor
import com.garfiec.librechat.core.model.resolveEndpointIconUrl
import kotlin.time.Instant

@Immutable
data class ArchivedConversationDisplayData(
    val id: String,
    val title: String,
    val endpoint: String?,
    val modelLabel: String?,
    // The chat's last activity. The server keeps no archive time, and archiving doesn't bump this.
    val updatedAt: Instant?,
    val endpointIconUrl: String? = null,
)

fun Conversation.toArchivedDisplayData(
    endpointConfigs: Map<String, EndpointConfig> = emptyMap(),
    modelSpecs: ModelSpecs? = null,
    agentNames: Map<String, String> = emptyMap(),
) = ArchivedConversationDisplayData(
    id = conversationId ?: "",
    title = title ?: "New Chat",
    endpoint = endpoint,
    modelLabel = modelSpecs?.labelFor(spec) ?: model?.let { agentNames[it] ?: it },
    updatedAt = updatedAt,
    endpointIconUrl = resolveEndpointIconUrl(endpointConfigs),
)
