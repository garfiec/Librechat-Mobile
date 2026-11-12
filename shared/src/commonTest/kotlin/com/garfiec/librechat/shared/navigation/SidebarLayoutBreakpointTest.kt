package com.garfiec.librechat.shared.navigation

import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.ArtifactPresentation
import com.garfiec.librechat.core.ui.components.automaticArtifactPresentation
import com.garfiec.librechat.core.ui.components.splitKeepsSidebar
import com.garfiec.librechat.core.ui.components.usesSidebarLayout
import kotlin.test.Test
import kotlin.test.assertEquals
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

    @Test
    fun automaticArtifact_byWindow() {
        val cases = listOf(
            Triple(412.dp, 892.dp, ArtifactPresentation.SHEET), // phone, portrait
            Triple(892.dp, 412.dp, ArtifactPresentation.FULL_SCREEN), // phone on its side
            Triple(600.dp, 960.dp, ArtifactPresentation.SHEET), // 8" tablet, portrait
            Triple(960.dp, 600.dp, ArtifactPresentation.SPLIT), // 8" tablet, landscape
            Triple(701.dp, 841.dp, ArtifactPresentation.SPLIT), // book-style fold, open
            Triple(841.dp, 701.dp, ArtifactPresentation.SPLIT), // the same, turned
            Triple(791.dp, 820.dp, ArtifactPresentation.SPLIT), // Pixel Fold emulator, open
            Triple(800.dp, 1280.dp, ArtifactPresentation.SPLIT), // tablet, portrait
            Triple(1280.dp, 800.dp, ArtifactPresentation.SPLIT), // tablet, landscape
            Triple(1032.dp, 1376.dp, ArtifactPresentation.SPLIT), // iPad Pro 13", portrait
        )
        cases.forEach { (width, height, expected) ->
            assertEquals(expected, automaticArtifactPresentation(width, height), "$width x $height")
        }
    }

    @Test
    fun automaticArtifact_boundaries() {
        assertEquals(ArtifactPresentation.SPLIT, automaticArtifactPresentation(664.dp, 480.dp))
        assertEquals(ArtifactPresentation.SHEET, automaticArtifactPresentation(663.dp, 480.dp))
        assertEquals(ArtifactPresentation.FULL_SCREEN, automaticArtifactPresentation(1280.dp, 479.dp))
    }

    @Test
    fun split_keepsTheSidebarOnlyWhenBothPanesFitBesideIt() {
        assertTrue(splitKeepsSidebar(1280.dp))
        assertTrue(splitKeepsSidebar(984.dp))
        assertFalse(splitKeepsSidebar(983.dp))
        assertFalse(splitKeepsSidebar(800.dp))
    }
}
