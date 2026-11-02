package com.garfiec.librechat.core.model.response

import com.garfiec.librechat.core.model.Conversation
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeleteChatProjectResponse(
    val deletedCount: Int = 0,
    val modifiedCount: Int = 0,
)

@Serializable
data class AssignConversationToProjectResponse(
    val conversation: Conversation,
    val previousProjectId: String? = null,
    val projectId: String? = null,
)

/**
 * One reference file of a project (`GET /api/projects/:id/files`, v0.8.9-rc1; upstream
 * `TChatProjectFile`). A file deleted or expired on the server stays listed, with no name, as
 * unavailable, so the user can still remove it.
 */
@Serializable
data class ChatProjectFile(
    @SerialName("file_id") val fileId: String,
    val filename: String? = null,
    val type: String? = null,
    val bytes: Long? = null,
    /** `ready` or `unavailable` on the wire. Kept as a string so a new value can't fail the list. */
    val availability: String? = null,
) {
    /** Anything but `ready` (including a value this build doesn't know) reads as unavailable. */
    val isReady: Boolean get() = availability == AVAILABILITY_READY

    companion object {
        const val AVAILABILITY_READY = "ready"
    }
}

/**
 * One of the user's own files that could be added to a project
 * (`GET /api/projects/:id/files/available`, v0.8.9-rc1). The server already leaves out the ones
 * attached.
 */
@Serializable
data class AvailableProjectFile(
    @SerialName("file_id") val fileId: String,
    val filename: String? = null,
    val type: String? = null,
    val bytes: Long? = null,
)

@Serializable
data class AvailableProjectFilesResponse(
    val files: List<AvailableProjectFile> = emptyList(),
    val nextCursor: String? = null,
)
