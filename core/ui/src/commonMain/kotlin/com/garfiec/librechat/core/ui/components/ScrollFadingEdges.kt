package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Fades a scrolling container's content out toward an edge that has more to scroll past, instead of
 * cutting it off (a scrolling menu's rows, a chip line). Each fade grows with the distance left to
 * scroll, up to [length], so a menu at rest at its top shows a hard top edge. [fadeStart] false fades
 * only the end. A horizontal fade follows the layout direction: the start is the right edge in RTL.
 * Goes before `verticalScroll(state)` / `horizontalScroll(state)`. A container that doesn't scroll
 * draws as it always has: the offscreen layer the fade needs is only used while it can scroll.
 * [reversed] matches `verticalScroll(state, reverseScrolling = true)`, whose hidden overflow at rest is at the start.
 * [fadeTo] paints the fade in that colour over the content instead of fading it to transparent, for
 * content that floats over other content, which would show through it.
 */
fun Modifier.scrollFadingEdges(
    state: ScrollState,
    orientation: Orientation = Orientation.Vertical,
    fadeStart: Boolean = true,
    length: Dp = FadeLength,
    reversed: Boolean = false,
    fadeTo: (() -> Color)? = null,
): Modifier =
    graphicsLayer {
        val alphaFade = state.maxValue > 0 && fadeTo == null
        compositingStrategy = if (alphaFade) CompositingStrategy.Offscreen else CompositingStrategy.Auto
    }.drawWithContent {
        drawContent()
        if (state.maxValue <= 0) return@drawWithContent
        val max = length.toPx()
        val towardStart = if (reversed) state.maxValue - state.value else state.value
        val color = fadeTo?.invoke()
        if (fadeStart) fade(orientation, atStart = true, extent = towardStart.toFloat().coerceAtMost(max), color)
        fade(orientation, atStart = false, extent = (state.maxValue - towardStart).toFloat().coerceAtMost(max), color)
    }

private fun ContentDrawScope.fade(orientation: Orientation, atStart: Boolean, extent: Float, color: Color?) {
    if (extent <= 0f) return
    val edge = color ?: Color.Transparent
    val inner = color?.copy(alpha = 0f) ?: Color.Black
    val blendMode = if (color != null) BlendMode.SrcOver else BlendMode.DstIn
    if (orientation == Orientation.Vertical) {
        val start = if (atStart) 0f else size.height - extent
        drawRect(
            brush = Brush.verticalGradient(
                colors = if (atStart) listOf(edge, inner) else listOf(inner, edge),
                startY = start,
                endY = start + extent,
            ),
            topLeft = Offset(0f, start),
            size = Size(size.width, extent),
            blendMode = blendMode,
        )
        return
    }
    val left = atStart != (layoutDirection == LayoutDirection.Rtl)
    val start = if (left) 0f else size.width - extent
    drawRect(
        brush = Brush.horizontalGradient(
            colors = if (left) listOf(edge, inner) else listOf(inner, edge),
            startX = start,
            endX = start + extent,
        ),
        topLeft = Offset(start, 0f),
        size = Size(extent, size.height),
        blendMode = blendMode,
    )
}

private val FadeLength = 24.dp
