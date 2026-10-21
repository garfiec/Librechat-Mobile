package com.garfiec.librechat.core.model

/**
 * Wrapper for a single page of agent marketplace results.
 *
 * [nextCursor] is the response's `after`: pass it back as `cursor` to read the next page.
 */
data class PaginatedAgents(
    val agents: List<Agent>,
    val hasMore: Boolean,
    val nextCursor: String?,
)
