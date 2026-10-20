package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp

/**
 * Two columns, as wide as they come, or one when two would be narrower than [minColumnWidth]: a
 * small phone at a large font scale can't fit a card's name and description in half the width.
 */
class UpToTwoColumns(private val minColumnWidth: Dp) : GridCells {
    override fun Density.calculateCrossAxisCellSizes(availableSize: Int, spacing: Int): List<Int> {
        val count = if ((availableSize - spacing) / 2 >= minColumnWidth.roundToPx()) 2 else 1
        // Split as GridCells.Fixed does: the remainder pixels go to the first columns.
        val usable = availableSize - spacing * (count - 1)
        return List(count) { usable / count + if (it < usable % count) 1 else 0 }
    }

    override fun equals(other: Any?): Boolean = other is UpToTwoColumns && other.minColumnWidth == minColumnWidth

    override fun hashCode(): Int = minColumnWidth.hashCode()
}
