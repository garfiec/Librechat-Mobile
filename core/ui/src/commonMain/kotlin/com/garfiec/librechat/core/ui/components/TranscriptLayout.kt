package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/*
 * TCHAT: transcript measures from the design system (design/system/components/Message):
 * the reading column caps at `size-reading`, the user's bubble at 80% of the row, and the bubble
 * has a `radius-xs` tail corner on the sender's side.
 */

/** `size-reading`: the widest the transcript and settings content get on tablets. */
val ReadingMaxWidth: Dp = 720.dp

/** Fills the width up to [ReadingMaxWidth], centred, so a tablet never stretches the phone layout. */
fun Modifier.readingWidth(): Modifier = fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = ReadingMaxWidth)

/** Caps the width at [fraction] of what the parent offers, without forcing a minimum. */
fun Modifier.maxWidthFraction(fraction: Float): Modifier = layout { measurable, constraints ->
    val max = if (constraints.hasBoundedWidth) (constraints.maxWidth * fraction).roundToInt() else constraints.maxWidth
    val placeable = measurable.measure(constraints.copy(minWidth = minOf(constraints.minWidth, max), maxWidth = max))
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

/** The user's bubble is at most this share of the row. */
const val USER_BUBBLE_MAX_FRACTION: Float = 0.8f

/** [BubbleShape] with the `radius-xs` tail at the bottom end, the sender's side. */
val UserBubbleShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 4.dp, bottomStart = 20.dp)
