package com.garfiec.librechat.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One of the user's shared links, as `GET /api/share` lists it and as create / update return it.
 *
 * [isPublic] is null when the server didn't say, which on v0.8.8 is always: who can open a link
 * is the link's ACL, not a field on it. Absent means unknown, never public.
 */
@Serializable
data class SharedLink(
    @SerialName("_id") val id: String? = null,
    val conversationId: String,
    val title: String? = null,
    val user: String? = null,
    val shareId: String? = null,
    val isPublic: Boolean? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)
