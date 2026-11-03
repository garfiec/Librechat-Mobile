package com.garfiec.librechat.shared

import com.garfiec.librechat.shared.navigation.EdgeSwipeBackRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EdgeSwipeBackRulesTest {

    @Test
    fun aFastFlickAwayFromTheEdgeGoesBackHoweverShort() {
        assertTrue(EdgeSwipeBackRules.commits(travelPx = 40f, widthPx = 1000f, velocityDpPerSecond = 500f))
    }

    @Test
    fun movingBackTowardTheEdgeStaysHoweverFar() {
        assertFalse(EdgeSwipeBackRules.commits(travelPx = 900f, widthPx = 1000f, velocityDpPerSecond = -200f))
    }

    @Test
    fun aSlowReleaseGoesBackOnlyPastThirtyPercent() {
        assertFalse(EdgeSwipeBackRules.commits(travelPx = 299f, widthPx = 1000f, velocityDpPerSecond = 0f))
        assertTrue(EdgeSwipeBackRules.commits(travelPx = 300f, widthPx = 1000f, velocityDpPerSecond = 0f))
    }

    @Test
    fun progressIsTheShareOfTheWidthClampedToZeroAndOne() {
        assertEquals(0.25f, EdgeSwipeBackRules.progress(travelPx = 250f, widthPx = 1000f))
        assertEquals(0f, EdgeSwipeBackRules.progress(travelPx = -50f, widthPx = 1000f))
        assertEquals(1f, EdgeSwipeBackRules.progress(travelPx = 1500f, widthPx = 1000f))
        assertEquals(0f, EdgeSwipeBackRules.progress(travelPx = 10f, widthPx = 0f))
    }
}
