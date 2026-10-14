package com.garfiec.librechat.feature.chat.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TableColumnFitTest {

    @Test
    fun aTableThatFitsKeepsItsPreferredWidths() {
        assertEquals(listOf(100, 200), fitTableColumns(listOf(100, 200), listOf(50, 60), available = 400))
    }

    @Test
    fun anOverflowingTableIsShrunkToExactlyTheAvailableWidth() {
        val widths = fitTableColumns(listOf(300, 900), listOf(80, 100), available = 600)
        assertTrue(widths.sum() in 599..600, "sum was ${widths.sum()}")
    }

    @Test
    fun theColumnWantingToGrowMoreGivesUpMoreWidth() {
        // A short label column next to a sentence column: the sentences wrap, the labels barely move.
        val widths = fitTableColumns(listOf(160, 1000), listOf(100, 120), available = 600)
        assertTrue(widths[0] >= 100 && widths[0] < 160, "label column was ${widths[0]}")
        assertTrue(1000 - widths[1] > 160 - widths[0])
    }

    @Test
    fun noColumnIsNarrowedBelowItsLongestWord() {
        val widths = fitTableColumns(listOf(400, 400, 400), listOf(150, 150, 150), available = 500)
        assertTrue(widths.all { it >= 150 }, "widths were $widths")
    }

    @Test
    fun minimumsThatOverflowAreShrunkTowardTheFloorToFit() {
        // Two URL-bearing columns whose "longest word" is a whole link.
        val widths = fitTableColumns(listOf(561, 3264, 3684), listOf(236, 383, 383), available = 910, floor = 175)
        assertTrue(widths.sum() in 908..910, "sum was ${widths.sum()}")
        assertTrue(widths.all { it >= 175 }, "widths were $widths")
    }

    @Test
    fun aTableTooWideForItsFloorsPansAtReadableWidths() {
        // Six columns can't fit at their floors, so the table pans: short columns on one line, long ones capped.
        val widths = fitTableColumns(
            preferred = listOf(150, 900, 900, 400, 900, 900),
            minimum = List(6) { 100 },
            available = 600,
            floor = 175,
            panCap = 500,
        )
        assertEquals(listOf(150, 500, 500, 400, 500, 500), widths)
    }

    @Test
    fun shortWordsDoNotLetASentenceColumnBeSqueezedBelowTheFloor() {
        // Every word is short, so the minimums alone would fit at 100 each — a column 100px wide.
        val widths = fitTableColumns(List(4) { 900 }, List(4) { 100 }, available = 600, floor = 175, panCap = 500)
        assertEquals(List(4) { 500 }, widths)
    }

    @Test
    fun aTableThatFitsAtItsFloorsDoesNotPan() {
        val widths = fitTableColumns(List(3) { 900 }, List(3) { 300 }, available = 600, floor = 175, panCap = 500)
        assertEquals(600, widths.sum())
    }

    @Test
    fun aFloorNeverWidensAColumnPastItsMinimum() {
        val widths = fitTableColumns(listOf(60, 900, 900), listOf(60, 400, 400), available = 500, floor = 175)
        assertEquals(60, widths[0])
    }

    @Test
    fun aMinimumIsNeverWiderThanItsPreferredWidth() {
        assertEquals(listOf(40, 700), fitTableColumns(listOf(40, 900), listOf(90, 700), available = 740, floor = 175))
    }
}
