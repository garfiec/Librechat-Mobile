package com.garfiec.librechat.feature.chat.components.mcpapp

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LegacyUiResourceBlockTest {

    @Test
    fun `without a cap a resource is drawn at the height it reports`() {
        assertEquals(1400.dp, legacyUiFrameHeight(reported = 1400, minHeight = 80.dp, cap = null))
        assertFalse(legacyUiClipped(reported = 1400, cap = null))
    }

    @Test
    fun `with a cap a taller resource is held to it and says it is clipped`() {
        assertEquals(646.dp, legacyUiFrameHeight(reported = 1400, minHeight = 80.dp, cap = 646.dp))
        assertTrue(legacyUiClipped(reported = 1400, cap = 646.dp))
    }

    @Test
    fun `a resource that fits the cap is not clipped`() {
        assertEquals(320.dp, legacyUiFrameHeight(reported = 320, minHeight = 80.dp, cap = 646.dp))
        assertFalse(legacyUiClipped(reported = 320, cap = 646.dp))
    }

    @Test
    fun `before its first report a resource takes the default height and never less than its floor`() {
        assertEquals(240.dp, legacyUiFrameHeight(reported = null, minHeight = 80.dp, cap = 646.dp))
        assertEquals(360.dp, legacyUiFrameHeight(reported = null, minHeight = 360.dp, cap = null))
        assertFalse(legacyUiClipped(reported = null, cap = 646.dp))
    }

    @Test
    fun `an uncapped report past what Compose can lay out is held to the ceiling`() {
        assertEquals(20_000.dp, legacyUiFrameHeight(reported = Int.MAX_VALUE, minHeight = 80.dp, cap = null))
    }

    @Test
    fun `a tiny report is raised to the floor`() {
        assertEquals(80.dp, legacyUiFrameHeight(reported = 10, minHeight = 80.dp, cap = null))
    }
}
