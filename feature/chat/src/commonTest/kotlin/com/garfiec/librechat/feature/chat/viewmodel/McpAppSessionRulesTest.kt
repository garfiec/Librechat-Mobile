package com.garfiec.librechat.feature.chat.viewmodel

import com.garfiec.librechat.feature.chat.viewmodel.McpAppSessionRules.ModeChange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class McpAppSessionRulesTest {

    @Test
    fun `the cap is the server's limit up to three and one on a low-RAM device`() {
        assertEquals(3, McpAppSessionRules.viewCap(maxActiveViews = 3, lowRamDevice = false))
        assertEquals(3, McpAppSessionRules.viewCap(maxActiveViews = 32, lowRamDevice = false))
        assertEquals(2, McpAppSessionRules.viewCap(maxActiveViews = 2, lowRamDevice = false))
        assertEquals(1, McpAppSessionRules.viewCap(maxActiveViews = 0, lowRamDevice = false))
        assertEquals(1, McpAppSessionRules.viewCap(maxActiveViews = 3, lowRamDevice = true))
    }

    @Test
    fun `the inline slot is clamped to 80dp and 60 percent of the pane`() {
        assertEquals(80, McpAppSessionRules.inlineHeightDp(reportedDp = 10, paneHeightDp = 800))
        assertEquals(300, McpAppSessionRules.inlineHeightDp(reportedDp = 300, paneHeightDp = 800))
        assertEquals(480, McpAppSessionRules.inlineHeightDp(reportedDp = 2000, paneHeightDp = 800))
        assertEquals(4000, McpAppSessionRules.inlineHeightDp(reportedDp = 9000, paneHeightDp = 9000))
        // A pane too short for the floor still gets the floor.
        assertEquals(80, McpAppSessionRules.inlineHeightDp(reportedDp = 300, paneHeightDp = 100))
    }

    @Test
    fun `the panel is the only foreground while it is up and otherwise every visible card is`() {
        val ids = listOf(1L, 2L, 3L)
        assertEquals(setOf(2L), McpAppSessionRules.foreground(ids, panelId = 2L, visibleInline = setOf(1L, 3L)))
        assertEquals(setOf(1L, 3L), McpAppSessionRules.foreground(ids, panelId = null, visibleInline = setOf(1L, 3L, 9L)))
    }

    @Test
    fun `one question at a time from the foreground in open order`() {
        val ids = listOf(1L, 2L, 3L)
        assertEquals(2L, McpAppSessionRules.presented(ids, foreground = setOf(2L, 3L), awaiting = setOf(1L, 2L, 3L)))
        assertNull(McpAppSessionRules.presented(ids, foreground = setOf(3L), awaiting = setOf(1L)))
    }

    @Test
    fun `eviction spares what the user sees and what waits on them`() {
        assertEquals(
            listOf(3L),
            McpAppSessionRules.evictable(listOf(1L, 2L, 3L), foreground = setOf(1L), awaiting = setOf(2L)),
        )
    }

    @Test
    fun `display mode requests`() {
        val fg = setOf(1L)
        assertEquals(ModeChange.Expand, McpAppSessionRules.modeChange(1L, "fullscreen", panelId = null, foreground = fg))
        assertEquals(ModeChange.None, McpAppSessionRules.modeChange(2L, "fullscreen", panelId = null, foreground = fg))
        assertEquals(ModeChange.Collapse, McpAppSessionRules.modeChange(1L, "inline", panelId = 1L, foreground = fg))
        assertEquals(ModeChange.None, McpAppSessionRules.modeChange(1L, "pip", panelId = 1L, foreground = fg))
        assertEquals("fullscreen", McpAppSessionRules.displayMode(1L, panelId = 1L))
        assertEquals("inline", McpAppSessionRules.displayMode(2L, panelId = 1L))
    }

    @Test
    fun `closing the panel's App shows the last used other one`() {
        val ids = listOf(1L, 2L, 3L)
        assertEquals(1L, McpAppSessionRules.panelAfterClose(ids, closedId = 3L, panelId = 3L, lastUsed = listOf(2L, 1L, 3L)))
        assertEquals(2L, McpAppSessionRules.panelAfterClose(ids, closedId = 3L, panelId = 2L, lastUsed = emptyList()))
        assertNull(McpAppSessionRules.panelAfterClose(listOf(3L), closedId = 3L, panelId = 3L, lastUsed = listOf(3L)))
        assertNull(McpAppSessionRules.panelAfterClose(ids, closedId = 3L, panelId = null, lastUsed = emptyList()))
    }
}
