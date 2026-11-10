package com.garfiec.librechat.core.ui.platform

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
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

/**
 * The bar column as an inset on its physical side, zero without vertical bars (Android, other iPhones).
 * Pad with it (`windowInsetsPadding`) only content that skips the horizontal safe-drawing inset, and
 * never inside an `AdaptiveScaffold` body: that padding already holds the column but consumes nothing,
 * so the column would be padded twice. Under an ancestor that consumed the column it pads nothing.
 * Like every inset it ignores where its layout sits, so a pane away from the bar side pads too.
 */
val WindowInsets.Companion.verticalBarColumn: WindowInsets
    @Composable get() {
        val bars = LocalWindowFeatures.current.verticalBars
        return remember(bars) { bars.insetOf(bars?.columnWidth ?: 0.dp) }
    }

/**
 * [WindowInsets.Companion.verticalBarColumn] cut to the part a sheet centred in the window and at most
 * [maxWidth] wide covers: on a wide window a capped sheet can stop short of the column.
 */
@Composable
internal fun WindowInsets.Companion.verticalBarColumnUnder(maxWidth: Dp): WindowInsets {
    val bars = LocalWindowFeatures.current.verticalBars
    val windowWidth = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    return remember(bars, windowWidth, maxWidth) {
        val width = minOf(windowWidth, maxWidth)
        bars.insetOf(bars?.overlap(left = (windowWidth - width) / 2, width = width, windowWidth = windowWidth) ?: 0.dp)
    }
}

/**
 * How much of the column a layout from [left] to [left] + [width] of a [windowWidth]-wide window
 * covers: none when it stops short of the column, [VerticalBars.columnWidth] when it spans it.
 */
internal fun VerticalBars.overlap(left: Dp, width: Dp, windowWidth: Dp): Dp = when (side) {
    BarSide.LEFT -> columnWidth - left
    BarSide.RIGHT -> left + width - (windowWidth - columnWidth)
}.coerceIn(0.dp, columnWidth)

/** An inset of [amount] on the bar side; zero without bars. */
internal fun VerticalBars?.insetOf(amount: Dp): WindowInsets = when (this?.side) {
    null -> WindowInsets(0.dp)
    BarSide.LEFT -> WindowInsets(left = amount)
    BarSide.RIGHT -> WindowInsets(right = amount)
}

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
