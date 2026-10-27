package com.garfiec.librechat.core.model.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Compare's second model (`addedConvo`). The sampling fields carry lane B's spec preset, flat, as
 * the server reads them (`packages/api/src/agents/added.ts`). v0.8.8 applies them only when the
 * primary is a saved agent. Unset fields are left off the wire.
 */
@Serializable
data class AddedConversation(
    val conversationId: String? = null,
    val parentMessageId: String? = null,
    val endpoint: String? = null,
    val endpointType: String? = null,
    val modelDisplayLabel: String? = null,
    val key: String? = null,
    @SerialName("agent_id") val agentId: String? = null,
    val model: String? = null,
    val modelLabel: String? = null,
    val spec: String? = null,
    val temperature: Double? = null,
    @SerialName("top_p") val topP: Double? = null,
    val topK: Int? = null,
    @SerialName("presence_penalty") val presencePenalty: Double? = null,
    @SerialName("frequency_penalty") val frequencyPenalty: Double? = null,
    val maxOutputTokens: Int? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
)
