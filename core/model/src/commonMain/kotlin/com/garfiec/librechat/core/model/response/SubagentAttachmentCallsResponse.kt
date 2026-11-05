package com.garfiec.librechat.core.model.response

import com.garfiec.librechat.core.model.content.SubagentAttachmentCall
import kotlinx.serialization.Serializable

/**
 * One file whose owner the client asks `GET …/:messageId/subagent-calls` for (upstream
 * `SubagentAttachmentFile`): the tool call it was saved under, with the scope saved with it.
 * Blank scopes are left out on the wire, as upstream does.
 */
@Serializable
data class SubagentAttachmentFile(
    val toolCallId: String,
    val agentId: String? = null,
    val stepId: String? = null,
)

/**
 * `GET /api/messages/:conversationId/:messageId/subagent-calls?files=[…]` (v0.8.9-rc1): for each
 * top-level subagent call of the stored message, the child calls of its transcript the asked files
 * belong to. Calls with none are left out.
 */
@Serializable
data class SubagentAttachmentCallsResponse(
    val parts: List<SubagentAttachmentCallsEntry> = emptyList(),
)

/** A top-level subagent call (by its identity) and the child calls the asked files were saved under. */
@Serializable
data class SubagentAttachmentCallsEntry(
    val toolCallId: String,
    val stepId: String? = null,
    val agentId: String? = null,
    val calls: List<SubagentAttachmentCall> = emptyList(),
)
