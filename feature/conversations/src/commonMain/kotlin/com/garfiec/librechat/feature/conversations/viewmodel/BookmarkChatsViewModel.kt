package com.garfiec.librechat.feature.conversations.viewmodel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.datetime.DateGroup
import com.garfiec.librechat.core.common.extensions.RelativeTimeReference
import com.garfiec.librechat.core.common.extensions.dayBoundaryReferences
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.bookmarks.BookmarkPickerController
import com.garfiec.librechat.core.data.bookmarks.BookmarkPickerState
import com.garfiec.librechat.core.data.repository.BookmarksGate
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.ConversationRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.data.repository.TagEvent
import com.garfiec.librechat.core.data.repository.TagRepository
import com.garfiec.librechat.core.data.repository.bookmarks
import com.garfiec.librechat.core.data.share.ShareLinkController
import com.garfiec.librechat.core.data.share.ShareLinkControllerFactory
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.ConversationTag
import com.garfiec.librechat.core.model.permissions.canCreateSharedLinks
import com.garfiec.librechat.feature.conversations.components.ConversationDisplayData
import com.garfiec.librechat.feature.conversations.components.message
import com.garfiec.librechat.feature.conversations.export.ConversationExporter
import com.garfiec.librechat.feature.conversations.export.ExportFormat
import com.garfiec.librechat.feature.conversations.resources.Res
import com.garfiec.librechat.feature.conversations.resources.bookmark_chats_load_error
import com.garfiec.librechat.feature.conversations.resources.bookmark_delete_failed
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the body of a bookmark's chats shows. */
enum class BookmarkChatsContent { LOADING, ERROR, EMPTY, CHATS }

@Immutable
data class BookmarkChatsUiState(
    /**
     * The bookmark's current name. The route's copy goes stale once it's renamed (here, or on
     * another screen of the app), so this one is what the page shows and asks the server for.
     */
    val name: String,
    /** The bookmark as the tag list has it: description and count. Null until the list has it. */
    val bookmark: ConversationTag? = null,
    /** Every bookmark's name (Saved excluded), for the Edit sheet's duplicate check. */
    val allNames: List<String> = emptyList(),
    val groupedConversations: List<Pair<DateGroup, List<ConversationDisplayData>>> = emptyList(),
    val conversationCount: Int = 0,
    /** No first page has answered yet, or one is loading with the spinner. */
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    /** The last first page failed with nothing loaded: the error state stands in for the list. */
    val loadFailed: Boolean = false,
    val hasMore: Boolean = false,
    /** BOOKMARKS USE and `interface.bookmarks`; the screen closes itself once this turns false. */
    val bookmarksEnabled: Boolean = true,
    /** Share shows on rows: the server has shared links on and the role may create them. */
    val sharedLinksEnabled: Boolean = false,
    /** The Edit sheet is up; [BookmarkEditorState.editing] is the bookmark as it was opened. */
    val editor: BookmarkEditorState? = null,
    /** The bookmark is gone (deleted here or elsewhere): the screen closes itself. */
    val deleted: Boolean = false,
) {
    val content: BookmarkChatsContent
        get() = when {
            conversationCount > 0 -> BookmarkChatsContent.CHATS
            isLoading -> BookmarkChatsContent.LOADING
            loadFailed -> BookmarkChatsContent.ERROR
            else -> BookmarkChatsContent.EMPTY
        }
}

private const val PAGE_SIZE = 25

/**
 * One bookmark's chats, paged from `GET /api/convos?tags=<name>` and held here, not filtered out of
 * Room: the list endpoint omits each chat's tags, so the cache can't tell which chats carry it.
 * Rows offer the same actions as a project's chats. Any bookmark change the server accepted reloads
 * the loaded rows quietly, so a chat taken off this bookmark drops out.
 *
 * [tag] is the name the screen was opened with; [BookmarkChatsUiState.name] follows renames.
 */
class BookmarkChatsViewModel(
    tag: String,
    private val conversationRepository: ConversationRepository,
    private val tagRepository: TagRepository,
    shareLinkControllerFactory: ShareLinkControllerFactory,
    private val conversationExporter: ConversationExporter,
    private val bookmarksGate: BookmarksGate,
    private val roleRepository: RoleRepository,
    private val configRepository: ConfigRepository,
    // Defaulted, as in ProjectChatsViewModel: the production flow never completes, which would hang
    // `advanceUntilIdle()` in tests.
    private val dayBoundaries: Flow<RelativeTimeReference> = dayBoundaryReferences(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookmarkChatsUiState(name = tag))
    val uiState: StateFlow<BookmarkChatsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ConversationListEvent>(extraBufferCapacity = EVENT_BUFFER)
    val events: SharedFlow<ConversationListEvent> = _events.asSharedFlow()

    /**
     * A first page has answered, so an empty list means empty rather than not yet loaded. A flow, not
     * a flag read beside the pager's state: that state can go loading and back between two reads.
     */
    private val firstPageAnswered = MutableStateFlow(false)

    private val pager = CursorPager(
        scope = viewModelScope,
        idOf = { it.conversationId.orEmpty() },
        onError = { onPageFailed() },
    ) { cursor, limit ->
        // The live name, not the route's: after a rename the old one matches no chat.
        val result = conversationRepository.getConversationsForTag(
            tag = _uiState.value.name,
            cursor = cursor,
            limit = limit ?: PAGE_SIZE,
        )
        firstPageAnswered.value = true
        when (result) {
            is Result.Success -> Result.Success(CursorPage(result.data.conversations, result.data.nextCursor))
            is Result.Error -> result
            is Result.Loading -> result
        }
    }

    private val actions = ConversationListActionsDelegate(
        scope = viewModelScope,
        events = _events,
        conversationRepository = conversationRepository,
        tagRepository = tagRepository,
        shareLinkControllerFactory = shareLinkControllerFactory,
        conversationExporter = conversationExporter,
        onMutated = { pager.reloadQuietly() },
    )

    /**
     * A row's "Bookmarks…" checklist. A tick or create that fails says so in the sheet; a load that
     * closes it, or a save that fails after it closed, surfaces as the screen's snackbar.
     */
    val bookmarkPicker = BookmarkPickerController(tagRepository, viewModelScope)

    /** The loaded chats, each with its cached tags (the list endpoint sends none), for the star and its sheet. */
    private val conversations: StateFlow<List<Conversation>> =
        pager.items.withCachedTags(tagRepository).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The gate has answered once. Before that nothing loads: a turned-off account would get a 403. */
    private var gateSettled = false
    private var checkJob: Job? = null

    init {
        observePaging()
        observeGrouped()
        observeBookmark()
        observeSharing()
        viewModelScope.launch {
            bookmarksGate.enabled.collect { enabled ->
                _uiState.update { it.copy(bookmarksEnabled = enabled) }
                if (!enabled) {
                    bookmarkPicker.close()
                } else if (!gateSettled) {
                    gateSettled = true
                    pager.load()
                }
            }
        }
        viewModelScope.launch {
            bookmarkPicker.failures.collect { _events.emit(ConversationListEvent.ShowErrorRes(it.message)) }
        }
        viewModelScope.launch {
            bookmarkPicker.state.collect(::dropRowTakenOffThisBookmark)
        }
        viewModelScope.launch {
            tagRepository.events.collect(::onTagEvent)
        }
        // A bookmark or a chat's bookmarks changed, here or on another screen of the app.
        viewModelScope.launch {
            tagRepository.revision.drop(1).collect { if (isLive()) pager.reloadQuietly() }
        }
    }

    private fun isLive(): Boolean = gateSettled && !_uiState.value.deleted && _uiState.value.bookmarksEnabled

    private fun onPageFailed() {
        if (_uiState.value.conversationCount == 0) {
            _uiState.update { it.copy(loadFailed = true) }
        } else {
            _events.tryEmit(ConversationListEvent.ShowErrorRes(Res.string.bookmark_chats_load_error))
        }
    }

    private fun onTagEvent(event: TagEvent) {
        val name = _uiState.value.name
        when (event) {
            is TagEvent.Renamed -> if (event.from == name) {
                _uiState.update { it.copy(name = event.to) }
                if (isLive()) pager.reloadQuietly()
            }
            is TagEvent.Deleted -> if (event.name == name) markDeleted()
        }
    }

    private fun markDeleted() {
        bookmarkPicker.close()
        _uiState.update { it.copy(deleted = true, editor = null) }
    }

    /**
     * The checklist answered for a row of this list without this bookmark (unticked, or the server
     * says the chat doesn't carry it): the row goes now, ahead of the reload that confirms it.
     */
    private fun dropRowTakenOffThisBookmark(picker: BookmarkPickerState) {
        val id = picker.conversationId ?: return
        val name = _uiState.value.name
        if (picker.loading || name in picker.pending || name in picker.attached) return
        pager.removeWhere { it.conversationId == id }
    }

    private fun observeBookmark() {
        viewModelScope.launch {
            combine(tagRepository.observeTags(), _uiState.map { it.name }.distinctUntilChanged()) { tags, name ->
                val bookmarks = tags.bookmarks()
                bookmarks.firstOrNull { it.tag == name } to bookmarks.mapNotNull { it.tag }
            }.collect { (tag, names) -> _uiState.update { it.copy(bookmark = tag, allNames = names) } }
        }
    }

    private fun observeGrouped() {
        viewModelScope.launch {
            // dayBoundaryReferences() re-groups at local midnight, as on a project's chats.
            combine(
                conversations,
                configRepository.endpointConfigs,
                configRepository.serverUrl,
                dayBoundaries,
            ) { convos, configs, serverUrl, reference ->
                groupConversationsByDate(convos, configs, serverUrl, reference) to convos.size
            }.collect { (grouped, count) ->
                _uiState.update {
                    it.copy(
                        groupedConversations = grouped,
                        conversationCount = count,
                        loadFailed = it.loadFailed && count == 0,
                    )
                }
            }
        }
    }

    private fun observePaging() {
        viewModelScope.launch {
            combine(pager.state, firstPageAnswered) { paging, answered -> paging to answered }.collect { (paging, answered) ->
                _uiState.update {
                    it.copy(
                        isLoading = paging.isLoading || !answered,
                        isRefreshing = paging.isRefreshing,
                        hasMore = paging.nextCursor != null,
                    )
                }
            }
        }
    }

    private fun observeSharing() {
        viewModelScope.launch {
            combine(roleRepository.userPermissions, configRepository.startupConfig) { role, config ->
                role.canCreateSharedLinks(config?.sharedLinksEnabled ?: false)
            }.collect { enabled -> _uiState.update { it.copy(sharedLinksEnabled = enabled) } }
        }
    }

    /**
     * Back on the page (and on first show): reloads the rows quietly, and checks the bookmark still
     * exists. A name missing from a fresh tag list means it was deleted on the web, or renamed there,
     * which the app can't tell apart (the cache keeps no server id), so either closes the page.
     */
    fun refreshOnReturn() {
        if (!isLive()) return
        if (!pager.isLoadingFirstPage) pager.reloadQuietly()
        checkStillExists()
    }

    /** Pull to refresh. */
    fun refresh() {
        if (!isLive() || _uiState.value.isRefreshing) return
        _uiState.update { it.copy(loadFailed = false) }
        pager.refresh()
        checkStillExists()
    }

    /** The error state's Retry. */
    fun retry() {
        if (!isLive()) return
        _uiState.update { it.copy(loadFailed = false) }
        pager.load()
    }

    fun loadMore() {
        if (isLive()) pager.loadMore()
    }

    private fun checkStillExists() {
        if (checkJob?.isActive == true) return
        val asked = _uiState.value.name
        checkJob = viewModelScope.launch {
            // Only a refresh that went through can say it's gone: an offline one leaves the old list.
            if (tagRepository.refreshTags() !is Result.Success) return@launch
            // Renamed here while the list was in flight: that list may predate the new name.
            if (_uiState.value.name != asked) return@launch
            val names = tagRepository.observeTags().first().mapNotNull { it.tag }
            if (asked !in names) markDeleted()
        }
    }

    fun openEdit() {
        val state = _uiState.value
        if (state.deleted || state.editor != null) return
        val current = state.bookmark ?: ConversationTag(tag = state.name)
        _uiState.update { it.copy(editor = BookmarkEditorState(editing = current)) }
    }

    fun closeEditor() {
        if (_uiState.value.editor?.saving == true) return
        _uiState.update { it.copy(editor = null) }
    }

    /**
     * Saves the Edit sheet's [newName] and [description] (both trimmed), sending only what changed;
     * an emptied description goes as `""`, which clears it. On a rename the page follows the new name.
     */
    fun save(newName: String, description: String) {
        val editor = _uiState.value.editor ?: return
        val editing = editor.editing ?: return
        if (editor.saving) return
        _uiState.update { it.copy(editor = editor.copy(saving = true, error = null)) }
        viewModelScope.launch {
            val oldName = editing.tag.orEmpty()
            // Against the trimmed name, as the sheet's own dirty check: a web name "Work " shows
            // as "Work", and sending that back would rename it.
            val rename = newName.takeIf { it != oldName.trim() }
            val newDescription = description.takeIf { it != editing.description.orEmpty().trim() }
            val result = if (rename == null && newDescription == null) {
                Result.Success(editing)
            } else {
                tagRepository.updateTag(oldName, newName = rename, description = newDescription)
            }
            when (result) {
                is Result.Success -> {
                    // The repository's rename event has already moved the page (and reloaded it); the
                    // stored name wins in case the server changed what was typed.
                    val stored = result.data.tag?.takeIf { it.isNotBlank() }
                    _uiState.update { state ->
                        state.copy(editor = null, name = if (rename != null && stored != null) stored else state.name)
                    }
                }
                // In the sheet, which stays up with what was typed: a snackbar would sit behind it.
                is Result.Error -> _uiState.update { state ->
                    state.copy(editor = state.editor?.copy(saving = false, error = BookmarksFailure.SAVE))
                }
                Result.Loading -> Unit
            }
        }
    }

    /** Deletes this bookmark; the page closes once the server took it. */
    fun delete() {
        val name = _uiState.value.name
        viewModelScope.launch {
            when (tagRepository.deleteTag(name)) {
                is Result.Success -> markDeleted()
                is Result.Error -> _events.emit(ConversationListEvent.ShowErrorRes(Res.string.bookmark_delete_failed))
                Result.Loading -> Unit
            }
        }
    }

    fun getConversation(conversationId: String): Conversation? =
        conversations.value.firstOrNull { it.conversationId == conversationId }

    fun toggleFavorite(conversation: Conversation) = actions.toggleFavorite(conversation)

    fun renameConversation(id: String, newTitle: String) = actions.renameConversation(id, newTitle)

    fun archiveConversation(id: String) = actions.archiveConversation(id)

    fun deleteConversation(id: String) = actions.deleteConversation(id)

    val shareLink: ShareLinkController get() = actions.shareLink

    fun shareConversation(conversationId: String) = actions.shareConversation(conversationId)

    fun closeShareSheet() = actions.closeShareSheet()

    fun duplicateConversation(conversationId: String, title: String?) =
        actions.duplicateConversation(conversationId, title)

    /** Opens the bookmark checklist on [conversationId]: its bookmarks are read from the server first. */
    fun openBookmarks(conversationId: String) {
        if (!_uiState.value.bookmarksEnabled) return
        bookmarkPicker.open(conversationId)
    }

    fun exportConversation(conversationId: String, title: String?, format: ExportFormat) =
        actions.exportConversation(conversationId, title, format)

    private companion object {
        const val EVENT_BUFFER = 8
    }
}
