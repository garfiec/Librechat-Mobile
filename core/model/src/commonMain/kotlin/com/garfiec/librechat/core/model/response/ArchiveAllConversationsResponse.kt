package com.garfiec.librechat.core.model.response

import kotlinx.serialization.Serializable

/** `POST /api/convos/archive/all` (v0.8.8-rc2). */
@Serializable
data class ArchiveAllConversationsResponse(
    val archivedCount: Int = 0,
)
