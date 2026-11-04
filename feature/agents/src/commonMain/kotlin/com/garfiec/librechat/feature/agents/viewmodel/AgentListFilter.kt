package com.garfiec.librechat.feature.agents.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.PermissionBits

/** One marketplace chip. Exactly one is selected; the screen opens on [TopPicks] when offered, else [All]. */
@Immutable
sealed interface AgentChip {
    /** The categories route's `promoted` sentinel: agents an admin marked as Top picks. */
    data object TopPicks : AgentChip

    /** The categories route's `all` sentinel: no filter. */
    data object All : AgentChip

    /** Agents the user can edit. Client-side chip, offered only with AGENTS.CREATE. */
    data object Editable : AgentChip

    /** A real category, by its server value. */
    data class Category(val value: String) : AgentChip
}

/**
 * The marketplace orders (v0.8.9-rc1), in the order the menu offers them. [wire] is the `sort`
 * value, from upstream `AGENT_SORT_OPTIONS` (`packages/api/src/agents/marketplace.ts`); mirrored by
 * hand (scripts/mirrors.json: agent-marketplace-sort). The web always sends one, [NEWEST] by default.
 */
enum class AgentSort(val wire: String) {
    NEWEST("newest"),
    OLDEST("oldest"),
    POPULAR("popular"),
    AUTHOR("author"),
}

/**
 * What one list read asks for: a chip, the trimmed search, and (v0.8.9-rc1 servers only) the order
 * and "My agents". [sort] is null, and [mine] false, on a server that predates them.
 */
@Immutable
data class AgentListQuery(
    val chip: AgentChip,
    val search: String,
    val sort: AgentSort? = null,
    val mine: Boolean = false,
)

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
 * Editable is this app's own chip: `requiredPermission` = EDIT, the bit the list route checks each
 * agent's effective permissions against. It narrows a search too, and ignores categories. The
 * answer is every agent the user can edit, not only the ones they own: agents shared with edit
 * rights, and for an admin every agent. Hence "Editable" rather than "Yours".
 *
 * Mirrored by hand (scripts/mirrors.json: agent-category-sentinels, agent-grid-query-params).
 */
internal data class AgentListFilter(
    val search: String? = null,
    val category: String? = null,
    val promoted: Boolean = false,
    val requiredPermission: Int? = null,
) {
    companion object {
        const val PROMOTED = "promoted"
        const val ALL = "all"

        fun of(chip: AgentChip, searchQuery: String): AgentListFilter {
            val search = searchQuery.trim().ifEmpty { null }
            return when (chip) {
                AgentChip.TopPicks -> AgentListFilter(search = search, promoted = search == null)
                AgentChip.All -> AgentListFilter(search = search)
                AgentChip.Editable -> AgentListFilter(search = search, requiredPermission = PermissionBits.EDIT)
                is AgentChip.Category -> AgentListFilter(search = search, category = chip.value)
            }
        }
    }
}
