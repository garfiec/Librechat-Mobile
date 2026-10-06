package com.garfiec.librechat.core.ui.contextusage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.usage.VisibleBreakdown
import com.garfiec.librechat.core.model.usage.percentOf
import com.garfiec.librechat.core.ui.theme.SeriesColors

/**
 * The context window as one bar: a segment per visible breakdown row, in its series colour and
 * in row order, over the track that stands for free space. Deferred tools are hatched. Without
 * rows (Simple, or an estimate) it draws a single fill, tinted by pressure. Mirrors web's
 * `SegmentedMeter`.
 */
@Composable
fun SegmentedMeter(visible: VisibleBreakdown, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val view = visible.view
    val pieces = if (visible.rows.isNotEmpty()) {
        visible.rows.filter { it.value > 0 }.map {
            MeterPiece(it.value, SeriesColors.slot(it.category.slot), it.category.hatched)
        }
    } else {
        val fill = pressureColor(percentOf(view.usedTokens, view.windowTokens)) ?: SeriesColors.slot(1)
        listOf(MeterPiece(view.usedTokens, fill, hatched = false))
    }
    val window = view.windowTokens
    Canvas(modifier.height(METER_HEIGHT).clip(METER_SHAPE)) {
        drawRect(track)
        if (window <= 0) return@Canvas
        val gap = SEGMENT_GAP.toPx()
        var x = 0f
        for (piece in pieces) {
            if (x >= size.width) break
            val width = (piece.value.toFloat() / window * size.width).coerceAtMost(size.width - x)
            if (width <= 0f) continue
            drawPiece(piece, x, width, track)
            x += width + gap
        }
    }
}

private fun DrawScope.drawPiece(piece: MeterPiece, x: Float, width: Float, track: Color) {
    drawRect(piece.color, topLeft = Offset(x, 0f), size = Size(width, size.height))
    if (!piece.hatched) return
    // Same hue, held out of the active set: diagonal stripes in the track colour.
    val stride = HATCH_STRIDE.toPx()
    clipRect(left = x, right = x + width) {
        var start = x - size.height
        while (start < x + width) {
            drawLine(track, Offset(start, size.height), Offset(start + size.height, 0f), strokeWidth = HATCH_STROKE.toPx())
            start += stride
        }
    }
}

/** A legend key; matches its meter segment, hatched included. */
@Composable
internal fun SwatchDot(color: Color, hatched: Boolean) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(
        Modifier
            .size(SWATCH)
            .clip(SWATCH_SHAPE)
            .drawBehind {
                drawRoundRect(color, cornerRadius = CornerRadius(2.dp.toPx()))
                if (hatched) drawPiece(MeterPiece(0, color, hatched = true), 0f, size.width, track)
            },
    )
}

private class MeterPiece(val value: Int, val color: Color, val hatched: Boolean)

internal val SWATCH = 8.dp
internal val SWATCH_SHAPE = RoundedCornerShape(2.dp)
private val METER_HEIGHT = 8.dp
private val METER_SHAPE = RoundedCornerShape(4.dp)
private val SEGMENT_GAP = 2.dp
private val HATCH_STRIDE = 4.dp
private val HATCH_STROKE = 1.5.dp
