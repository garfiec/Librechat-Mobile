package com.garfiec.librechat.core.model.response

import com.garfiec.librechat.core.model.serializer.LenientInstantSerializer
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * `POST /api/convos/seen`. `modified: false` means the acknowledged reply is no longer the newest:
 * a later one landed, so the chat stays unread.
 */
@Serializable
data class ConvoSeenResponse(
    val modified: Boolean = false,
)

/** `POST /api/convos/unread`: the read state the server now holds for the chat. */
@Serializable
data class ConvoUnreadResponse(
    val modified: Boolean = false,
    @Serializable(with = LenientInstantSerializer::class)
    val lastResponseAt: Instant? = null,
    val lastResponseMessageId: String? = null,
    val lastResponseIsManual: Boolean? = null,
    val isMarkedUnread: Boolean? = null,
)
