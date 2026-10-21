package com.garfiec.librechat.core.model.share

/**
 * The orders `GET /api/share` accepts. The route takes `sortBy` = `createdAt` | `title` and
 * `sortDirection` = `asc` | `desc`, and silently falls back to newest first on anything else.
 *
 * A page cursor is only valid for the sort that produced it (the route 400s otherwise), so a
 * change of sort or search must start again from the first page.
 */
enum class SharedLinksSort(val sortBy: String, val sortDirection: String) {
    NEWEST(sortBy = "createdAt", sortDirection = "desc"),
    OLDEST(sortBy = "createdAt", sortDirection = "asc"),
    TITLE(sortBy = "title", sortDirection = "asc"),
}

/**
 * Longest `search` the shared-links list accepts (upstream `MAX_SHARED_LINK_SEARCH_LENGTH`).
 * The route 400s past it, after trimming.
 */
const val MAX_SHARED_LINK_SEARCH_LENGTH = 256
