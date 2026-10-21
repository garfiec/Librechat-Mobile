package com.garfiec.librechat.feature.agents.viewmodel

/**
 * The list filter one marketplace chip and search query turn into.
 *
 * `GET /api/agents/categories` appends two entries that are not categories: `promoted` (only when
 * some agent is promoted) and `all`. Sent as `category`, the route filters on a category nobody
 * has and answers with nothing, so they map to the route's own parameters instead, exactly as the
 * web grid does (upstream `client/src/components/Agents/AgentGrid.tsx`, `queryParams`):
 * - no search: `promoted` → `promoted=1`; `all` → no filter; anything else → `category`.
 * - searching: `promoted` and `all` send neither, so the search covers every agent; a real
 *   category still narrows it.
 *
 * Mirrored by hand (scripts/mirrors.json: agent-category-sentinels, agent-grid-query-params).
 */
internal data class AgentListFilter(
    val search: String? = null,
    val category: String? = null,
    val promoted: Boolean = false,
) {
    companion object {
        const val PROMOTED = "promoted"
        const val ALL = "all"

        fun of(selectedCategory: String?, searchQuery: String): AgentListFilter {
            val search = searchQuery.ifBlank { null }
            val category = selectedCategory?.takeUnless { it == PROMOTED || it == ALL }
            return AgentListFilter(
                search = search,
                category = category,
                promoted = search == null && selectedCategory == PROMOTED,
            )
        }
    }
}
