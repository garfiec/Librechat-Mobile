package com.garfiec.librechat.core.model.request

import kotlinx.serialization.Serializable

/**
 * Body of `POST /api/share/:shareId/fork` (v0.8.8 line, #13714).
 *
 * [targetMessageIndex] is a position in the snapshot's `messages`, NOT a message id: a recipient
 * never sees server message ids (they are re-anonymized per request). It is the index of the tip
 * of the branch the viewer has active, and the server copies the direct path to that message.
 *
 * [shareRevision] is the snapshot's `updatedAt`. A link keeps its id across an update, so an
 * index read from one revision could land on another branch of the next; the server refuses a
 * mismatch with 409 `SHARE_REVISION_MISMATCH`. Without a revision the server ignores the index
 * and copies the whole share, so the two only mean something together.
 */
@Serializable
data class ForkSharedConversationRequest(
    val targetMessageIndex: Int? = null,
    val shareRevision: String? = null,
)
