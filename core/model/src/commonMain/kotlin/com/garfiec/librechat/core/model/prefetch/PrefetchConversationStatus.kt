package com.garfiec.librechat.core.model.prefetch

/** One conversation's place in the warm set, as the settings readout presents it. */
data class PrefetchConversationStatus(
    val conversationId: String,
    val title: String,
    val pinned: Boolean,
    /** When it was last warmed, or null if it never has been. */
    val warmedAt: Long?,
    /** True when the watermark still matches the server's `updatedAt` — nothing to re-fetch. */
    val isCurrent: Boolean,
)
