package com.garfiec.librechat.shared.navigation

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SidebarLayoutBreakpointTest {

    @Test
    fun smallTabletPortrait_keepsTheDrawer() {
        // 600dp less the 320dp sidebar would leave a 280dp pane, narrower than any phone.
        assertFalse(usesSidebarLayout(600.dp, 960.dp))
    }

    @Test
    fun roomForAPhoneWidthPane_getsTheSidebar() {
        assertTrue(usesSidebarLayout(680.dp, 800.dp))
        assertTrue(usesSidebarLayout(851.dp, 800.dp))
    }

    @Test
    fun boundaries_areInclusive() {
        assertFalse(usesSidebarLayout(679.dp, 800.dp))
        assertTrue(usesSidebarLayout(680.dp, 480.dp))
        assertFalse(usesSidebarLayout(680.dp, 479.dp))
    }

    @Test
    fun phoneOnItsSide_keepsTheDrawer() {
        assertFalse(usesSidebarLayout(900.dp, 400.dp))
        assertFalse(usesSidebarLayout(760.dp, 360.dp))
    }

    @Test
    fun phonePortrait_keepsTheDrawer() {
        assertFalse(usesSidebarLayout(411.dp, 891.dp))
    }
}
