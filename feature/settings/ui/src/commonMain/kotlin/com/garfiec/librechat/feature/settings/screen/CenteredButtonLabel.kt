package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

/**
 * A full-width button's content with [label] centred, as the plain buttons beside it are, and
 * [trailing] (a count, a chevron) pinned to the end. The trailing width is reserved on both sides,
 * so the label stays centred on the button and a long translation wraps before it reaches the end.
 */
@Composable
internal fun CenteredButtonLabel(
    label: @Composable () -> Unit,
    trailing: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(
        contents = listOf(label, trailing),
        modifier = modifier.fillMaxWidth(),
    ) { (labelMeasurables, trailingMeasurables), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val trailingPlaceables = trailingMeasurables.map { it.measure(loose) }
        val trailingWidth = trailingPlaceables.maxOfOrNull { it.width } ?: 0
        val reserved = if (trailingWidth > 0) trailingWidth + TrailingGap.roundToPx() else 0
        val labelMaxWidth = if (constraints.hasBoundedWidth) {
            (constraints.maxWidth - 2 * reserved).coerceAtLeast(0)
        } else {
            Constraints.Infinity
        }
        val labelPlaceables = labelMeasurables.map { it.measure(loose.copy(maxWidth = labelMaxWidth)) }
        val labelWidth = labelPlaceables.maxOfOrNull { it.width } ?: 0

        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else labelWidth + 2 * reserved
        val contentHeight = maxOf(
            labelPlaceables.maxOfOrNull { it.height } ?: 0,
            trailingPlaceables.maxOfOrNull { it.height } ?: 0,
        )
        val height = contentHeight.coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            labelPlaceables.forEach { it.placeRelative((width - it.width) / 2, (height - it.height) / 2) }
            trailingPlaceables.forEach { it.placeRelative(width - it.width, (height - it.height) / 2) }
        }
    }
}

private val TrailingGap = 8.dp
