package com.garfiec.librechat.core.model.response

import com.garfiec.librechat.core.model.content.AgentToolCall
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `GET /api/messages/:conversationId/:messageId/parts/:partIndex` (v0.8.9-rc1): one stored tool call
 * in full, `subagent_content` included. [partIndex] is only echoed; the server finds the call by
 * its identity.
 */
@Serializable
data class ToolCallPartResponse(
    val conversationId: String? = null,
    val messageId: String? = null,
    val partIndex: Int? = null,
    @SerialName("tool_call") val toolCall: AgentToolCall,
)
