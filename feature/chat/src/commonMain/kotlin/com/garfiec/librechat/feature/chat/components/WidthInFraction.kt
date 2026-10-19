package com.garfiec.librechat.feature.chat.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt

/**
 * Caps the width at [max] of the incoming max width. On an unweighted Row child that is the space
 * the Row has left for it, so a trailing badge or value can never take more than that share from
 * its weighted sibling, at any screen width. Text that fits within the cap keeps its natural width.
 */
internal fun Modifier.widthInFraction(max: Float): Modifier = layout { measurable, constraints ->
    val capped = if (constraints.hasBoundedWidth) {
        val maxWidth = (constraints.maxWidth * max).roundToInt()
        constraints.copy(minWidth = minOf(constraints.minWidth, maxWidth), maxWidth = maxWidth)
    } else {
        constraints
    }
    val placeable = measurable.measure(capped)
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}
