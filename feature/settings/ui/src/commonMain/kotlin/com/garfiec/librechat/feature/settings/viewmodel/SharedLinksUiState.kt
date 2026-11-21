package com.garfiec.librechat.feature.settings.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.share.SharedLinksSort
import com.garfiec.librechat.feature.settings.model.SharedLinkDisplayData

@Immutable
data class SharedLinksUiState(
    val links: List<SharedLinkDisplayData> = emptyList(),
    /** A first page is loading for a new list. Starts true: the page opens loading. */
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasNextPage: Boolean = false,
    /** The last first page failed. With no rows the page shows the error state and Retry. */
    val loadFailed: Boolean = false,
    /** The last next page failed; the list ends in a retry row instead of loading on scroll. */
    val loadMoreFailed: Boolean = false,
    /** Lives only as long as this ViewModel: there is no saved sort preference yet. */
    val sort: SharedLinksSort = SharedLinksSort.NEWEST,
    val searchEnabled: Boolean = false,
    val searchQuery: String = "",
    /** The trimmed search the shown list answers. Trails [searchQuery] until its results land. */
    val shownQuery: String = "",
)

fun SharedLinksUiState.shouldLoadMore(lastVisibleIndex: Int, totalItems: Int): Boolean =
    hasNextPage &&
        links.isNotEmpty() &&
        !isLoading &&
        !isRefreshing &&
        !isLoadingMore &&
        !loadMoreFailed &&
        lastVisibleIndex >= totalItems - LOAD_MORE_THRESHOLD

private const val LOAD_MORE_THRESHOLD = 3

/** A new sort, search or Retry is loading while the old rows still show. */
val SharedLinksUiState.isReloading: Boolean
    get() = isLoading && links.isNotEmpty()

enum class SharedLinksMessage { REFRESH_FAILED, DELETE_FAILED }
