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
    fun keyboardShrinkingTheViewportReanchorsAListOnItsTail() {
        val keyboardUp = TailRest(viewportEnd = 1300, restLine = 900, nearTail = false)
        assertTrue(reanchorsToTail(atTail, keyboardUp, lastVisibleIndex = 9, itemCount = 10))
    }

    @Test
    fun keyboardOpeningUnderAReaderScrolledUpInTheLastItemsLeavesThemThere() {
        // A short chat, or a long last reply: the last items are on screen but the reader is above the tail.
        val reading = atTail.copy(nearTail = false)
        val keyboardUp = TailRest(viewportEnd = 1300, restLine = 900, nearTail = false)
        assertFalse(reanchorsToTail(reading, keyboardUp, lastVisibleIndex = 1, itemCount = 2))
        assertFalse(reanchorsToTail(reading, keyboardUp, lastVisibleIndex = 8, itemCount = 10))
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

    // The frames a locked code-workspace chat opened offline produced on a 1080x2340 phone: the open
    // lands on the tail, then the messages above it settle 414px taller, then the workspace rows
    // measure in, raising the bottom reserve by 169 and then 126px.
    private val opened = TailRest(viewportEnd = 2108, restLine = 1625, nearTail = true, overhang = 0)
    private val messagesSettled = opened.copy(nearTail = false, overhang = 414)
    private val rowsIn = TailRest(viewportEnd = 2108, restLine = 1456, nearTail = true, overhang = 169)
    private val rowsWrapped = TailRest(viewportEnd = 2108, restLine = 1330, nearTail = true, overhang = 126)

    @Test
    fun aPinnedTailIsHeldThroughEveryFrameOfTheOpen() {
        listOf(messagesSettled, rowsIn, rowsWrapped).forEach { assertTrue(holdsPinnedTail(pinned = true, rest = it), "$it") }
    }

    @Test
    fun withoutThePinTheChromeGrowthAfterTheMessagesSettledIsMissed() {
        val settledThenAnchored = messagesSettled
        val chromeGrown = settledThenAnchored.copy(restLine = 1330, overhang = 295)
        assertFalse(reanchorsToTail(settledThenAnchored, chromeGrown, lastVisibleIndex = 13, itemCount = 14))
        assertTrue(holdsPinnedTail(pinned = true, rest = chromeGrown))
    }

    @Test
    fun anUnpinnedListIsNeverHeld() {
        listOf(messagesSettled, rowsIn, rowsWrapped).forEach { assertFalse(holdsPinnedTail(pinned = false, rest = it)) }
    }

    @Test
    fun aTailAtRestOrAShortListNeedsNoScroll() {
        assertFalse(holdsPinnedTail(pinned = true, rest = opened))
        assertFalse(holdsPinnedTail(pinned = true, rest = opened.copy(overhang = -800)))
        assertFalse(holdsPinnedTail(pinned = true, rest = opened.copy(overhang = 1)))
    }

    @Test
    fun aPressOrAFindTakesThePin() {
        assertFalse(nextTailPin(pinned = true, touching = true, scrolling = false, wasScrolling = false, nearTail = true))
    }

    @Test
    fun aScrollOfTheReadersTakesThePinWhileItRuns() {
        assertFalse(nextTailPin(pinned = true, touching = false, scrolling = true, wasScrolling = false, nearTail = true))
    }

    @Test
    fun aScrollThatComesToRestAwayFromTheTailLeavesItUnpinned() {
        assertFalse(nextTailPin(pinned = false, touching = false, scrolling = false, wasScrolling = true, nearTail = false))
    }

    @Test
    fun aScrollThatComesToRestOnTheTailPinsItAgain() {
        assertTrue(nextTailPin(pinned = false, touching = false, scrolling = false, wasScrolling = true, nearTail = true))
    }

    @Test
    fun aTapThatIsNotAScrollDoesNotPinAgain() {
        assertFalse(nextTailPin(pinned = false, touching = false, scrolling = false, wasScrolling = false, nearTail = true))
    }

    @Test
    fun layoutAloneKeepsThePin() {
        assertTrue(nextTailPin(pinned = true, touching = false, scrolling = false, wasScrolling = false, nearTail = false))
    }
}
