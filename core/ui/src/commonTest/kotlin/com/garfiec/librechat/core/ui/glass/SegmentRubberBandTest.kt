package com.garfiec.librechat.core.ui.glass

import kotlin.test.Test
import kotlin.test.assertEquals

class SegmentRubberBandTest {

    @Test
    fun onTheTrackTheThumbFollowsTheFinger() {
        for (position in listOf(0f, 0.4f, 1f, 1.7f, 2f)) {
            assertEquals(position, rubberBand(position, last = 2f))
        }
    }

    @Test
    fun pastEitherEndTheThumbFollowsAQuarterAsFar() {
        assertEquals(-0.1f, rubberBand(-0.4f, last = 2f), 1e-5f)
        assertEquals(2.1f, rubberBand(2.4f, last = 2f), 1e-5f)
    }

    @Test
    fun overdragStopsAtAThirdOfASegment() {
        assertEquals(-0.33f, rubberBand(-5f, last = 2f), 1e-5f)
        assertEquals(2.33f, rubberBand(7f, last = 2f), 1e-5f)
    }
}
