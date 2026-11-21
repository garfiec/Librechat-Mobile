package com.garfiec.librechat.feature.conversations.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.common.datetime.DateGroup
import com.garfiec.librechat.core.model.ConversationTag
import com.garfiec.librechat.feature.conversations.components.ConversationDisplayData

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
