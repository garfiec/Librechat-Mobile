package com.garfiec.librechat.core.model.response

import com.garfiec.librechat.core.model.Conversation
import kotlinx.serialization.Serializable

/** `POST /api/convos/duplicate` body. `messages` stays undecoded so an unusual message can't fail a duplicate the server made. */
@Serializable
data class DuplicateConversationResponse(
    val conversation: Conversation,
)
