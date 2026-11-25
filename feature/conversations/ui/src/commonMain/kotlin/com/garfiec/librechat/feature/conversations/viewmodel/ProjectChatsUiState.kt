package com.garfiec.librechat.feature.conversations.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.common.datetime.DateGroup
import com.garfiec.librechat.core.model.ChatProject
import com.garfiec.librechat.core.model.config.ChatProjectLimits
import com.garfiec.librechat.feature.conversations.components.ConversationDisplayData

/** Sort field for the project chat list (v0.8.7), matching the convos endpoint. */
enum class ProjectChatsSort(val sortBy: String) {
    UPDATED("updatedAt"),
    CREATED("createdAt"),
}

@Immutable
data class ProjectChatsUiState(
    val groupedConversations: List<Pair<DateGroup, List<ConversationDisplayData>>> = emptyList(),
    val conversationCount: Int = 0,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    /** The last first page failed: with no chats held, the error state stands in for the list. */
    val loadFailed: Boolean = false,
    val nextCursor: String? = null,
    val hasMore: Boolean = true,
    val sort: ProjectChatsSort = ProjectChatsSort.UPDATED,
    /** Bookmarks and the favorite star: BOOKMARKS USE and `interface.bookmarks`. */
    val bookmarksEnabled: Boolean = true,
    /** Share shows on rows: the server has shared links on and the role may create them. */
    val sharedLinksEnabled: Boolean = false,
    /** The project itself, for the header and Edit. Null while loading, and always for Unassigned. */
    val project: ChatProject? = null,
    /** The server no longer has this project (deleted elsewhere): the page has nothing to show. */
    val projectDeleted: Boolean = false,
    /** The Edit sheet's description cap (`projects.maxDescriptionLength`). */
    val descriptionLimit: Int = ChatProjectLimits.DEFAULT_MAX_DESCRIPTION_LENGTH,
)
