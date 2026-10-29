package com.garfiec.librechat.core.model.request

import kotlinx.serialization.Serializable

/**
 * Body of `POST /api/tags` (not `arg`-wrapped). Defaults are omitted on the wire (`encodeDefaults = false`).
 *
 * The server returns an existing tag of the same name unchanged and skips the attach, so
 * [addToConversation] only takes effect for a name that is new.
 */
@Serializable
data class TagCreateRequest(
    val tag: String,
    val description: String? = null,
    val addToConversation: Boolean = false,
    val conversationId: String? = null,
)
