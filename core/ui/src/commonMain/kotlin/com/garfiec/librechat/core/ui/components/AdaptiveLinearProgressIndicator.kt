package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.glass.GlassControlColors
import com.garfiec.librechat.core.ui.theme.isLiquidGlass

/**
 * A determinate bar: the M3 [LinearProgressIndicator] in Material; in Liquid Glass, an iOS progress
 * bar (a capsule track in `GlassControlColors.fill` with an accent capsule fill, unless the caller
 * picked colours). [progress] is clamped to 0..1, NaN reading as 0. [strokeCap], [gapSize] and
 * [drawStopIndicator] only apply in Material.
 */
// The gap and stop-indicator defaults are experimental in M3; the parameters mirror its signature.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveLinearProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = ProgressIndicatorDefaults.linearColor,
    trackColor: Color = ProgressIndicatorDefaults.linearTrackColor,
    strokeCap: StrokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
    gapSize: Dp = ProgressIndicatorDefaults.LinearIndicatorTrackGapSize,
    drawStopIndicator: DrawScope.() -> Unit = {
        ProgressIndicatorDefaults.drawStopIndicator(
            drawScope = this,
            stopSize = ProgressIndicatorDefaults.LinearTrackStopIndicatorSize,
            color = color,
            strokeCap = strokeCap,
        )
    },
) {
    if (!isLiquidGlass) {
        LinearProgressIndicator(
            progress = progress,
            modifier = modifier,
            color = color,
            trackColor = trackColor,
            strokeCap = strokeCap,
            gapSize = gapSize,
            drawStopIndicator = drawStopIndicator,
        )
        return
    }
    val fill = if (color == ProgressIndicatorDefaults.linearColor) GlassControlColors.tint else color
    val track = if (trackColor == ProgressIndicatorDefaults.linearTrackColor) GlassControlColors.fill else trackColor
    Canvas(
        modifier
            .progressSemantics(progressFraction(progress()))
            // Same footprint as the M3 bar, so a caller's width and layout carry over.
            .size(DefaultWidth, DefaultHeight)
            .clip(CircleShape),
    ) {
        drawRect(track)
        val fraction = progressFraction(progress())
        if (fraction <= 0f) return@Canvas
        val width = size.width * fraction
        val left = if (layoutDirection == LayoutDirection.Rtl) size.width - width else 0f
        drawRoundRect(
            color = fill,
            topLeft = Offset(left, 0f),
            size = Size(width, size.height),
            cornerRadius = CornerRadius(size.height / 2),
        )
    }
}

/** [raw] as a fraction the bar and its semantics can take: within 0..1, NaN as 0. */
internal fun progressFraction(raw: Float): Float = if (raw.isNaN()) 0f else raw.coerceIn(0f, 1f)

private val DefaultWidth = 240.dp
private val DefaultHeight = 4.dp
