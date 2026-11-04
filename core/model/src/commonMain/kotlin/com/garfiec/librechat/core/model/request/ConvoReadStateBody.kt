package com.garfiec.librechat.core.model.request

import kotlinx.serialization.Serializable

/** `POST /api/convos/seen` (v0.8.9-rc1). */
@Serializable
data class ConvoSeenBody(
    val arg: ConvoSeenArg,
)

/**
 * [lastResponseAt] names the reply that was on screen (ISO 8601), so a newer one persisted from
 * another device in the meantime is not acknowledged along with it.
 */
@Serializable
data class ConvoSeenArg(
    val conversationId: String,
    val lastResponseAt: String,
)

/** `POST /api/convos/unread` (v0.8.9-rc1). */
@Serializable
data class ConvoUnreadBody(
    val arg: ConvoUnreadArg,
)

@Serializable
data class ConvoUnreadArg(
    val conversationId: String,
)
