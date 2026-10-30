package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.ScrollState
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
import androidx.compose.ui.unit.dp

/**
 * Fades a scrolling menu's rows out toward an edge that has more to scroll past, instead of cutting
 * them off. Each fade grows with the distance left to scroll, up to [length], so a menu at rest at
 * its top shows a hard top edge. Goes before `verticalScroll(state)`. A menu that doesn't scroll
 * draws as it always has: the offscreen layer the fade needs is only used while it can scroll.
 */
internal fun Modifier.menuFadingEdges(state: ScrollState, length: Dp = MenuFadeLength): Modifier =
    graphicsLayer {
        compositingStrategy = if (state.maxValue > 0) CompositingStrategy.Offscreen else CompositingStrategy.Auto
    }.drawWithContent {
        drawContent()
        if (state.maxValue <= 0) return@drawWithContent
        val max = length.toPx()
        fade(top = true, extent = state.value.toFloat().coerceAtMost(max))
        fade(top = false, extent = (state.maxValue - state.value).toFloat().coerceAtMost(max))
    }

private fun ContentDrawScope.fade(top: Boolean, extent: Float) {
    if (extent <= 0f) return
    val start = if (top) 0f else size.height - extent
    drawRect(
        brush = Brush.verticalGradient(
            colors = if (top) listOf(Color.Transparent, Color.Black) else listOf(Color.Black, Color.Transparent),
            startY = start,
            endY = start + extent,
        ),
        topLeft = Offset(0f, start),
        size = Size(size.width, extent),
        blendMode = BlendMode.DstIn,
    )
}

private val MenuFadeLength = 24.dp
