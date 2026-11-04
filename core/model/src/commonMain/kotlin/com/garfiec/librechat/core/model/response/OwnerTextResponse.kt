package com.garfiec.librechat.core.model.response

import kotlinx.serialization.Serializable

/** `POST /api/messages/:conversationId/owner-text` (v0.8.9-rc1). */
@Serializable
data class OwnerTextResponse(val messages: List<OwnerText> = emptyList())

/**
 * The author's original text of one redacted user turn. Valid only for the stored turn it was read
 * against: [revision] must equal the message's `privacyRevision` and [canonicalText] its (filtered)
 * `text`. [text] is absent when the server could not open it.
 *
 * Never cached, persisted, indexed or exported: the server answers it `private, no-store`.
 */
@Serializable
data class OwnerText(
    val messageId: String,
    val revision: String? = null,
    val canonicalText: String? = null,
    val text: String? = null,
)
