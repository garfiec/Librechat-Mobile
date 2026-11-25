package com.garfiec.librechat.feature.conversations.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.ChatProject

/** Project index orderings, as the `GET /api/projects` `sortBy`/`sortDirection` pair. */
enum class ProjectSort(val wire: String?, val direction: String?) {
    ACTIVITY("lastConversationAt", "desc"),
    CREATED("createdAt", "desc"),
    NAME("name", "asc"),

    /**
     * **Switchboard-only**: the order the user dragged the folders into in the drawer, kept on the
     * device. The server has no such sort, so this one sends no pair and loads every project at once.
     */
    CUSTOM(null, null),
}

@Immutable
data class ProjectsUiState(
    val projects: List<ChatProject> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    /** The last first page failed: with no projects held, the error state stands in for the list. */
    val loadFailed: Boolean = false,
    val nextCursor: String? = null,
    val hasMore: Boolean = true,
    /** Lives only as long as this ViewModel: there is no saved sort preference yet. */
    val sort: ProjectSort = ProjectSort.ACTIVITY,
    val searchQuery: String = "",
    /** The trimmed search the shown list answers. Trails [searchQuery] until its results land. */
    val shownQuery: String = "",
)
