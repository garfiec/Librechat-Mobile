package com.garfiec.librechat.core.model.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `GET /api/share/link/:conversationId`: whether the caller already has a live share link for the
 * conversation. [success] is false, with a null [shareId], when there is none or it has expired.
 */
@Serializable
data class ShareLinkLookup(
    @SerialName("_id") val id: String? = null,
    val success: Boolean = false,
    val shareId: String? = null,
    val targetMessageId: String? = null,
    val snapshotFiles: Boolean? = null,
    val conversationId: String? = null,
)
