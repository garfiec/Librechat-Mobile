package com.garfiec.librechat.feature.chat.components

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TailReanchorTest {
    // Viewport ends at 2000px. The bottom chrome grows from a 400px reserve to 640px when the code
    // workspace rows appear, with the viewport itself unchanged.
    private val atTail = TailRest(viewportEnd = 2000, restLine = 1600, nearTail = true)
    private val chromeGrown = TailRest(viewportEnd = 2000, restLine = 1360, nearTail = false)

    @Test
    fun bottomChromeGrowingUnderATailedListReanchors() {
        assertTrue(reanchorsToTail(atTail, chromeGrown, lastVisibleIndex = 9, itemCount = 10))
    }

    @Test
    fun bottomChromeGrowingUnderAReaderParkedInTheLastReplyLeavesThemThere() {
        val reading = atTail.copy(nearTail = false)
        assertFalse(reanchorsToTail(reading, chromeGrown, lastVisibleIndex = 9, itemCount = 10))
    }

    @Test
    fun keyboardShrinkingTheViewportReanchorsAListShowingItsLastItems() {
        val reading = atTail.copy(nearTail = false)
        val keyboardUp = TailRest(viewportEnd = 1300, restLine = 900, nearTail = false)
        assertTrue(reanchorsToTail(reading, keyboardUp, lastVisibleIndex = 8, itemCount = 10))
    }

    @Test
    fun aListScrolledAwayFromItsTailStaysPut() {
        val keyboardUp = TailRest(viewportEnd = 1300, restLine = 900, nearTail = false)
        assertFalse(reanchorsToTail(atTail, keyboardUp, lastVisibleIndex = 4, itemCount = 10))
    }

    @Test
    fun chromeShrinkingNeedsNoScroll() {
        assertFalse(reanchorsToTail(chromeGrown, atTail, lastVisibleIndex = 9, itemCount = 10))
    }

    @Test
    fun theFirstLayoutIsNotAMove() {
        assertFalse(reanchorsToTail(null, chromeGrown, lastVisibleIndex = 9, itemCount = 10))
        assertFalse(reanchorsToTail(TailRest(0, 0, true), chromeGrown, lastVisibleIndex = 9, itemCount = 10))
    }

    @Test
    fun anEmptyListHasNoTail() {
        assertFalse(reanchorsToTail(atTail, chromeGrown, lastVisibleIndex = null, itemCount = 0))
    }
}
