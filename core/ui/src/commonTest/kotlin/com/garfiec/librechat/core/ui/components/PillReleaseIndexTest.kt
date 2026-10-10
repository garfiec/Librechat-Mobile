package com.garfiec.librechat.core.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class PillReleaseIndexTest {

    @Test
    fun slowReleaseSettlesOnTheNearestSegment() {
        assertEquals(1, pillReleaseIndex(at = 1.4f, velocity = 0f, flingVelocity = 5f, last = 3))
        assertEquals(2, pillReleaseIndex(at = 1.6f, velocity = 0f, flingVelocity = 5f, last = 3))
    }

    @Test
    fun flingPicksTheSegmentItHeadsToward() {
        assertEquals(2, pillReleaseIndex(at = 1.1f, velocity = 6f, flingVelocity = 5f, last = 3))
        assertEquals(1, pillReleaseIndex(at = 1.9f, velocity = -6f, flingVelocity = 5f, last = 3))
    }

    @Test
    fun releaseStaysOnTheTrack() {
        assertEquals(3, pillReleaseIndex(at = 3f, velocity = 6f, flingVelocity = 5f, last = 3))
        assertEquals(0, pillReleaseIndex(at = 0f, velocity = -6f, flingVelocity = 5f, last = 3))
    }
}
