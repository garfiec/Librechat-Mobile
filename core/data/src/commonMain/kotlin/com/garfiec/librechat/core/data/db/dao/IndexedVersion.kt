package com.garfiec.librechat.core.data.db.dao

/** The `updatedAt` the search index holds for one message, read to skip rewriting unchanged rows. */
data class IndexedVersion(
    val messageId: String,
    val updatedAt: Long,
)
