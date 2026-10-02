package com.garfiec.librechat.core.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateValue
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.progressSemantics
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.theme.isLiquidGlass

/**
 * In Liquid Glass, the iOS activity indicator (grey unless the caller picked a colour).
 * [strokeWidth], [trackColor] and [strokeCap] only apply in Material.
 */
@Composable
fun AdaptiveCircularProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = ProgressIndicatorDefaults.circularColor,
    strokeWidth: Dp = ProgressIndicatorDefaults.CircularStrokeWidth,
    trackColor: Color = ProgressIndicatorDefaults.circularIndeterminateTrackColor,
    strokeCap: StrokeCap = ProgressIndicatorDefaults.CircularIndeterminateStrokeCap,
) {
    if (!isLiquidGlass) {
        CircularProgressIndicator(
            modifier = modifier,
            color = color,
            strokeWidth = strokeWidth,
            trackColor = trackColor,
            strokeCap = strokeCap,
        )
        return
    }
    val spokeColor = if (color == ProgressIndicatorDefaults.circularColor) MaterialTheme.colorScheme.onSurfaceVariant else color
    val step = rememberInfiniteTransition(label = "activity").animateValue(
        initialValue = 0,
        targetValue = SPOKES,
        typeConverter = Int.VectorConverter,
        animationSpec = infiniteRepeatable(tween(durationMillis = CYCLE_MS, easing = LinearEasing)),
        label = "activityStep",
    )
    Canvas(modifier.progressSemantics().size(DefaultSize)) {
        val radius = size.minDimension / 2
        val width = size.minDimension * SPOKE_WIDTH
        // Read in the draw phase only, so a step redraws without recomposing.
        val lead = step.value
        for (i in 0 until SPOKES) {
            val age = (lead - i + SPOKES) % SPOKES
            rotate(degrees = i * 360f / SPOKES) {
                drawLine(
                    color = spokeColor.copy(alpha = 1f - age * FADE_PER_SPOKE),
                    start = Offset(center.x, center.y - radius * SPOKE_INNER),
                    end = Offset(center.x, center.y - radius + width / 2),
                    strokeWidth = width,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

/** M3 in both styles: iOS shows determinate progress as a ring too. */
@Composable
fun AdaptiveCircularProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = ProgressIndicatorDefaults.circularColor,
    strokeWidth: Dp = ProgressIndicatorDefaults.CircularStrokeWidth,
    trackColor: Color = ProgressIndicatorDefaults.circularDeterminateTrackColor,
    strokeCap: StrokeCap = ProgressIndicatorDefaults.CircularDeterminateStrokeCap,
) {
    CircularProgressIndicator(
        progress = progress,
        modifier = modifier,
        color = color,
        strokeWidth = strokeWidth,
        trackColor = trackColor,
        strokeCap = strokeCap,
    )
}

private const val SPOKES = 8
private const val CYCLE_MS = 800
private const val SPOKE_WIDTH = 0.09f
private const val SPOKE_INNER = 0.45f
private const val FADE_PER_SPOKE = 0.1f
private val DefaultSize = 40.dp
