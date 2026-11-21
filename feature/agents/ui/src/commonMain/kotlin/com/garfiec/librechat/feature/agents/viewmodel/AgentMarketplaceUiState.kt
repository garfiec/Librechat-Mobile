package com.garfiec.librechat.feature.agents.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.AgentCategory
import com.garfiec.librechat.feature.agents.AgentCardDisplayData

/**
 * A list read that failed. The screen says so in its own words; [serverMessage] is the server's
 * explanation, added under them only when the server wrote one.
 */
@Immutable
data class AgentListError(val serverMessage: String?)

@Immutable
data class AgentMarketplaceUiState(
    val agents: List<AgentCardDisplayData> = emptyList(),
    /**
     * A first page for a new chip or search is on its way. True from the first frame, so the
     * screen opens on placeholders rather than an empty state that the first page then replaces.
     */
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    /** Pull-to-refresh only; a quiet reload on return never sets it. */
    val isRefreshing: Boolean = false,
    /** The last first-page read failed. With no [agents] the screen is an error page, else a snackbar. */
    val loadError: AgentListError? = null,
    /** The last next-page read failed: paging stops until [AgentMarketplaceViewModel.retryLoadMore]. */
    val loadMoreFailed: Boolean = false,
    /** Null until the default chip is picked, once categories and the role are in. */
    val selectedChip: AgentChip? = null,
    /** As typed; reads use it trimmed. */
    val searchQuery: String = "",
    /** The chip and search [agents] answers, which run behind [selectedChip] and [searchQuery] while loading. */
    val shownQuery: AgentListQuery? = null,
    val categories: List<AgentCategory> = emptyList(),
    val hasMore: Boolean = false,
    /** The last page's `after`: the cursor [AgentMarketplaceViewModel.loadMore] reads the next page by. */
    val nextCursor: String? = null,
    /**
     * The server takes `sort` and `mine` (v0.8.9-rc1). Below that the route ignores both, so the
     * row that sets them is hidden rather than offering controls that do nothing.
     */
    val sortAvailable: Boolean = false,
    /** The order the next read asks for. Not remembered across visits (web parity). */
    val sort: AgentSort = AgentSort.NEWEST,
    /** "My agents": only agents this user authored. Combines with any chip but Top picks. */
    val mineOnly: Boolean = false,
    // Role-permission gates — default permissive.
    val agentsEnabled: Boolean = true,
    val agentsCreateEnabled: Boolean = true,
    val marketplaceEnabled: Boolean = true,
) {
    /** Whether this role may browse agents at all: AGENTS.USE and MARKETPLACE.USE (web parity). */
    val canBrowse: Boolean get() = agentsEnabled && marketplaceEnabled

    /**
     * The chip row: Top picks (only when the server has promoted agents), All, Editable (only with
     * AGENTS.CREATE), then the server's real categories.
     */
    val chips: List<AgentChip>
        get() = availableChips(categories, agentsCreateEnabled)
}

internal fun availableChips(categories: List<AgentCategory>, canCreate: Boolean): List<AgentChip> = buildList {
    if (categories.any { it.value == AgentListFilter.PROMOTED }) add(AgentChip.TopPicks)
    add(AgentChip.All)
    if (canCreate) add(AgentChip.Editable)
    categories
        .filter { it.value != AgentListFilter.PROMOTED && it.value != AgentListFilter.ALL }
        .forEach { add(AgentChip.Category(it.value)) }
}
