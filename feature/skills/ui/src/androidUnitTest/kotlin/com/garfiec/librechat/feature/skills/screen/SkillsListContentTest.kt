package com.garfiec.librechat.feature.skills.screen

import com.garfiec.librechat.core.model.SkillSummary
import com.garfiec.librechat.feature.skills.SkillError
import com.garfiec.librechat.feature.skills.SkillErrorKind
import com.garfiec.librechat.feature.skills.viewmodel.SkillsListUiState
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SkillsListContentTest {

    private val skills = listOf(SkillSummary(id = "a", name = "a"))

    @Test
    fun `the first frame shows placeholders`() {
        assertThat(listContent(SkillsListUiState())).isEqualTo(ListContent.PLACEHOLDERS)
    }

    @Test
    fun `a loaded list shows cards, even while it reloads`() {
        assertThat(listContent(SkillsListUiState(skills = skills, isLoading = false))).isEqualTo(ListContent.CARDS)
        assertThat(listContent(SkillsListUiState(skills = skills, isLoading = true))).isEqualTo(ListContent.CARDS)
    }

    @Test
    fun `nothing loaded and nothing searched is the empty state`() {
        assertThat(listContent(SkillsListUiState(isLoading = false))).isEqualTo(ListContent.EMPTY)
    }

    @Test
    fun `a failed first load is the error banner, also under a pull-to-refresh`() {
        val boom = SkillError(SkillErrorKind.LOAD_SKILLS, "boom")
        assertThat(listContent(SkillsListUiState(isLoading = false, error = boom))).isEqualTo(ListContent.ERROR)
        assertThat(listContent(SkillsListUiState(isLoading = false, isRefreshing = true, error = boom)))
            .isEqualTo(ListContent.ERROR)
    }

    @Test
    fun `an empty search is no results, and stays so while the next query runs`() {
        val answered = SkillsListUiState(isLoading = false, searchQuery = "zz", shownQuery = "zz")
        assertThat(listContent(answered)).isEqualTo(ListContent.NO_RESULTS)
        assertThat(listContent(answered.copy(searchQuery = "zzz", isLoading = true))).isEqualTo(ListContent.NO_RESULTS)
        // Cleared, the unfiltered list still loading: no flash of "No skills yet".
        assertThat(listContent(answered.copy(searchQuery = "", isLoading = true))).isEqualTo(ListContent.NO_RESULTS)
    }
}
