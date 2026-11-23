package com.garfiec.librechat.feature.conversations.drawer

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DrawerUiStateTest {

    private val idle = DrawerUiState(listLoad = ListLoad.LOADED, hasMore = true)

    @Test
    fun `the end of the list pages on when the server has more`() {
        assertTrue(idle.shouldLoadMore(lastVisibleIndex = 17, totalItems = 25))
        assertFalse(idle.shouldLoadMore(lastVisibleIndex = 16, totalItems = 25))
        assertFalse(idle.shouldLoadMore(lastVisibleIndex = 0, totalItems = 0))
    }

    @Test
    fun `a load in flight or a finished list does not page`() {
        assertFalse(idle.copy(isLoadingMore = true).shouldLoadMore(17, 25))
        assertFalse(idle.copy(isRefreshing = true).shouldLoadMore(17, 25))
        assertFalse(idle.copy(hasMore = false).shouldLoadMore(17, 25))
        assertFalse(idle.copy(listLoad = ListLoad.PENDING).shouldLoadMore(17, 25))
    }

    @Test
    fun `a search does not page`() {
        assertFalse(idle.copy(searchQuery = "alpha").shouldLoadMore(17, 25))
        assertTrue(idle.copy(searchQuery = "  ").shouldLoadMore(17, 25))
    }

    @Test
    fun `a failed first page or a filtered list still pages while the server has more`() {
        assertTrue(idle.copy(listLoad = ListLoad.FAILED).shouldLoadMore(17, 25))
        val filtered = idle.copy(filtersEnabled = true, filters = DrawerFilters(hasFiles = true))
        assertTrue(filtered.filtersApplied)
        assertTrue(filtered.shouldLoadMore(17, 25))
    }
}
