package com.garfiec.librechat.core.ui.media

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MediaFlightGeometryTest {

    // A 1000×2000 page showing a 1000×500 landscape image, fitted and centred.
    private val image = Rect(0f, 750f, 1000f, 1250f)

    @Test
    fun aboutScalesAroundThePivotThenOffsets() {
        val transform = PageTransform.about(pivot = Offset(500f, 1000f), scale = 0.5f, offset = Offset(10f, 20f))
        assertRectEquals(Rect(260f, 895f, 760f, 1145f), transform.map(image))
    }

    @Test
    fun coverFillsTheTargetCroppingTheLongerSide() {
        val square = Rect(100f, 100f, 300f, 300f)
        val covered = PageTransform.cover(image, square).map(image)
        // Height matches; width overflows equally on both sides, centred on the target.
        assertRectEquals(Rect(0f, 100f, 400f, 300f), covered)
    }

    @Test
    fun flightStartsWhereThePageIsDrawnUnclipped() {
        val from = PageTransform.about(Offset(500f, 1000f), 0.8f, Offset(0f, 300f))
        val flight = Flight(image, from, fromCorner = 0f, target = Rect(100f, 100f, 300f, 300f), targetCorner = 48f, fade = false)
        val frame = flight.frame(0f)
        assertEquals(from, frame.transform)
        assertRectEquals(from.map(image), frame.clip)
        assertEquals(0f, frame.cornerRadius)
    }

    @Test
    fun flightLandsExactlyOnTheThumbnailCrop() {
        val target = Rect(100f, 100f, 300f, 300f)
        val flight = Flight(image, PageTransform(), fromCorner = 0f, target = target, targetCorner = 48f, fade = false)
        val frame = flight.frame(1f)
        assertRectEquals(target, frame.clip)
        assertEquals(48f, frame.cornerRadius)
        assertEquals(1f, frame.alpha)
        // The image covers the window it shows through, as a centre-cropped thumbnail does.
        assertRectEquals(Rect(0f, 100f, 400f, 300f), frame.transform.map(image))
    }

    @Test
    fun flightWindowStaysInsideTheImageMidway() {
        val target = Rect(100f, 100f, 300f, 300f)
        val flight = Flight(image, PageTransform(), fromCorner = 0f, target = target, targetCorner = 48f, fade = false)
        val frame = flight.frame(0.5f)
        val drawn = frame.transform.map(image)
        val clip = frame.clip
        assertTrue(drawn.left <= clip.left + EPSILON && drawn.top <= clip.top + EPSILON)
        assertTrue(drawn.right >= clip.right - EPSILON && drawn.bottom >= clip.bottom - EPSILON)
    }

    @Test
    fun fadingFlightEndsTransparent() {
        val flight = Flight(image, PageTransform(), 0f, image.scaledAboutCenter(0.75f), 0f, fade = true)
        assertEquals(0f, flight.frame(1f).alpha)
        assertRectEquals(Rect(125f, 812.5f, 875f, 1187.5f), flight.frame(1f).clip)
    }

    @Test
    fun mostVisiblePicksTheLargestOnScreenArea() {
        val viewport = Rect(0f, 0f, 1000f, 2000f)
        val candidates = listOf(
            "half-off" to Rect(-100f, 0f, 100f, 200f),
            "whole" to Rect(200f, 200f, 380f, 380f),
            "off" to Rect(-500f, 0f, -100f, 400f),
        )
        assertEquals("whole", mostVisible(candidates, viewport) { it.second }?.first)
    }

    @Test
    fun mostVisibleIsNullWhenNothingIsOnScreen() {
        val viewport = Rect(0f, 0f, 1000f, 2000f)
        val candidates = listOf(Rect(0f, 2000f, 100f, 2100f), Rect(-100f, 0f, 0f, 100f))
        assertNull(mostVisible(candidates, viewport) { it })
    }

    private fun assertRectEquals(expected: Rect, actual: Rect) {
        val close = abs(expected.left - actual.left) < EPSILON && abs(expected.top - actual.top) < EPSILON &&
            abs(expected.right - actual.right) < EPSILON && abs(expected.bottom - actual.bottom) < EPSILON
        assertTrue(close, "expected $expected, was $actual")
    }

    private companion object {
        const val EPSILON = 0.01f
    }
}
