package com.garfiec.librechat.core.ui.components

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RubberBandTest {

    @Test
    fun noTravelGivesNoStretch() {
        assertEquals(0f, rubberBand(distance = 0f, cap = 10f, softness = 0.5f))
        assertEquals(0f, rubberBand(distance = -5f, cap = 10f, softness = 0.5f))
    }

    @Test
    fun smallTravelIsNearlyLinear() {
        // The slope at zero is the softness.
        val stretch = rubberBand(distance = 0.1f, cap = 10f, softness = 0.5f)
        assertEquals(0.05f, stretch, absoluteTolerance = 0.001f)
    }

    @Test
    fun largeTravelApproachesButNeverPassesTheCap() {
        val far = rubberBand(distance = 100_000f, cap = 10f, softness = 0.5f)
        assertTrue(far < 10f)
        assertEquals(10f, far, absoluteTolerance = 0.01f)
    }

    @Test
    fun stretchGrowsWithTravel() {
        val stretches = listOf(1f, 10f, 50f, 200f).map { rubberBand(it, cap = 10f, softness = 0.5f) }
        assertEquals(stretches.sorted(), stretches)
    }

    @Test
    fun lowerSoftnessNeedsMoreTravel() {
        assertTrue(rubberBand(20f, cap = 10f, softness = 0.1f) < rubberBand(20f, cap = 10f, softness = 0.5f))
    }

    @Test
    fun halfTheCapAtCapOverSoftness() {
        assertEquals(5f, rubberBand(distance = 20f, cap = 10f, softness = 0.5f), absoluteTolerance = 0.001f)
    }

    @Test
    fun zeroVectorStaysZero() {
        assertEquals(Offset.Zero, rubberVector(Offset.Zero, cap = 10f, softness = 0.5f))
    }

    @Test
    fun vectorKeepsItsDirectionAndBandsItsLength() {
        val vector = Offset(30f, -40f)
        val banded = rubberVector(vector, cap = 10f, softness = 0.5f)
        assertEquals(rubberBand(50f, cap = 10f, softness = 0.5f), banded.getDistance(), absoluteTolerance = 0.001f)
        // Same direction: the cross product is zero and the dot product positive.
        assertTrue(abs(vector.x * banded.y - vector.y * banded.x) < 0.001f)
        assertTrue(vector.x * banded.x + vector.y * banded.y > 0f)
    }
}
