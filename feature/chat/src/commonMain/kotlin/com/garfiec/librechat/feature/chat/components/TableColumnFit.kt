package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

/** The narrowest a table column is squeezed to (cell padding included) before the table pans instead. */
internal val TABLE_COLUMN_FLOOR = 120.dp

/** The widest a column of a panning table grows (cell padding included) before its text wraps. */
internal val TABLE_COLUMN_PAN_CAP = 320.dp

/**
 * Column widths for a markdown table that must fit [available] pixels, after the browser's
 * automatic table layout (CSS 2.1 §17.5.2.2).
 *
 * Each column has a [preferred] width (its widest cell on one line) and a [minimum] (its widest
 * unbreakable word). A table whose preferred widths fit keeps them. Otherwise every column gets its
 * minimum plus a share of the remaining space proportional to how much it wants to grow, so a
 * column of long sentences wraps before a column of short labels does.
 *
 * Unlike a browser, a table whose minimums still overflow keeps shrinking, down to [floor] per
 * column, and lets its longest words break: the measured minimum treats a URL as one word, so
 * honouring it would scroll most tables that cite a source. When the floors overflow too — many
 * columns — squeezing further would leave every column unreadable, so the table gives up on fitting
 * and pans instead, at [pannedTableColumns] widths.
 */
internal fun fitTableColumns(
    preferred: List<Int>,
    minimum: List<Int>,
    available: Int,
    floor: Int = 0,
    panCap: Int = Int.MAX_VALUE,
): List<Int> {
    // No column is squeezed below the floor unless its whole content is narrower than that.
    val floors = preferred.map { it.coerceAtMost(floor) }
    val minimums = minimum.zip(preferred) { min, pref -> min.coerceAtMost(pref) }
        .zip(floors) { min, low -> maxOf(min, low) }
    return when {
        minimums.sum() <= available -> interpolate(lower = minimums, upper = preferred, available)
        floors.sum() <= available -> interpolate(lower = floors, upper = minimums, available)
        else -> pannedTableColumns(preferred, panCap)
    }
}

/**
 * Column widths for a table that pans rather than fits: each column on one line up to [cap], wrapping
 * past it, so a long-sentence column stays a readable paragraph instead of one line meters wide.
 */
internal fun pannedTableColumns(preferred: List<Int>, cap: Int): List<Int> = preferred.map { it.coerceAtMost(cap) }

/**
 * [upper] when it fits, [lower] when even that overflows, and in between each column gets [lower]
 * plus a share of the slack proportional to its own `upper - lower`.
 */
private fun interpolate(lower: List<Int>, upper: List<Int>, available: Int): List<Int> {
    val upperTotal = upper.sum()
    val lowerTotal = lower.sum()
    if (upperTotal <= available) return upper
    if (lowerTotal >= available) return lower

    val slack = (available - lowerTotal).toLong()
    val growth = (upperTotal - lowerTotal).toLong()
    return lower.zip(upper) { low, up -> low + ((up - low) * slack / growth).toInt() }
}

/**
 * The width of each column of a table laid out in [maxWidthPx], by [fitTableColumns], or by
 * [pannedTableColumns] when [pan] (the fullscreen view, which always pans). Cells are [cellPaddingH]
 * in from each side, and columns are separated by 1dp dividers.
 */
@Composable
internal fun rememberTableColumnWidths(
    cells: RenderedTableCells,
    headerStyle: TextStyle,
    bodyStyle: TextStyle,
    cellPaddingH: Dp,
    maxWidthPx: Int,
    pan: Boolean = false,
): List<Dp> {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val columnCount = cells.headers.size

    // Per column, in px including padding: the widest cell on one line, and its widest unbreakable word.
    val extents = remember(cells, headerStyle, bodyStyle, cellPaddingH, textMeasurer, density) {
        // Padding rounds each side on its own; rounding the sum can reserve a pixel less than the cell
        // gives up, and the widest cell then wraps its last word.
        val cellPaddingPx = with(density) { cellPaddingH.roundToPx() } * 2
        List(columnCount) { col ->
            var preferred = 0f
            var minimum = 0f
            val column = listOf(cells.headers[col] to headerStyle) +
                cells.rows.mapNotNull { row -> row.getOrNull(col)?.let { it to bodyStyle } }
            for ((text, style) in column) {
                val intrinsics = textMeasurer.measure(text = text, style = style).multiParagraph.intrinsics
                preferred = maxOf(preferred, intrinsics.maxIntrinsicWidth)
                minimum = maxOf(minimum, intrinsics.minIntrinsicWidth)
            }
            (ceil(preferred).toInt() + cellPaddingPx) to (ceil(minimum).toInt() + cellPaddingPx)
        }
    }

    return remember(extents, maxWidthPx, density, pan) {
        with(density) {
            val preferred = extents.map { it.first }
            val panCap = TABLE_COLUMN_PAN_CAP.roundToPx()
            val widths = if (pan) {
                pannedTableColumns(preferred, panCap)
            } else {
                val dividersPx = (columnCount - 1).coerceAtLeast(0) * 1.dp.roundToPx()
                fitTableColumns(
                    preferred = preferred,
                    minimum = extents.map { it.second },
                    available = maxWidthPx - dividersPx,
                    floor = TABLE_COLUMN_FLOOR.roundToPx(),
                    panCap = panCap,
                )
            }
            widths.map { it.toDp() }
        }
    }
}
