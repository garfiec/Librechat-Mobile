package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val LabelDotSize = 8.dp
private val LabelDotGap = 6.dp

/** Width a leading dot takes from a label's share, so choice-fit measurement leaves room for it. */
internal fun labelDotReserve(dotColors: List<Color>?): Dp =
    if (dotColors.isNullOrEmpty()) 0.dp else LabelDotSize + LabelDotGap

/** A tab or segment label led by a small coloured dot; just the label when [dotColor] is null. */
@Composable
internal fun LabelWithDot(dotColor: Color?, label: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (dotColor != null) {
            Box(
                Modifier
                    .size(LabelDotSize)
                    .background(dotColor, CircleShape),
            )
            Spacer(Modifier.width(LabelDotGap))
        }
        // No weight: the Row measures the dot first and hands the label what is left, so a long title
        // still ellipsizes. A weighted label measures zero wide under the unbounded intrinsic pass M3
        // tab rows size their indicator from, which would shrink the underline to the dot.
        label()
    }
}
