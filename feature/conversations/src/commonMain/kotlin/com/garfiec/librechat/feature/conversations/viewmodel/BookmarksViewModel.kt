package com.garfiec.librechat.feature.conversations.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.BookmarksGate
import com.garfiec.librechat.core.data.repository.TagRepository
import com.garfiec.librechat.core.data.repository.bookmarks
import com.garfiec.librechat.core.model.ConversationTag
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The bookmark editor sheet: a new bookmark when [editing] is null. */
@Immutable
data class BookmarkEditorState(
    val editing: ConversationTag? = null,
    val saving: Boolean = false,
    /** The last save failed ([BookmarksFailure.CREATE] or [BookmarksFailure.SAVE]): the sheet says so itself. */
    val error: BookmarksFailure? = null,
)

@Immutable
data class BookmarksUiState(
    /** Every bookmark, Saved excluded, in the account's order. */
    val bookmarks: List<ConversationTag> = emptyList(),
    /** The first refresh hasn't settled yet. */
    val isLoading: Boolean = true,
    /** A pull-to-refresh is running. */
    val isRefreshing: Boolean = false,
    /** The last refresh failed. */
    val loadFailed: Boolean = false,
    /**
     * Local, case-insensitive, over names and descriptions. Session-only: it lives as long as this
     * ViewModel.
     */
    val searchQuery: String = "",
    val editor: BookmarkEditorState? = null,
    /** BOOKMARKS USE and `interface.bookmarks`; the screen closes itself once this turns false. */
    val enabled: Boolean = true,
) {
    val isSearching: Boolean get() = searchQuery.isNotBlank()

    /** What the grid shows: [bookmarks] matching [searchQuery]. */
    val shown: List<ConversationTag> = searchQuery.trim().let { query ->
        if (query.isEmpty()) {
            bookmarks
        } else {
            bookmarks.filter { tag ->
                tag.tag.orEmpty().contains(query, ignoreCase = true) ||
                    tag.description.orEmpty().contains(query, ignoreCase = true)
            }
        }
    }

    /** Counted on every bookmark, not the matches, so the field doesn't vanish as the user types. */
    val showsSearch: Boolean get() = bookmarks.size >= SEARCH_FROM || isSearching

    /** Reordering (drag, and the hint that offers it) needs two cards and the whole list on screen. */
    val canReorder: Boolean get() = bookmarks.size > 1 && !isSearching

    val content: BookmarksContent
        get() = when {
            shown.isNotEmpty() -> BookmarksContent.CARDS
            isSearching && bookmarks.isNotEmpty() -> BookmarksContent.NO_RESULTS
            isLoading -> BookmarksContent.PLACEHOLDERS
            loadFailed -> BookmarksContent.ERROR
            else -> BookmarksContent.EMPTY
        }

    companion object {
        /** The search field shows once there are this many bookmarks. */
        const val SEARCH_FROM = 8
    }
}

/** What the body of the Bookmarks index shows. */
enum class BookmarksContent { PLACEHOLDERS, ERROR, EMPTY, NO_RESULTS, CARDS }

enum class BookmarksFailure { LOAD, CREATE, SAVE, DELETE, MOVE }

sealed interface BookmarksEvent {
    data class Failed(val failure: BookmarksFailure) : BookmarksEvent
}

/**
 * The Bookmarks index: every bookmark (conversation tag) of the account, from the tag cache, which
 * every write keeps current. A refresh re-reads the server's list into the cache.
 */
class BookmarksViewModel(
    private val tagRepository: TagRepository,
    private val bookmarksGate: BookmarksGate,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookmarksUiState())
    val uiState: StateFlow<BookmarksUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<BookmarksEvent>(extraBufferCapacity = EVENT_BUFFER)
    val events: SharedFlow<BookmarksEvent> = _events.asSharedFlow()

    /** The order of moves not yet confirmed or refused, shown over the cache until they all settle. */
    private val pendingOrder = MutableStateFlow<List<String>?>(null)
    private var movesInFlight = 0

    private var refreshJob: Job? = null

    /** The gate has answered once. Before that nothing loads: a turned-off account would get a 403. */
    private var gateSettled = false

    init {
        viewModelScope.launch {
            combine(tagRepository.observeTags(), pendingOrder) { tags, pending ->
                tags.bookmarks().inOrder(pending)
            }.collect { list -> _uiState.update { it.copy(bookmarks = list) } }
        }
        viewModelScope.launch {
            bookmarksGate.enabled.collect { enabled ->
                _uiState.update { it.copy(enabled = enabled) }
                gateSettled = true
                if (enabled) startRefresh(pull = false)
            }
        }
        // A bookmark or a chat's bookmarks changed here or on another screen.
        viewModelScope.launch {
            tagRepository.revision.drop(1).collect { startRefresh(pull = false) }
        }
    }

    /** Pull-to-refresh. */
    fun refresh() = startRefresh(pull = true)

    /** Back on the index, e.g. from the web or a chat. Skips while a refresh is already running. */
    fun refreshOnReturn() = startRefresh(pull = false)

    /** The error state's Retry: placeholders again until it settles. */
    fun retry() {
        if (refreshJob?.isActive == true) return
        _uiState.update { it.copy(isLoading = true, loadFailed = false) }
        startRefresh(pull = false)
    }

    private fun startRefresh(pull: Boolean) {
        if (!gateSettled || !_uiState.value.enabled || refreshJob?.isActive == true) return
        if (pull) _uiState.update { it.copy(isRefreshing = true) }
        refreshJob = viewModelScope.launch {
            val failed = tagRepository.refreshTags() is Result.Error
            _uiState.update { it.copy(isLoading = false, isRefreshing = false, loadFailed = failed) }
            // With cards on screen they stay, and the failure is a snackbar; without, the error state.
            if (failed && _uiState.value.bookmarks.isNotEmpty()) _events.emit(BookmarksEvent.Failed(BookmarksFailure.LOAD))
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun openCreate() {
        _uiState.update { it.copy(editor = BookmarkEditorState()) }
    }

    fun openEdit(name: String) {
        val tag = _uiState.value.bookmarks.firstOrNull { it.tag == name } ?: return
        _uiState.update { it.copy(editor = BookmarkEditorState(editing = tag)) }
    }

    fun closeEditor() {
        if (_uiState.value.editor?.saving == true) return
        _uiState.update { it.copy(editor = null) }
    }

    /**
     * Saves the editor's [name] and [description] (both trimmed). An edit sends only what changed,
     * and an emptied description as `""`, which clears it. The sheet closes once the server took it.
     */
    fun save(name: String, description: String) {
        val editor = _uiState.value.editor ?: return
        if (editor.saving) return
        _uiState.update { it.copy(editor = editor.copy(saving = true, error = null)) }
        viewModelScope.launch {
            val editing = editor.editing
            val result = if (editing == null) {
                tagRepository.createTag(name, description.ifBlank { null })
            } else {
                val oldName = editing.tag.orEmpty()
                // Against the trimmed name, as the sheet's own dirty check: a web name "Work " shows
                // as "Work", and sending that back would rename it.
                val newName = name.takeIf { it != oldName.trim() }
                val newDescription = description.takeIf { it != editing.description.orEmpty().trim() }
                if (newName == null && newDescription == null) {
                    Result.Success(editing)
                } else {
                    tagRepository.updateTag(oldName, newName = newName, description = newDescription)
                }
            }
            when (result) {
                is Result.Success -> _uiState.update { it.copy(editor = null) }
                // In the sheet, which stays up with what was typed: a snackbar would sit behind it.
                is Result.Error -> _uiState.update { state ->
                    val failure = if (editing == null) BookmarksFailure.CREATE else BookmarksFailure.SAVE
                    state.copy(editor = state.editor?.copy(saving = false, error = failure))
                }
                Result.Loading -> Unit
            }
        }
    }

    fun delete(name: String) {
        viewModelScope.launch {
            if (tagRepository.deleteTag(name) is Result.Error) {
                _events.emit(BookmarksEvent.Failed(BookmarksFailure.DELETE))
            }
        }
    }

    /** A drag dropped the cards in [orderedNames]. */
    fun onDrop(orderedNames: List<String>) {
        val (name, toIndex) = singleMove(names(), orderedNames) ?: return
        move(name, toIndex)
    }

    /** Move up (-1) / Move down (+1), from the accessibility actions. */
    fun moveBy(name: String, delta: Int) {
        val from = names().indexOf(name)
        if (from < 0) return
        val to = from + delta
        if (to in names().indices) move(name, to)
    }

    private fun move(name: String, toIndex: Int) {
        val current = names()
        val from = current.indexOf(name)
        if (from < 0 || from == toIndex) return
        val intended = current.toMutableList().apply { add(toIndex, removeAt(from)) }
        pendingOrder.value = intended
        // What the user did is "put it below this card": the index is only good for the order on
        // screen, and an earlier move still saving may be refused and put the cache back.
        val after = intended.getOrNull(toIndex - 1)
        movesInFlight++
        viewModelScope.launch {
            // The repository re-reads the server's order, queues the move behind any still out and
            // runs it past this screen's end: leaving the index right after a drop keeps the move.
            val result = tagRepository.reorderAfter(name, after)
            movesInFlight--
            if (movesInFlight == 0) pendingOrder.value = null
            if (result is Result.Error) _events.emit(BookmarksEvent.Failed(BookmarksFailure.MOVE))
        }
    }

    private fun names(): List<String> = _uiState.value.bookmarks.mapNotNull { it.tag }

    private companion object {
        const val EVENT_BUFFER = 8
    }
}

/** [this] in [order]'s order where it names them; the rest after, as they were. */
private fun List<ConversationTag>.inOrder(order: List<String>?): List<ConversationTag> {
    if (order == null) return this
    val rank = order.withIndex().associate { it.value to it.index }
    return sortedBy { rank[it.tag] ?: Int.MAX_VALUE }
}

/**
 * The one item a drag moved from [old] to get [new], and where it landed; null when [new] isn't
 * [old] with a single item moved.
 */
internal fun singleMove(old: List<String>, new: List<String>): Pair<String, Int>? {
    if (old.size != new.size || old == new || old.toSet() != new.toSet()) return null
    val first = old.indices.first { old[it] != new[it] }
    val last = old.indices.last { old[it] != new[it] }
    val candidates = listOf(new[first] to first, old[first] to last)
    return candidates.firstOrNull { (name, to) ->
        old.toMutableList().apply { add(to, removeAt(indexOf(name))) } == new
    }
}
