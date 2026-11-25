package com.garfiec.librechat.feature.chat.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Whose a drag that starts on the composer's chrome is: the thread's, or a row's own. */
class PassThroughDragTest {
    private val slop = 10f

    @Test
    fun movementInsideTheSlopIsNobodysYet() {
        val drag = PassThroughDrag(slop)
        assertEquals(0f, drag.onMove(dx = 2f, dy = 6f, consumedByChild = false))
        assertFalse(drag.isForwarding)
        assertFalse(drag.isStoodDown)
    }

    @Test
    fun aMostlyVerticalDragPastTheSlopGoesToTheThreadWithOnlyWhatLiesPastIt() {
        val drag = PassThroughDrag(slop)
        drag.onMove(dx = 1f, dy = 6f, consumedByChild = false)
        assertEquals(4f, drag.onMove(dx = 1f, dy = 8f, consumedByChild = false))
        assertTrue(drag.isForwarding)
        // Once it has the drag, every move goes through whole, sideways drift and all.
        assertEquals(-3f, drag.onMove(dx = 9f, dy = -3f, consumedByChild = false))
    }

    @Test
    fun anUpwardDragKeepsItsSign() {
        val drag = PassThroughDrag(slop)
        assertEquals(-5f, drag.onMove(dx = 0f, dy = -15f, consumedByChild = false))
    }

    @Test
    fun aSidewaysDragIsLeftToTheRow() {
        val drag = PassThroughDrag(slop)
        assertEquals(0f, drag.onMove(dx = 12f, dy = 4f, consumedByChild = false))
        assertTrue(drag.isStoodDown)
        assertEquals(0f, drag.onMove(dx = 0f, dy = 40f, consumedByChild = false))
        assertFalse(drag.isForwarding)
    }

    @Test
    fun aDiagonalPastTheSlopThatIsMoreSidewaysStandsDown() {
        val drag = PassThroughDrag(slop)
        drag.onMove(dx = 11f, dy = 11f, consumedByChild = false)
        assertTrue(drag.isStoodDown)
    }

    @Test
    fun aChildThatConsumesTakesTheGestureForGood() {
        // A horizontal chip row past its slop, or the queue's long-press reorder once it started.
        val drag = PassThroughDrag(slop)
        drag.onMove(dx = 0f, dy = 4f, consumedByChild = false)
        assertEquals(0f, drag.onMove(dx = 0f, dy = 30f, consumedByChild = true))
        assertTrue(drag.isStoodDown)
        assertEquals(0f, drag.onMove(dx = 0f, dy = 30f, consumedByChild = false))
    }

    @Test
    fun aChildConsumingMidForwardEndsTheForward() {
        val drag = PassThroughDrag(slop)
        drag.onMove(dx = 0f, dy = 20f, consumedByChild = false)
        assertTrue(drag.isForwarding)
        assertEquals(0f, drag.onMove(dx = 0f, dy = 5f, consumedByChild = true))
        assertTrue(drag.isStoodDown)
    }
}

/** A row held near the queue region's edge scrolls the region under it; the middle does nothing. */
class ReorderAutoScrollStepTest {
    private fun step(pointerY: Float) =
        reorderAutoScrollStep(pointerY = pointerY, viewportTop = 100f, viewportBottom = 300f, edge = 20f, maxStep = 10f)

    @Test
    fun theMiddleOfTheRegionDoesNotScroll() {
        assertEquals(0f, step(200f))
        assertEquals(0f, step(120f))
        assertEquals(0f, step(280f))
    }

    @Test
    fun nearTheTopItScrollsUpFasterTheCloserItGets() {
        assertEquals(-5f, step(110f))
        assertEquals(-10f, step(100f))
    }

    @Test
    fun nearTheBottomItScrollsDown() {
        assertEquals(5f, step(290f))
        assertEquals(10f, step(300f))
    }

    @Test
    fun pastEitherEdgeItScrollsAtFullSpeed() {
        assertEquals(-10f, step(40f))
        assertEquals(10f, step(360f))
    }

    @Test
    fun aRegionNoTallerThanItsEdgesPicksTheNearerEdge() {
        val tiny = { y: Float -> reorderAutoScrollStep(y, viewportTop = 0f, viewportBottom = 30f, edge = 20f, maxStep = 10f) }
        assertTrue(tiny(5f) < 0f)
        assertTrue(tiny(25f) > 0f)
    }

    @Test
    fun noViewportNoScroll() {
        assertEquals(0f, reorderAutoScrollStep(10f, viewportTop = 0f, viewportBottom = 0f, edge = 20f, maxStep = 10f))
    }
}

/** The list a drag on the composer scrolls is the one it covers. */
class ThreadScrollTargetTest {
    @Test
    fun aSingleChatScrollsItsList() {
        assertEquals(ThreadScrollTarget.PRIMARY, threadScrollTarget(comparing = false, wide = false, activeTab = 1))
    }

    @Test
    fun aPhoneComparisonScrollsTheTabOnScreen() {
        assertEquals(ThreadScrollTarget.PRIMARY, threadScrollTarget(comparing = true, wide = false, activeTab = 0))
        assertEquals(ThreadScrollTarget.SECONDARY, threadScrollTarget(comparing = true, wide = false, activeTab = 1))
    }

    @Test
    fun sideBySidePaneALeads() {
        assertEquals(ThreadScrollTarget.PRIMARY, threadScrollTarget(comparing = true, wide = true, activeTab = 1))
    }
}

/** The "+" button's "tools on" dot, drawn the same on Android and iOS. */
class ActiveToolsDotTest {
    @Test
    fun aToolOrAnMcpServerOnShowsTheDot() {
        assertTrue(showsActiveToolsDot(showEphemeralTools = true, enabledTools = setOf("web_search"), selectedMcpServerNames = emptySet()))
        assertTrue(showsActiveToolsDot(showEphemeralTools = true, enabledTools = emptySet(), selectedMcpServerNames = setOf("github")))
    }

    @Test
    fun nothingOnShowsNoDot() {
        assertFalse(showsActiveToolsDot(showEphemeralTools = true, enabledTools = emptySet(), selectedMcpServerNames = emptySet()))
    }

    @Test
    fun whereTheComposerShowsNoToolsTheKeptChoiceShowsNoDot() {
        // The agents endpoint: the agent's own tools run, and the user's stay for the next model.
        assertFalse(
            showsActiveToolsDot(showEphemeralTools = false, enabledTools = setOf("web_search"), selectedMcpServerNames = setOf("github")),
        )
    }
}
