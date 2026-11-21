package com.garfiec.librechat.feature.conversations.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.feature.conversations.ArchivedConversationDisplayData

@Immutable
data class ArchivedConversationsUiState(
    val conversations: List<ArchivedConversationDisplayData> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val isLoadingMore: Boolean = false,
    /** Only the server's cursor decides this: the cache can hold archived chats past the pages loaded. */
    val hasMore: Boolean = true,
    val loadMoreFailed: Boolean = false,
)
