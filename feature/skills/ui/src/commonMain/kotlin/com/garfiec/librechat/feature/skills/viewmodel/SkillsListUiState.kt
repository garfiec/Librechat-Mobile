package com.garfiec.librechat.feature.skills.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.SkillSummary
import com.garfiec.librechat.core.model.response.Category
import com.garfiec.librechat.core.model.response.SkillImportFailedResponse
import com.garfiec.librechat.feature.skills.SkillError

/**
 * One list row: the skill plus what is true of it for the viewer.
 *
 * [isActive] is null while it can't be known yet (the states map or the viewer hasn't loaded), so
 * a row never flashes "Off" before the inputs arrive.
 */
@Immutable
data class SkillRowUi(
    val summary: SkillSummary,
    val isActive: Boolean?,
    val isOwnedByViewer: Boolean,
)

@Immutable
data class SkillsListUiState(
    val skills: List<SkillSummary> = emptyList(),
    /** [skills] with the viewer's active state and ownership per row, in the same order. */
    val rows: List<SkillRowUi> = emptyList(),
    /** True from the start: the first frame is "loading", never a flash of the empty state. */
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    /**
     * The last replace-load failed. Drawn only while nothing is listed; with skills on screen the
     * failure also goes out as [SkillsListEvent.LoadFailed].
     */
    val error: SkillError? = null,
    val searchQuery: String = "",
    /** The query the listed [skills] answer. Runs behind [searchQuery] while a search is in flight. */
    val shownQuery: String = "",
    val hasMore: Boolean = false,
    val cursor: String? = null,
    /**
     * The last next-page fetch failed. Auto-paging stops until [SkillsListViewModel.retryLoadMore]
     * or a reload: otherwise a failing page flips `isLoadingMore` back to false with `hasMore`
     * still true, the scroll trigger re-fires, and the screen requests the page forever.
     */
    val loadMoreFailed: Boolean = false,
    /** Fail-CLOSED: only true when SKILLS.CREATE is explicitly granted. Hides the
     *  create + import affordances unless the server would allow it. */
    val canCreate: Boolean = false,
    val isImporting: Boolean = false,
    /**
     * The last import was refused with a per-file failure report (v0.8.8-rc4). Held apart from
     * [error] on purpose: the list reload that follows clears [error], and the failed paths are the
     * only place the user learns which files their archive lost.
     */
    val importFailure: SkillImportFailedResponse? = null,
    /** Server category presets, for the cards' category labels. Empty until (or unless) loaded. */
    val categories: List<Category> = emptyList(),
) {
    /** A search is typed or still shown, so an empty list means "no results", not "no skills". */
    val isSearching: Boolean get() = searchQuery.isNotBlank() || shownQuery.isNotEmpty()

    /** The search field earns its place once there are a few skills to sift, and stays while searching. */
    val showSearch: Boolean get() = skills.size >= SEARCH_MIN_SKILLS || isSearching

    private companion object {
        const val SEARCH_MIN_SKILLS = 5
    }
}
