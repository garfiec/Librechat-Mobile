package com.garfiec.librechat.core.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * The chat list's filter facets (v0.8.9-rc1 `GET /api/convos`). Each one narrows; an unset facet
 * leaves the list alone. Only one date facet is ever set: the sheet picks updated or created.
 */
@Serializable
data class ConversationListFilters(
    val updatedAfter: Instant? = null,
    val createdAfter: Instant? = null,
    val endpoints: List<String> = emptyList(),
    val hasFiles: Boolean = false,
    val sharedOnly: Boolean = false,
    /** Bookmark tags, OR-matched by the server; empty means not filtering on bookmarks. */
    val tags: List<String> = emptyList(),
) {
    val isActive: Boolean
        get() = updatedAfter != null || createdAfter != null || endpoints.isNotEmpty() ||
            hasFiles || sharedOnly || tags.isNotEmpty()

    companion object {
        val None: ConversationListFilters = ConversationListFilters()
    }
}
