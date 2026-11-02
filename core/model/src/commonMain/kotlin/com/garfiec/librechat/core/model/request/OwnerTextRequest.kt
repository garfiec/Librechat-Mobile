package com.garfiec.librechat.core.model.request

import kotlinx.serialization.Serializable

/**
 * Body of `POST /api/messages/:conversationId/owner-text` (v0.8.9-rc1): 1 to 50 message ids. Bare,
 * not under `arg`: the route's schema is strict and refuses any other key.
 */
@Serializable
data class OwnerTextRequest(val messageIds: List<String>)
