package com.garfiec.librechat.core.ui.platform

import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The iOS host's readings, as measured on the iPhone Duo simulator. */
class WindowFeaturesTest {

    private val noMargins = DpRect(0.dp, 0.dp, 0.dp, 0.dp)

    private fun host(
        barEdge: Int,
        leftInset: Double = 0.0,
        rightInset: Double = 0.0,
        topLeftZone: Double = 0.0,
        topRightZone: Double = 0.0,
        fold: DpRect? = null,
        foldMargins: DpRect = noMargins,
        regularWidth: Boolean = false,
    ) = windowFeaturesFromHost(barEdge, leftInset, rightInset, topLeftZone, topRightZone, fold, foldMargins, regularWidth)

    @Test
    fun coverPortraitHasTheColumnOnTheRight() {
        val bars = host(barEdge = TRAILING, rightInset = 84.0, topRightZone = 174.0).verticalBars

        assertEquals(VerticalBars(BarSide.RIGHT, 84.dp, 174.dp), bars)
    }

    @Test
    fun coverLandscapeHasTheColumnOnTheLeftThoughTheEdgeIsLeading() {
        val bars = host(barEdge = LEADING, leftInset = 84.0, topLeftZone = 82.0, topRightZone = 0.0).verticalBars

        assertEquals(VerticalBars(BarSide.LEFT, 84.dp, 82.dp), bars)
    }

    @Test
    fun anUnspecifiedEdgeOrNoSideInsetMeansHorizontalBars() {
        assertNull(host(barEdge = UNSPECIFIED, rightInset = 84.0, topRightZone = 124.0).verticalBars)
        assertNull(host(barEdge = TRAILING, topRightZone = 124.0).verticalBars)
    }

    @Test
    fun anActiveFoldKeepsItsBandAndItsHinge() {
        val fold = host(
            barEdge = TRAILING,
            rightInset = 84.0,
            fold = DpRect(455.5.dp, 0.dp, 495.5.dp, 669.dp),
            foldMargins = DpRect(left = 20.dp, top = 0.dp, right = 20.dp, bottom = 0.dp),
            regularWidth = true,
        ).fold

        assertEquals(DpRect(455.5.dp, 0.dp, 495.5.dp, 669.dp), fold?.bounds)
        assertEquals(DpRect(475.5.dp, 0.dp, 475.5.dp, 669.dp), fold?.hinge)
        assertTrue(fold?.isVertical == true)
    }

    @Test
    fun aPortraitFoldIsHorizontal() {
        val fold = host(barEdge = UNSPECIFIED, fold = DpRect(0.dp, 455.5.dp, 669.dp, 495.5.dp)).fold

        assertFalse(fold?.isVertical ?: true)
    }

    @Test
    fun theDefaultsSayNothing() {
        assertEquals(WindowFeatures(verticalBars = null, fold = null, regularWidth = null), WindowFeatures())
    }

    private companion object {
        const val UNSPECIFIED = 0
        const val LEADING = 1
        const val TRAILING = 2
    }
}
