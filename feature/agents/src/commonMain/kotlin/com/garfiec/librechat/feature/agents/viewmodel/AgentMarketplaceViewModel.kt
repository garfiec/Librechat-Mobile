package com.garfiec.librechat.feature.agents.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.ServerDataStore
import com.garfiec.librechat.core.data.repository.AgentRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.data.util.PermissionGate
import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.AgentCategory
import com.garfiec.librechat.core.model.PaginatedAgents
import com.garfiec.librechat.core.model.permissions.Permission
import com.garfiec.librechat.core.model.permissions.PermissionType
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import com.garfiec.librechat.core.model.permissions.hasAccessOrPermissive
import com.garfiec.librechat.feature.agents.AgentCardDisplayData
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

/**
 * The agent index: one list, read a page at a time by cursor, for one chip and search.
 *
 * Every read that replaces the list (a chip, a search, Retry, pull-to-refresh, the quiet reload on
 * return) goes through [startLoad], which cancels the one before it and any next-page read, and
 * stamps a generation that a late answer is checked against. So whatever arrives, the list only
 * ever shows the answer to the latest request.
 */
class AgentMarketplaceViewModel(
    private val agentRepository: AgentRepository,
    private val serverDataStore: ServerDataStore,
    private val roleRepository: RoleRepository,
    private val permissionGate: PermissionGate,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AgentMarketplaceUiState())
    val uiState: StateFlow<AgentMarketplaceUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var loadMoreJob: Job? = null
    private var searchJob: Job? = null

    /** Bumped by every [startLoad]; an answer stamped with an older one is dropped. */
    private var generation = 0

    /** What the latest [startLoad] asked for, so a search that trims to the same text doesn't reload. */
    private var requestedQuery: AgentListQuery? = null

    /** Set once the first read has started: until then, chip, search and resume wait for it. */
    private var initialLoadStarted = false

    /**
     * The grid asked for the next page while a reload of the same list (the quiet one on return, or
     * a pull) was running. The grid asks only when its item count or its near-the-end state changes,
     * and a reload that lands with the same agents changes neither, so the ask is kept and made
     * when the reload lands instead of being lost. A new chip or search drops it.
     */
    private var loadMoreAfterReload = false

    private enum class LoadMode {
        /** A new chip or search, or Retry: placeholders or a dimmed list until it lands. */
        REPLACE,

        /** Pull-to-refresh: the refresh spinner, the list stays. */
        REFRESH,

        /** Return to the screen: no indicator, as many agents as were loaded, failure stays silent. */
        QUIET,
    }

    companion object {
        private const val PAGE_SIZE = 20
        internal const val SEARCH_DEBOUNCE_MS = 300L
    }

    init {
        observePermissionFlags()
        loadInitialAgents()
    }

    /** Continuous collector mirroring the current role into UiState permission flags. */
    private fun observePermissionFlags() {
        viewModelScope.launch {
            roleRepository.userPermissions.collect { role ->
                _uiState.update {
                    it.copy(
                        agentsEnabled = role.hasAccessOrPermissive(PermissionType.AGENTS, Permission.USE),
                        agentsCreateEnabled = role.hasAccessOrPermissive(PermissionType.AGENTS, Permission.CREATE),
                        marketplaceEnabled = role.hasAccessOrPermissive(PermissionType.MARKETPLACE, Permission.USE),
                    )
                }
                fallBackIfChipGone()
            }
        }
    }

    /**
     * Reads the first page once the role allows browsing (permissive on timeout) and the categories
     * are in, so the default chip is known before anything is fetched: no flash of All before Top
     * picks, and one read, not two.
     */
    private fun loadInitialAgents() {
        viewModelScope.launch {
            // Categories are ungated server-side, so they load while the role is awaited.
            val categories = async { fetchCategories() }
            val role = permissionGate.awaitRole()
            categories.await()
            if (!role.allowsBrowsing()) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }
            _uiState.update { state ->
                val default = if (AgentChip.TopPicks in state.chips) AgentChip.TopPicks else AgentChip.All
                // A chip tapped while this waited is the user's pick, and wins.
                state.copy(selectedChip = state.selectedChip?.takeIf { it in state.chips } ?: default)
            }
            initialLoadStarted = true
            startLoad(LoadMode.REPLACE)
        }
    }

    private fun UserRolePermissions?.allowsBrowsing(): Boolean =
        this?.hasAccess(PermissionType.AGENTS, Permission.USE) != false &&
            this?.hasAccess(PermissionType.MARKETPLACE, Permission.USE) != false

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        if (!initialLoadStarted) return
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            if (currentQuery() != requestedQuery) startLoad(LoadMode.REPLACE)
        }
    }

    /** Selects [chip] and reloads. Tapping the selected chip does nothing: one is always selected. */
    fun onChipSelected(chip: AgentChip) {
        if (chip == _uiState.value.selectedChip) return
        _uiState.update { it.copy(selectedChip = chip) }
        if (!initialLoadStarted) return
        // A search still in its debounce is read with this chip, now.
        searchJob?.cancel()
        startLoad(LoadMode.REPLACE)
    }

    /** The no-results way out under a narrowing chip: All, keeping the search. */
    fun searchAllAgents() {
        searchJob?.cancel()
        _uiState.update { it.copy(selectedChip = AgentChip.All) }
        if (initialLoadStarted) startLoad(LoadMode.REPLACE)
    }

    /** The no-results way out with nothing narrowing but the search: All, search cleared. */
    fun showAllAgents() {
        searchJob?.cancel()
        _uiState.update { it.copy(selectedChip = AgentChip.All, searchQuery = "") }
        if (initialLoadStarted) startLoad(LoadMode.REPLACE)
    }

    /** Retry from the full-page error. */
    fun retry() {
        if (!initialLoadStarted) return
        startLoad(LoadMode.REPLACE)
    }

    /** Pull-to-refresh: the list from the first page, and the categories again. */
    fun refresh() {
        if (!initialLoadStarted) return
        startLoad(LoadMode.REFRESH)
        viewModelScope.launch {
            fetchCategories()
            fallBackIfChipGone()
        }
    }

    /**
     * Reloads quietly on return, so a delete or edit made on a pushed page shows when the user
     * comes back: as many agents as were loaded, no spinner, and a failure keeps what is shown.
     * Nothing before the first read has started (the first resume comes before it), and nothing
     * while another read is running.
     */
    fun refreshOnReturn() {
        if (!initialLoadStarted) return
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || state.isRefreshing || loadJob?.isActive == true) return
        startLoad(LoadMode.QUIET)
    }

    /**
     * Fetches the page after the last one read and appends it. No-ops while a new chip or search
     * or another page is loading, when there is no next page or no cursor, or after a failed page
     * until [retryLoadMore]. Asked during a quiet reload or a pull, it runs once that lands.
     */
    fun loadMore() {
        val state = _uiState.value
        val cursor = state.nextCursor
        val query = state.shownQuery
        if (!state.hasMore || cursor == null || query == null) return
        if (state.isLoadingMore || state.isLoading || state.loadMoreFailed) return
        if (loadJob?.isActive == true) {
            loadMoreAfterReload = true
            return
        }

        val stamp = generation
        _uiState.update { it.copy(isLoadingMore = true) }
        loadMoreJob = viewModelScope.launch {
            val result = readPage(query, cursor)
            if (stamp != generation) return@launch
            when (result) {
                is Result.Success -> _uiState.update {
                    // The grid keys cards by id: a page boundary that shifts under the cursor
                    // would otherwise repeat an agent and crash the grid.
                    val appended = (it.agents + result.data.agents.map(::toCard)).distinctBy { card -> card.id }
                    it.copy(
                        agents = appended,
                        isLoadingMore = false,
                        hasMore = result.data.hasMore,
                        nextCursor = result.data.nextCursor,
                    )
                }
                is Result.Error -> _uiState.update { it.copy(isLoadingMore = false, loadMoreFailed = true) }
                is Result.Loading -> Unit
            }
        }
    }

    /** The footer's Retry after a failed page. */
    fun retryLoadMore() {
        _uiState.update { it.copy(loadMoreFailed = false) }
        loadMore()
    }

    /** Clears an error the snackbar has shown. */
    fun dismissError() {
        _uiState.update { it.copy(loadError = null) }
    }

    private fun currentQuery(): AgentListQuery {
        val state = _uiState.value
        return AgentListQuery(chip = state.selectedChip ?: AgentChip.All, search = state.searchQuery.trim())
    }

    private fun startLoad(mode: LoadMode) {
        loadJob?.cancel()
        loadMoreJob?.cancel()
        // An ask for the next page of the list this read replaces is not one for the new list.
        if (mode == LoadMode.REPLACE) loadMoreAfterReload = false
        val stamp = ++generation
        val query = currentQuery()
        requestedQuery = query
        _uiState.update {
            it.copy(
                isLoading = mode == LoadMode.REPLACE || (it.isLoading && mode == LoadMode.REFRESH),
                isRefreshing = mode == LoadMode.REFRESH,
                isLoadingMore = false,
                loadMoreFailed = false,
                // A pull on the error page keeps the error until it lands, so the list area knows
                // what the pull is over (AgentListState): placeholders there, the answer elsewhere.
                loadError = if (mode == LoadMode.QUIET || (mode == LoadMode.REFRESH && it.agents.isEmpty())) {
                    it.loadError
                } else {
                    null
                },
            )
        }
        // A quiet reload walks the cursor until it covers what was loaded; anything else reads one page.
        val target = if (mode == LoadMode.QUIET) _uiState.value.agents.size else 0
        loadJob = viewModelScope.launch {
            val result = readPages(query, target)
            if (stamp != generation) return@launch
            when (result) {
                is Result.Success -> _uiState.update {
                    it.copy(
                        agents = result.data.agents.map(::toCard).distinctBy { card -> card.id },
                        isLoading = false,
                        isRefreshing = false,
                        loadError = null,
                        shownQuery = query,
                        hasMore = result.data.hasMore,
                        nextCursor = result.data.nextCursor,
                    )
                }
                is Result.Error -> _uiState.update { state ->
                    val error = AgentListError(result.serverMessage())
                    when {
                        // Nobody asked for this read; what is on screen stays, error and all.
                        mode == LoadMode.QUIET -> state
                        // The list on screen still answers this query: keep it, and say it failed.
                        state.shownQuery == query && state.agents.isNotEmpty() -> state.copy(
                            isLoading = false,
                            isRefreshing = false,
                            loadError = error,
                        )
                        // What is on screen answers another chip or search; it can't stand in.
                        else -> state.copy(
                            agents = emptyList(),
                            isLoading = false,
                            isRefreshing = false,
                            loadError = error,
                            shownQuery = query,
                            hasMore = false,
                            nextCursor = null,
                        )
                    }
                }
                is Result.Loading -> Unit
            }
            if (loadMoreAfterReload) {
                loadMoreAfterReload = false
                // This job is still the active one; it has landed, so it no longer holds paging back.
                loadJob = null
                loadMore()
            }
        }
    }

    /** Reads the first page, then on until [target] agents are in or there are no more. */
    private suspend fun readPages(query: AgentListQuery, target: Int): Result<PaginatedAgents> {
        val agents = mutableListOf<Agent>()
        var cursor: String? = null
        while (true) {
            when (val page = readPage(query, cursor)) {
                is Result.Success -> {
                    agents += page.data.agents
                    val next = page.data.nextCursor
                    if (!page.data.hasMore || next == null || agents.size >= target) {
                        return Result.Success(page.data.copy(agents = agents.toList()))
                    }
                    cursor = next
                }
                else -> return page
            }
        }
    }

    private suspend fun readPage(query: AgentListQuery, cursor: String?): Result<PaginatedAgents> {
        val filter = AgentListFilter.of(chip = query.chip, searchQuery = query.search)
        return agentRepository.getAgentsPaginated(
            limit = PAGE_SIZE,
            cursor = cursor,
            search = filter.search,
            category = filter.category,
            promoted = filter.promoted,
            requiredPermission = filter.requiredPermission,
        )
    }

    private fun toCard(agent: Agent): AgentCardDisplayData =
        agent.toCardDisplayData(serverDataStore.getBaseUrl())

    /** Loads the categories into state; a failure leaves them as they were (they are non-critical). */
    private suspend fun fetchCategories() {
        when (val result = agentRepository.getAgentCategories()) {
            is Result.Success -> _uiState.update { it.copy(categories = result.data.distinctBy { c -> c.value }) }
            is Result.Error, is Result.Loading -> Unit
        }
    }

    /** A selected chip the row no longer offers (Editable after losing CREATE, a removed category) falls back to All. */
    private fun fallBackIfChipGone() {
        val state = _uiState.value
        val selected = state.selectedChip ?: return
        if (selected !in state.chips) onChipSelected(AgentChip.All)
    }
}
