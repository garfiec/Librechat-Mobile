package com.garfiec.librechat.feature.agents.viewmodel

import androidx.compose.runtime.Immutable

/** What the agent index's list area shows, decided from [AgentMarketplaceUiState] by [listState]. */
@Immutable
internal sealed interface AgentListState {
    /** A first page is on its way and there is nothing to show yet: stand-in cards, not an empty state. */
    data object Placeholders : AgentListState

    /** The first page failed with nothing to show: the full-page error with Retry. */
    data class Error(val serverMessage: String?) : AgentListState

    /**
     * The server has no agents this user can see: All, no search, nothing back. Search and chips
     * have nothing to narrow, and the state's own New agent button replaces the FAB.
     */
    data object ServerEmpty : AgentListState

    /** A chip or search found nothing. [chip] is the one that was read; [wayOut] what the state offers. */
    data class NoMatches(val chip: AgentChip, val wayOut: AgentListWayOut) : AgentListState

    data object Agents : AgentListState
}

/** The way out of a no-results state. */
internal enum class AgentListWayOut {
    /** Nothing narrows the search but the text (All, or Top picks, which searches everything): clear it. */
    ClearSearch,

    /** A narrowing chip with a search: search every agent instead, keeping the text. */
    SearchAll,

    /** A narrowing chip with no search, Top picks included: show every agent. */
    ShowAll,
}

internal fun AgentMarketplaceUiState.listState(): AgentListState {
    val shown = shownQuery
    return when {
        agents.isNotEmpty() -> AgentListState.Agents
        isLoading -> AgentListState.Placeholders
        // A pull on the error page: placeholders while it reads. A pull over an empty or no-matches
        // answer keeps that answer under the pull spinner, rather than flashing the search, chips and FAB.
        isRefreshing && (loadError != null || shown == null) -> AgentListState.Placeholders
        loadError != null -> AgentListState.Error(loadError.serverMessage)
        // Nothing has answered yet (the read waits on the role); no state to claim about the server.
        shown == null -> AgentListState.Placeholders
        else -> shown.emptyState()
    }
}

/** The New agent FAB: for roles that may create, except over [AgentListState.ServerEmpty], which has its own button. */
internal val AgentMarketplaceUiState.showsNewAgentFab: Boolean
    get() = canBrowse && agentsCreateEnabled && listState() != AgentListState.ServerEmpty

private fun AgentListQuery.emptyState(): AgentListState {
    val searching = search.isNotEmpty()
    // While searching, Top picks searches everything, like All (AgentListFilter.of).
    val narrowed = chip != AgentChip.All && !(searching && chip == AgentChip.TopPicks)
    return when {
        !narrowed && !searching -> AgentListState.ServerEmpty
        !narrowed -> AgentListState.NoMatches(chip, AgentListWayOut.ClearSearch)
        searching -> AgentListState.NoMatches(chip, AgentListWayOut.SearchAll)
        else -> AgentListState.NoMatches(chip, AgentListWayOut.ShowAll)
    }
}
