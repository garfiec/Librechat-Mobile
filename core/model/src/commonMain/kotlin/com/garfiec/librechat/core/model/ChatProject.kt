package com.garfiec.librechat.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A Chat Project (folder) that conversations can be assigned to (v0.8.7).
 * Mirrors upstream `TChatProject`; the server identifier is the Mongo `_id`.
 */
@Serializable
data class ChatProject(
    @SerialName("_id") val id: String,
    val name: String,
    val description: String? = null,
    val user: String? = null,
    val conversationCount: Int = 0,
    val lastConversationAt: String? = null,
    val lastConversationId: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    /**
     * The project's standing brief for the model (v0.8.9-rc1). Only the detail route
     * (`GET /api/projects/:id`) sends it; the list leaves it out and sends [hasInstructions].
     */
    val instructions: String? = null,
    /**
     * Bumped by the server on every instructions, file add or file remove change (v0.8.9-rc1). An
     * instructions save sends back the value it was based on; a stale one is refused with 409
     * `CHAT_PROJECT_CONFLICT`.
     */
    val contextRevision: Int? = null,
    /** The attached reference files' ids, in order (detail route only, v0.8.9-rc1). */
    @SerialName("file_ids") val fileIds: List<String>? = null,
    /** List route only (v0.8.9-rc1). */
    val hasInstructions: Boolean? = null,
    /** List route only (v0.8.9-rc1). */
    val fileCount: Int? = null,
) {
    companion object {
        /**
         * Sentinel project id for the read-side "no project" filter
         * (`GET /api/convos?projectId=unassigned`). Distinct from the write-side
         * unassign (`assignConversation(projectId = null)`).
         */
        const val UNASSIGNED = "unassigned"
    }
}
