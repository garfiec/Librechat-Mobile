package com.garfiec.librechat.core.model.response

import com.garfiec.librechat.core.model.Message
import kotlinx.serialization.Serializable

/**
 * `GET /api/messages?search=`. The route always answers `nextCursor: null`; it is kept so a server
 * that starts paging doesn't go unnoticed.
 */
@Serializable
data class MessageSearchResponse(
    val messages: List<Message> = emptyList(),
    val nextCursor: String? = null,
)
