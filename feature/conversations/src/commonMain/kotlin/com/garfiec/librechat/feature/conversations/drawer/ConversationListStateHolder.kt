package com.garfiec.librechat.feature.conversations.drawer

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.datetime.DateGroup
import com.garfiec.librechat.core.common.extensions.RelativeTimeReference
import com.garfiec.librechat.core.common.extensions.dayBoundaryReferences
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConversationRepository
import com.garfiec.librechat.core.data.search.TitleMatch
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.ConversationListFilters
import com.garfiec.librechat.feature.conversations.viewmodel.groupedByDateBucket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

class ConversationListStateHolder(
    private val conversationRepository: ConversationRepository,
    private val scope: CoroutineScope,
    // Injectable so tests can supply a finite flow: the production one never completes, and a
    // repeating delay makes `advanceUntilIdle()` advance virtual time forever.
    private val dayBoundaries: Flow<RelativeTimeReference> = dayBoundaryReferences(),
    /**
     * The server's conversation search, or null when it can't answer (search disabled, offline,
     * failed). It returns conversations whose title **or any message** matched, so only those whose
     * title matches ([TitleMatch]) join the local title hits; body matches are the Messages
     * section's job.
     */
    private val remoteTitleSearch: (suspend (String) -> List<Conversation>?)? = null,
) {

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 300L

        /** Shorter queries stay local: upstream doesn't rate-limit search, so the client does. */
        private const val MIN_REMOTE_QUERY_CHARS = 3
    }

    /** Server title hits for the current query. Cleared whenever the query changes. */
    private var remoteTitleHits: List<Conversation> = emptyList()

    private val _recentConversations = MutableStateFlow<List<Conversation>>(emptyList())
    val recentConversations: StateFlow<List<Conversation>> = _recentConversations.asStateFlow()

    private val _groupedConversations = MutableStateFlow<List<Pair<DateGroup, List<Conversation>>>>(emptyList())
    val groupedConversations: StateFlow<List<Pair<DateGroup, List<Conversation>>>> = _groupedConversations.asStateFlow()

    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private var nextCursor: String? = null

    private val _hasMore = MutableStateFlow(true)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private var searchObserverJob: Job? = null

    /**
     * The list filters (v0.8.9-rc1), or null when the list is unfiltered. A filtered list pages from
     * the server and never through Room, which can't answer "has files" or "shared"; Room's
     * emissions still reach its rows (a rename, a pin, a delete) through [overlayRoom].
     */
    private val _filters = MutableStateFlow<ConversationListFilters?>(null)
    val filters: StateFlow<ConversationListFilters?> = _filters.asStateFlow()

    /** The filtered rows paged in so far, newest first. Empty while unfiltered. */
    private var filteredRows: List<Conversation> = emptyList()
    private var filteredCursor: String? = null
    private var filterLoadJob: Job? = null

    /** The last filtered load failed, so an empty list is not "nothing matches". */
    private val _filterLoadFailed = MutableStateFlow(false)
    val filterLoadFailed: StateFlow<Boolean> = _filterLoadFailed.asStateFlow()

    /** The ids Room held on its last emission, so a row that leaves it leaves the filtered list too. */
    private var roomIds: Set<String> = emptySet()

    init {
        observeConversations()
        observeSearchQuery()
        observeDayBoundary()
        loadInitialConversations()
    }

    private fun observeConversations() {
        scope.launch {
            conversationRepository.observeConversations()
                .catch { e -> Logger.w(e) { "Failed to observe conversations" } }
                .collect { result ->
                    if (result is Result.Success) {
                        _recentConversations.value = result.data
                        overlayRoom(result.data)
                        // Regroup on every Room emission — including during an active search. Search
                        // is a client-side filter over live Room data, so a delete/rename/pin/favorite
                        // (which re-emits Room) must reflect in the visible results immediately, not
                        // wait for the next query keystroke (Punted Bug #25, drawer side).
                        regroup()
                    }
                }
        }
    }

    /**
     * Re-bucket at each local midnight. Date groups are clock-dependent, and a conversation crossing
     * midnight changes *section*, not just label — so unlike a row's "5m ago" this cannot be fixed
     * by reformatting at render time. Without this, a drawer left open overnight keeps yesterday's
     * sections until some unrelated Room emission happens to regroup.
     */
    private fun observeDayBoundary() {
        scope.launch {
            dayBoundaries.collect { regroup() }
        }
    }

    /** Rebuilds [_groupedConversations] from the current cache, honouring any active search. */
    private fun regroup() {
        val query = _searchQuery.value
        if (_filters.value != null) {
            // Pinned chats stay in a filtered list: the Pinned section is hidden while it shows.
            _groupedConversations.value = if (query.isBlank()) {
                filteredRows.groupedByDateBucket()
            } else {
                searchResults(filteredRows, query).groupedByDateBucket()
            }
            return
        }
        val conversations = _recentConversations.value
        _groupedConversations.value = if (query.isBlank()) {
            conversations.withoutPinned().groupedByDateBucket()
        } else {
            searchResults(conversations, query).groupedByDateBucket()
        }
    }

    /** The local title filter's hits, then any server hits that aren't cached. */
    private fun searchResults(conversations: List<Conversation>, query: String): List<Conversation> {
        val local = filterByQuery(conversations, query)
        if (remoteTitleHits.isEmpty()) return local
        val localIds = local.mapTo(HashSet()) { it.conversationId }
        return local + remoteTitleHits.filter { it.conversationId !in localIds && TitleMatch.matches(it.title, query) }
    }

    private fun loadInitialConversations() {
        scope.launch {
            loadPage(cursor = null)
        }
    }

    private suspend fun loadPage(cursor: String?): Boolean {
        _filters.value?.let { return loadFilteredPage(it, cursor) }
        val result = conversationRepository.loadNextPage(cursor = cursor)
        return if (result is Result.Success) {
            nextCursor = result.data
            _hasMore.value = result.data != null
            true
        } else {
            Logger.w((result as? Result.Error)?.exception) { "Failed to load conversations page" }
            false
        }
    }

    /**
     * One page of the filtered list; the first replaces what was paged in. A reply for filters the
     * user has since changed is dropped.
     */
    private suspend fun loadFilteredPage(filters: ConversationListFilters, cursor: String?): Boolean {
        val result = conversationRepository.getConversationsFiltered(filters, cursor = cursor)
        if (_filters.value != filters) return false
        return if (result is Result.Success) {
            val page = result.data.conversations
            filteredRows = if (cursor == null) {
                page
            } else {
                val held = filteredRows.mapNotNullTo(HashSet()) { it.conversationId }
                filteredRows + page.filterNot { it.conversationId in held }
            }
            filteredCursor = result.data.nextCursor
            _hasMore.value = result.data.nextCursor != null
            _filterLoadFailed.value = false
            regroup()
            true
        } else {
            Logger.w((result as? Result.Error)?.exception) { "Failed to load filtered conversations" }
            if (cursor == null) {
                filteredRows = emptyList()
                _hasMore.value = false
                _filterLoadFailed.value = true
                regroup()
            }
            false
        }
    }

    /**
     * Applies [filters] (null clears them). The filtered list starts empty and loads its first page;
     * clearing them returns the drawer to the cached list.
     */
    fun setFilters(filters: ConversationListFilters?) {
        val next = filters?.takeIf { it.isActive }
        if (next == _filters.value) return
        filterLoadJob?.cancel()
        _filters.value = next
        filteredRows = emptyList()
        filteredCursor = null
        _filterLoadFailed.value = false
        remoteTitleHits = emptyList()
        // Not until the first page says so: a load-more meanwhile would fetch that page twice.
        _hasMore.value = next == null
        _isLoadingMore.value = false
        regroup()
        if (next == null) {
            nextCursor = null
            filterLoadJob = scope.launch { loadPage(cursor = null) }
            return
        }
        _isRefreshing.value = true
        filterLoadJob = scope.launch {
            try {
                loadFilteredPage(next, cursor = null)
            } finally {
                if (_filters.value == next) _isRefreshing.value = false
            }
        }
    }

    /**
     * Keeps the filtered rows in step with the cache: a row Room holds takes Room's copy (renamed,
     * pinned, bookmarked), and one that left Room (deleted, archived) leaves the list.
     */
    private fun overlayRoom(rows: List<Conversation>) {
        val byId = rows.associateBy { it.conversationId }
        val previous = roomIds
        roomIds = byId.keys.filterNotNullTo(HashSet())
        if (_filters.value == null || filteredRows.isEmpty()) return
        filteredRows = filteredRows.mapNotNull { row ->
            val id = row.conversationId
            when {
                id != null && id in byId -> byId.getValue(id)
                id != null && id in previous -> null
                else -> row
            }
        }
    }

    fun loadMoreConversations() {
        if (_isLoadingMore.value || !_hasMore.value) return
        _isLoadingMore.value = true
        scope.launch {
            loadPage(cursor = if (_filters.value != null) filteredCursor else nextCursor)
            _isLoadingMore.value = false
        }
    }

    fun refreshConversations() {
        _isRefreshing.value = true
        scope.launch {
            nextCursor = null
            _hasMore.value = true
            coroutineScope {
                launch { loadPage(cursor = null) }
                launch { conversationRepository.syncFavoritesFromServer() }
            }
            _isRefreshing.value = false
        }
    }

    fun setActiveConversation(conversationId: String?) {
        _activeConversationId.value = conversationId
    }

    fun onSearchQueryChanged(query: String) {
        if (query.trim() != _searchQuery.value.trim()) remoteTitleHits = emptyList()
        _searchQuery.value = query
        if (query.isBlank()) {
            regroup()
        }
    }

    @OptIn(FlowPreview::class)
    private fun observeSearchQuery() {
        searchObserverJob?.cancel()
        searchObserverJob = scope.launch {
            _searchQuery
                .filter { it.isNotBlank() }
                .debounce(SEARCH_DEBOUNCE_MS)
                .collectLatest { query ->
                    val filters = _filters.value
                    _groupedConversations.value =
                        searchResults(if (filters != null) filteredRows else _recentConversations.value, query)
                            .groupedByDateBucket()
                    // A filtered list asks the server with the same filters, so its title hits stay narrowed.
                    val search = if (filters != null) {
                        { q: String -> filteredRemoteTitles(filters, q) }
                    } else {
                        remoteTitleSearch ?: return@collectLatest
                    }
                    if (query.trim().length < MIN_REMOTE_QUERY_CHARS) return@collectLatest
                    val remote = search(query.trim()) ?: return@collectLatest
                    if (_filters.value != filters) return@collectLatest
                    // A reply for a query the user has since changed (or that a reset blanked on an
                    // account switch) belongs to nothing on screen.
                    if (_searchQuery.value != query) return@collectLatest
                    remoteTitleHits = remote
                    regroup()
                }
        }
    }

    private suspend fun filteredRemoteTitles(filters: ConversationListFilters, query: String): List<Conversation>? =
        (conversationRepository.getConversationsFiltered(filters, search = query) as? Result.Success)?.data?.conversations

    /** Titles matched the way messages are: every word, the last one as a prefix. */
    private fun filterByQuery(conversations: List<Conversation>, query: String): List<Conversation> =
        conversations.filter { TitleMatch.matches(it.title, query) }

    fun reset() {
        searchObserverJob?.cancel()
        searchObserverJob = null
        remoteTitleHits = emptyList()
        filterLoadJob?.cancel()
        _filters.value = null
        filteredRows = emptyList()
        filteredCursor = null
        _filterLoadFailed.value = false
        roomIds = emptySet()
        _recentConversations.value = emptyList()
        _groupedConversations.value = emptyList()
        _activeConversationId.value = null
        _searchQuery.value = ""
        nextCursor = null
        _hasMore.value = true
        _isLoadingMore.value = false
        _isRefreshing.value = false
        observeSearchQuery()
    }

    /**
     * Drops pinned conversations so they aren't double-listed: the dedicated Pinned drawer section
     * is their canonical home when not searching. Search keeps them (that section is hidden then),
     * so this is only applied on the non-search grouping paths.
     */
    private fun List<Conversation>.withoutPinned(): List<Conversation> = filterNot { it.pinned == true }
}
