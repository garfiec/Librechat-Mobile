package com.garfiec.librechat.feature.settings.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.SearchCapabilityRepository
import com.garfiec.librechat.core.data.repository.ShareRepository
import com.garfiec.librechat.core.data.share.ShareLinkController
import com.garfiec.librechat.core.data.share.ShareLinkControllerFactory
import com.garfiec.librechat.core.model.SharedLink
import com.garfiec.librechat.core.model.response.SharedLinksResponse
import com.garfiec.librechat.core.model.share.MAX_SHARED_LINK_SEARCH_LENGTH
import com.garfiec.librechat.core.model.share.SharedLinksSort
import com.garfiec.librechat.feature.settings.model.SharedLinkDisplayData
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class SharedLinksUiState(
    val links: List<SharedLinkDisplayData> = emptyList(),
    /** A first page is loading for a new list (open, sort, search, Retry). Starts true: the page opens loading. */
    val isLoading: Boolean = true,
    /** Pull to refresh is running. */
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasNextPage: Boolean = false,
    /** The last first page failed. With no rows the page shows the error state and Retry. */
    val loadFailed: Boolean = false,
    /** The last next page failed; the list ends in a retry row instead of loading on scroll. */
    val loadMoreFailed: Boolean = false,
    /** Lives only as long as this ViewModel: there is no saved sort preference yet. */
    val sort: SharedLinksSort = SharedLinksSort.NEWEST,
    /** The server's search is on, so the page offers a search box. */
    val searchEnabled: Boolean = false,
    val searchQuery: String = "",
    /** The trimmed search the shown list answers. Trails [searchQuery] until its results land. */
    val shownQuery: String = "",
)

/**
 * Whether the list should ask for its next page, given the last visible item and the item count.
 * Read from the CURRENT state each time: a scroll collector that captured the first composition's
 * values never loaded past the first page.
 */
internal fun SharedLinksUiState.shouldLoadMore(lastVisibleIndex: Int, totalItems: Int): Boolean =
    hasNextPage &&
        links.isNotEmpty() &&
        !isLoading &&
        !isRefreshing &&
        !isLoadingMore &&
        !loadMoreFailed &&
        lastVisibleIndex >= totalItems - LOAD_MORE_THRESHOLD

private const val LOAD_MORE_THRESHOLD = 3

/** One-off notices; the screen words them. */
enum class SharedLinksMessage { REFRESH_FAILED, DELETE_FAILED }

/**
 * Backs Settings → Data → Shared chats: the user's shared links, a page at a time, newest first by
 * default, searchable when the server's search is on. A card opens the share sheet ([shareLink])
 * on that link; Update and the files choice live there, not here.
 *
 * The list follows changes made anywhere in the app ([ShareRepository.revision]) and is reloaded
 * quietly on return to the page, keeping as many rows as were loaded.
 */
class SharedLinksViewModel(
    private val shareRepository: ShareRepository,
    shareLinkControllerFactory: ShareLinkControllerFactory,
    searchCapabilityRepository: SearchCapabilityRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SharedLinksUiState())
    val uiState: StateFlow<SharedLinksUiState> = _uiState.asStateFlow()

    private val _messages = Channel<SharedLinksMessage>(Channel.BUFFERED)
    val messages: Flow<SharedLinksMessage> = _messages.receiveAsFlow()

    /** The share sheet a card opens. */
    val shareLink: ShareLinkController = shareLinkControllerFactory.create(viewModelScope)

    /** Bumped by every first page; a page that lands under an older value belongs to a replaced list. */
    private var generation = 0L
    private var firstPageJob: Job? = null
    private var appendJob: Job? = null
    private var searchJob: Job? = null
    private var nextCursor: String? = null

    /** A next page asked for while a first page ran; it runs once that page lands. */
    private var loadMoreDeferred = false

    /** The trimmed search the current first page asks for; set when it starts, not when it lands. */
    private var requestedQuery = ""

    /**
     * The rows on screen answer an older sort or search: a LOAD for the new one started and no first
     * page has landed since. A refresh or quiet reload that takes over and fails must not leave them.
     */
    private var rowsStale = false

    init {
        load(Kind.LOAD)
        viewModelScope.launch {
            // A link made, updated or deleted here or anywhere else in the app.
            shareRepository.revision.drop(1).collect { reloadQuietly() }
        }
        viewModelScope.launch {
            searchCapabilityRepository.serverSearchEnabled
                .map { it == true }
                .distinctUntilChanged()
                .collect(::onSearchAvailability)
        }
    }

    /** Pull to refresh; ignored while one is already running. */
    fun refresh() {
        if (_uiState.value.isRefreshing) return
        load(Kind.REFRESH)
    }

    /** The error state's Retry. */
    fun retry() = load(Kind.LOAD)

    /** Back on the page, e.g. from a chat opened from a card. Skips while a first page is loading. */
    fun refreshOnReturn() {
        if (firstPageJob?.isActive != true) reloadQuietly()
    }

    fun loadMore() {
        val cursor = nextCursor ?: return
        if (_uiState.value.isLoadingMore) return
        if (firstPageJob?.isActive == true) {
            loadMoreDeferred = true
            return
        }
        appendPage(cursor)
    }

    fun setSort(sort: SharedLinksSort) {
        if (sort == _uiState.value.sort) return
        _uiState.update { it.copy(sort = sort) }
        load(Kind.LOAD)
    }

    fun onSearchQueryChange(query: String) {
        val capped = query.take(MAX_SHARED_LINK_SEARCH_LENGTH)
        if (capped == _uiState.value.searchQuery) return
        _uiState.update { it.copy(searchQuery = capped) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            if (capped.trim() != requestedQuery) load(Kind.LOAD)
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _uiState.update { it.copy(searchQuery = "") }
        if (requestedQuery.isNotEmpty()) load(Kind.LOAD)
    }

    /**
     * Opens the share sheet on [link]. The sheet looks up the conversation's newest link; when that
     * isn't this card's link (an older or targeted one made on the web) it opens reduced.
     */
    fun openLink(link: SharedLinkDisplayData) {
        shareLink.open(link.conversationId, target = null, focusShareId = link.shareId)
    }

    fun closeShareSheet() = shareLink.close()

    /** Revokes a link. One already gone counts as deleted. */
    fun delete(shareId: String) {
        viewModelScope.launch {
            when (shareRepository.deleteShareLink(shareId)) {
                is Result.Success -> _uiState.update { state ->
                    state.copy(links = state.links.filter { it.shareId != shareId })
                }
                is Result.Error -> _messages.send(SharedLinksMessage.DELETE_FAILED)
                is Result.Loading -> Unit
            }
        }
    }

    private fun onSearchAvailability(enabled: Boolean) {
        _uiState.update { it.copy(searchEnabled = enabled) }
        // A search box that goes away must not leave the list filtered by it.
        if (!enabled && _uiState.value.searchQuery.isNotEmpty()) clearSearch()
    }

    private fun reloadQuietly() = load(Kind.QUIET)

    private enum class Kind { LOAD, REFRESH, QUIET }

    private fun load(kind: Kind) {
        if (kind != Kind.QUIET) searchJob?.cancel()
        firstPageJob?.cancel()
        appendJob?.cancel()
        if (kind != Kind.QUIET) loadMoreDeferred = false
        val gen = ++generation
        val keep = _uiState.value.links.size
        val sort = _uiState.value.sort
        val query = if (kind == Kind.QUIET) requestedQuery else _uiState.value.searchQuery.trim()
        requestedQuery = query
        if (kind == Kind.LOAD) rowsStale = true
        _uiState.update {
            it.copy(
                // A quiet reload keeps a spinner it took over, and shows none of its own.
                isLoading = if (kind == Kind.QUIET) it.isLoading else kind == Kind.LOAD,
                isRefreshing = kind == Kind.REFRESH,
                isLoadingMore = false,
            )
        }
        firstPageJob = viewModelScope.launch {
            val result = if (kind == Kind.QUIET && keep > PAGE_SIZE) {
                fetchRows(keep, sort, query)
            } else {
                shareRepository.getSharedLinksPaginated(cursor = null, sort = sort, search = query)
            }
            if (gen != generation) return@launch
            when (result) {
                is Result.Success -> {
                    nextCursor = result.data.nextCursorOrNull()
                    rowsStale = false
                    _uiState.update {
                        it.copy(
                            links = result.data.toDisplayRows(),
                            hasNextPage = nextCursor != null,
                            shownQuery = query,
                            loadFailed = false,
                            loadMoreFailed = false,
                        )
                    }
                }
                is Result.Error -> onFirstPageFailed(kind)
                is Result.Loading -> Unit
            }
            _uiState.update { it.copy(isLoading = false, isRefreshing = false) }
            if (loadMoreDeferred) {
                loadMoreDeferred = false
                nextCursor?.let(::appendPage)
            }
        }
    }

    private suspend fun onFirstPageFailed(kind: Kind) {
        when {
            // The rows shown answer another sort or search: show the error state, not them. Also when a
            // refresh or quiet reload took over from that LOAD and failed in its place.
            kind == Kind.LOAD || rowsStale -> {
                nextCursor = null
                _uiState.update { it.copy(links = emptyList(), hasNextPage = false, loadFailed = true) }
            }
            kind == Kind.REFRESH -> {
                _uiState.update { it.copy(loadFailed = true) }
                if (_uiState.value.links.isNotEmpty()) _messages.send(SharedLinksMessage.REFRESH_FAILED)
            }
            // Nobody asked: the rows already shown stay, and so does the page's state.
            else -> Unit
        }
    }

    private fun appendPage(cursor: String) {
        val gen = generation
        val sort = _uiState.value.sort
        val query = requestedQuery
        _uiState.update { it.copy(isLoadingMore = true, loadMoreFailed = false) }
        appendJob = viewModelScope.launch {
            val result = shareRepository.getSharedLinksPaginated(cursor = cursor, sort = sort, search = query)
            if (gen != generation) return@launch
            when (result) {
                is Result.Success -> {
                    nextCursor = result.data.nextCursorOrNull()
                    _uiState.update {
                        it.copy(
                            // distinctBy: a link made between two pages shifts the next one; a
                            // repeated key crashes the list.
                            links = (it.links + result.data.toDisplayRows()).distinctBy(SharedLinkDisplayData::shareId),
                            hasNextPage = nextCursor != null,
                            isLoadingMore = false,
                        )
                    }
                }
                is Result.Error -> _uiState.update { it.copy(isLoadingMore = false, loadMoreFailed = true) }
                is Result.Loading -> Unit
            }
        }
    }

    /** The first [count] links, following cursors (the route serves fixed-size pages), as one page. */
    private suspend fun fetchRows(count: Int, sort: SharedLinksSort, query: String): Result<SharedLinksResponse> {
        val rows = mutableListOf<SharedLink>()
        var cursor: String? = null
        while (true) {
            when (val page = shareRepository.getSharedLinksPaginated(cursor = cursor, sort = sort, search = query)) {
                is Result.Success -> {
                    rows += page.data.links
                    cursor = page.data.nextCursorOrNull()
                    if (cursor == null || rows.size >= count) {
                        return Result.Success(SharedLinksResponse(rows, cursor, hasNextPage = cursor != null))
                    }
                }
                else -> return page
            }
        }
    }

    private fun SharedLinksResponse.nextCursorOrNull(): String? = nextCursor?.takeIf { hasNextPage != false }

    private fun SharedLinksResponse.toDisplayRows(): List<SharedLinkDisplayData> =
        links.mapNotNull { link ->
            // A link without an id can't be opened, copied or deleted.
            val shareId = link.shareId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            SharedLinkDisplayData(
                shareId = shareId,
                conversationId = link.conversationId,
                title = link.title?.takeIf { it.isNotBlank() },
                createdAt = link.createdAt,
                url = shareRepository.shareUrl(shareId),
            )
        }.distinctBy(SharedLinkDisplayData::shareId)

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L

        /** The route's page size (`ShareApi.getSharedLinks`). */
        const val PAGE_SIZE = 25
    }
}
