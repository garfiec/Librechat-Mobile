package com.garfiec.librechat.core.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MaterialMenuMotionTest {

    private val clocks = (0..20).map { it / 20f }

    @Test
    fun everyRowStartsHiddenAndEndsShown() {
        for (index in 0..10) {
            assertEquals(0f, rowProgress(0f, index), "row $index")
            assertEquals(1f, rowProgress(1f, index), "row $index")
        }
    }

    @Test
    fun rowProgressNeverRunsBackwardsOrLeavesTheUnitRange() {
        for (index in 0..10) {
            clocks.zipWithNext().forEach { (earlier, later) ->
                val a = rowProgress(earlier, index)
                val b = rowProgress(later, index)
                assertTrue(a in 0f..1f && b in 0f..1f, "row $index at $earlier")
                assertTrue(b >= a, "row $index went back between $earlier and $later")
            }
        }
    }

    @Test
    fun eachRowFollowsTheOneAboveIt() {
        for (index in 0..4) {
            assertTrue(rowProgress(MID, index) > rowProgress(MID, index + 1), "row $index vs ${index + 1}")
        }
    }

    @Test
    fun rowsPastTheCapStartTogether() {
        // A long menu doesn't make its last rows wait: from the cap on, rows move as one.
        // The cap is the first row that moves with the next one for the whole open; two rows that
        // merely haven't started yet agree only until they do.
        val cap = (0..20).first { index -> clocks.all { rowProgress(it, index) == rowProgress(it, index + 1) } }
        assertTrue(cap > 1, "the first rows are staggered")
        for (clock in clocks) {
            assertEquals(rowProgress(clock, cap), rowProgress(clock, cap + 14), "at $clock")
        }
    }

    @Test
    fun panelGrowsFromItsCornerNearestTheAnchor() {
        assertRect(Rect(0f, 0f, GROWN_W, GROWN_H), growCorner(anchorAt(10f, 10f), panel))
        assertRect(Rect(100f - GROWN_W, 0f, 100f, GROWN_H), growCorner(anchorAt(90f, 10f), panel))
        assertRect(Rect(0f, 200f - GROWN_H, GROWN_W, 200f), growCorner(anchorAt(10f, 190f), panel))
        assertRect(Rect(100f - GROWN_W, 200f - GROWN_H, 100f, 200f), growCorner(anchorAt(90f, 190f), panel))
    }

    @Test
    fun anchorOutsideThePanelStillGrowsFromInsideIt() {
        // Above and past the end of the panel, as when the menu opens below its button.
        assertRect(Rect(100f - GROWN_W, 0f, 100f, GROWN_H), growCorner(anchorAt(150f, -40f), panel))
        assertRect(Rect(0f, 200f - GROWN_H, GROWN_W, 200f), growCorner(anchorAt(-30f, 260f), panel))
    }

    @Test
    fun anchorCentredOnThePanelGrowsFromTheTopEnd() {
        assertRect(Rect(100f - GROWN_W, 0f, 100f, GROWN_H), growCorner(anchorAt(50f, 100f), panel))
    }

    private val panel = Size(100f, 200f)

    private fun anchorAt(x: Float, y: Float) = Rect(Offset(x - 5f, y - 5f), Size(10f, 10f))

    private fun assertRect(expected: Rect, actual: Rect) {
        val close = listOf(
            expected.left to actual.left,
            expected.top to actual.top,
            expected.right to actual.right,
            expected.bottom to actual.bottom,
        ).all { (e, a) -> abs(e - a) < TOLERANCE }
        assertTrue(close, "expected $expected, was $actual")
    }

    private companion object {
        /** Partway through the open, when the first rows have started and the later ones haven't arrived. */
        const val MID = 0.5f

        // The panel grows from 0.3 of its size: 100×200 → 30×60.
        const val GROWN_W = 30f
        const val GROWN_H = 60f
        const val TOLERANCE = 0.01f
    }
}
