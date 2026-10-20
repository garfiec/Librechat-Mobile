package com.garfiec.librechat.feature.conversations.viewmodel

import com.garfiec.librechat.core.common.result.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One page of a cursor-paged list; a null [nextCursor] means it was the last. */
internal data class CursorPage<T>(val items: List<T>, val nextCursor: String?)

internal data class CursorPagerState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val nextCursor: String? = null,
)

/**
 * A network-direct list loaded a page at a time, for the Projects index and a project's chats. The
 * rules it keeps for both:
 * - **First pages are latest wins.** A newer first page cancels an older one, and a page still in
 *   flight for an older list (a next page, a sort, a search) is dropped when it lands.
 * - **A next page waits for a first page.** Its cursor belongs to the list being replaced, so it
 *   runs once the new first page lands; the scroll position that asked won't ask again. A load or a
 *   pull starts over instead, and drops it.
 * - **A quiet reload keeps the reader's place.** It refetches as many rows as are loaded, following
 *   cursors past the server's per-request cap, swaps them in at once, and shows no spinner of its
 *   own while keeping one it took over.
 *
 * [fetch] gets a null limit for a normal page, so the server's default page size applies.
 */
internal class CursorPager<T>(
    private val scope: CoroutineScope,
    private val idOf: (T) -> String,
    private val onError: (String?) -> Unit,
    private val fetch: suspend (cursor: String?, limit: Int?) -> Result<CursorPage<T>>,
) {
    private val _items = MutableStateFlow<List<T>>(emptyList())
    val items: StateFlow<List<T>> = _items.asStateFlow()

    private val _state = MutableStateFlow(CursorPagerState())
    val state: StateFlow<CursorPagerState> = _state.asStateFlow()

    private var firstPageJob: Job? = null
    private var generation = 0
    private var loadMoreDeferred = false

    val isLoadingFirstPage: Boolean get() = firstPageJob?.isActive == true

    /** A first page with the loading spinner: on open, and for a new sort or search. */
    fun load() {
        loadMoreDeferred = false
        loadFirstPage(Kind.LOAD)
    }

    /** Pull to refresh; ignored while one is already running. */
    fun refresh() {
        if (_state.value.isRefreshing) return
        loadMoreDeferred = false
        loadFirstPage(Kind.REFRESH)
    }

    /** A reload nobody asked for (a change elsewhere, a return to the screen): no spinner. */
    fun reloadQuietly() = loadFirstPage(Kind.QUIET)

    fun loadMore() {
        val cursor = _state.value.nextCursor ?: return
        // Guard against BOTH flags: a first page running concurrently with this append would
        // resolve a stale cursor against a just-replaced list and could duplicate ids, which crashes
        // the keyed list.
        if (_state.value.isLoading || _state.value.isRefreshing) return
        if (isLoadingFirstPage) {
            loadMoreDeferred = true
            return
        }
        appendPage(cursor)
    }

    private fun appendPage(cursor: String) {
        val gen = generation
        scope.launch {
            _state.update { it.copy(isLoading = true) }
            val result = fetch(cursor, null)
            if (gen != generation) return@launch
            when (result) {
                is Result.Success -> {
                    // distinctBy: defense-in-depth against a page boundary that reorders under the
                    // sort handing back an id already held; duplicate keys crash the list.
                    _items.value = (_items.value + result.data.items).distinctBy(idOf)
                    _state.update { it.copy(nextCursor = result.data.nextCursor) }
                }
                is Result.Error -> onError(result.message)
                is Result.Loading -> Unit
            }
            _state.update { it.copy(isLoading = false) }
        }
    }

    private enum class Kind { LOAD, REFRESH, QUIET }

    private fun loadFirstPage(kind: Kind) {
        firstPageJob?.cancel()
        val gen = ++generation
        val keep = _items.value.size
        firstPageJob = scope.launch {
            // Sets both flags, since this may have cancelled a load that can no longer reset its
            // own; a quiet reload keeps a spinner it took over.
            _state.update {
                it.copy(
                    isLoading = if (kind == Kind.QUIET) it.isLoading else kind == Kind.LOAD,
                    isRefreshing = kind == Kind.REFRESH,
                )
            }
            val page = if (kind == Kind.QUIET && keep > PAGE_SIZE) fetchRows(keep) else fetch(null, null)
            if (gen != generation) return@launch
            when (page) {
                is Result.Success -> {
                    _items.value = page.data.items
                    _state.update { it.copy(nextCursor = page.data.nextCursor) }
                }
                is Result.Error -> onError(page.message)
                is Result.Loading -> Unit
            }
            _state.update { it.copy(isLoading = false, isRefreshing = false) }
            // Still inside this job, so append directly: loadMore would defer it again.
            if (loadMoreDeferred) {
                loadMoreDeferred = false
                _state.value.nextCursor?.let(::appendPage)
            }
        }
    }

    /** The first [count] rows, in as many requests as the server's cap needs, as one page. */
    private suspend fun fetchRows(count: Int): Result<CursorPage<T>> {
        val rows = mutableListOf<T>()
        var cursor: String? = null
        while (true) {
            val limit = (count - rows.size).coerceAtMost(MAX_PAGE_SIZE)
            when (val result = fetch(cursor, limit)) {
                is Result.Success -> {
                    rows += result.data.items
                    cursor = result.data.nextCursor
                    if (cursor == null || rows.size >= count) {
                        return Result.Success(CursorPage(rows.distinctBy(idOf), cursor))
                    }
                }
                else -> return result
            }
        }
    }

    private companion object {
        // The server's default page size and its per-request cap, for both lists.
        const val PAGE_SIZE = 25
        const val MAX_PAGE_SIZE = 100
    }
}
