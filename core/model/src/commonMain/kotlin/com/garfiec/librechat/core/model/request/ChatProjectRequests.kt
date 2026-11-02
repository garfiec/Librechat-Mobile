package com.garfiec.librechat.core.model.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Bodies for the Chat Projects endpoints (v0.8.7). Unlike the conversation
 * mutation endpoints, the projects routes read `req.body` fields directly — they
 * are NOT wrapped in an `arg` envelope.
 */
@Serializable
data class CreateChatProjectRequest(
    val name: String,
    val description: String? = null,
)

@Serializable
data class UpdateChatProjectRequest(
    val name: String? = null,
    val description: String? = null,
)

/**
 * Body for an instructions save (`PATCH /api/projects/:id`, v0.8.9-rc1). Kept apart from
 * [UpdateChatProjectRequest]: a name or description edit never carries [contextRevision] (it can't
 * conflict), and this one never carries a name, which the server would validate.
 */
@Serializable
data class UpdateChatProjectInstructionsRequest(
    val instructions: String,
    val contextRevision: Int? = null,
)

/** Body for `POST /api/projects/:id/files` (v0.8.9-rc1). */
@Serializable
data class AddChatProjectFileRequest(
    @SerialName("file_id") val fileId: String,
)

/** Body for `PUT /api/projects/conversations/:conversationId`. `projectId = null` unassigns. */
@Serializable
data class AssignConversationToProjectRequest(
    val projectId: String? = null,
)
