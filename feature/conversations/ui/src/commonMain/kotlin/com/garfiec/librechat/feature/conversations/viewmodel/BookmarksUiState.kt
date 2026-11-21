package com.garfiec.librechat.feature.conversations.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.ConversationTag

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
