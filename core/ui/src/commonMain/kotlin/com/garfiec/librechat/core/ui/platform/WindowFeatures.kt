package com.garfiec.librechat.core.ui.platform

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width

/** The physical side of the window. Never start/end: the bar column is hardware-aligned and ignores RTL. */
enum class BarSide { LEFT, RIGHT }

/**
 * The system's column for vertical bars (iPhone Duo): a [columnWidth]-wide strip on [side] whose top
 * [systemZoneHeight] holds the camera, Dynamic Island and status bar. App controls go below that zone.
 */
@Immutable
data class VerticalBars(val side: BarSide, val columnWidth: Dp, val systemZoneHeight: Dp)

/**
 * An active fold, in window coordinates. [bounds] is the band content keeps out of: the hinge plus
 * the system's margins on both sides (40pt wide on the Duo's inner screen). [hinge] is [bounds]
 * without those margins.
 */
@Immutable
data class FoldRegion(val bounds: DpRect, val hinge: DpRect) {
    /** A fold running top to bottom, splitting the window into left and right halves. */
    val isVertical: Boolean get() = bounds.height > bounds.width
}

/**
 * What the platform host knows about the window's hardware that Compose's insets don't carry. Android
 * never sets it, so every field stays at its default there and layouts keyed on it don't change.
 */
@Immutable
data class WindowFeatures(
    val verticalBars: VerticalBars? = null,
    /** Present only while the fold is active (the device partly folded). */
    val fold: FoldRegion? = null,
    /** iOS's horizontal size class is regular; null where unknown (Android, iOS before 27.1). */
    val regularWidth: Boolean? = null,
)

val LocalWindowFeatures = compositionLocalOf { WindowFeatures() }

/** `UIVerticalBarEdge.unspecified`: the system draws its bars horizontally. */
private const val BAR_EDGE_UNSPECIFIED = 0

/**
 * The iOS host's raw UIKit readings turned into [WindowFeatures]; all lengths in points (= dp).
 *
 * Bars are vertical when [barEdge] is not unspecified and a horizontal safe-area inset holds the
 * column; the side is that inset's, because the edge enum is relative to the orientation (leading in
 * cover landscape, where the column is on the left) while the column follows the hardware.
 * [topLeftZone] and [topRightZone] are the lowest edge of the active occlusion regions touching each
 * top corner; the bar side's one is the system zone. [fold] is the active division region's frame,
 * which includes [foldMargins] (top, left, bottom, right), or null.
 */
@Suppress("LongParameterList") // The Swift host passes plain UIKit values.
fun windowFeaturesFromHost(
    barEdge: Int,
    leftInset: Double,
    rightInset: Double,
    topLeftZone: Double,
    topRightZone: Double,
    fold: DpRect?,
    foldMargins: DpRect,
    regularWidth: Boolean,
): WindowFeatures {
    val bars = if (barEdge != BAR_EDGE_UNSPECIFIED && maxOf(leftInset, rightInset) > 0.0) {
        val right = rightInset >= leftInset
        VerticalBars(
            side = if (right) BarSide.RIGHT else BarSide.LEFT,
            columnWidth = (if (right) rightInset else leftInset).dp,
            systemZoneHeight = (if (right) topRightZone else topLeftZone).dp,
        )
    } else {
        null
    }
    return WindowFeatures(
        verticalBars = bars,
        fold = fold?.let {
            FoldRegion(
                bounds = it,
                hinge = DpRect(
                    left = it.left + foldMargins.left,
                    top = it.top + foldMargins.top,
                    right = it.right - foldMargins.right,
                    bottom = it.bottom - foldMargins.bottom,
                ),
            )
        },
        regularWidth = regularWidth,
    )
}
