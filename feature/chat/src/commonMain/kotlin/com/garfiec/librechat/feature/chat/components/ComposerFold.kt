package com.garfiec.librechat.feature.chat.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.util.lerp
import kotlin.math.roundToInt

/** Whether the composer stays open while idle: it holds something the user is still working on. */
@Suppress("LongParameterList")
internal fun composerHoldsOpen(
    inputText: String,
    hasDraftItems: Boolean,
    arePicksUnsettled: Boolean,
    showsWorkspaceNotice: Boolean,
    isRecording: Boolean,
    isTranscribing: Boolean,
    isEditingQueued: Boolean,
): Boolean = inputText.isNotEmpty() || hasDraftItems || arePicksUnsettled || showsWorkspaceNotice ||
    isRecording || isTranscribing || isEditingQueued

/** Whether the keyboard is up or on its way up. Changes only when a keyboard animation starts or ends. */
@Composable
internal expect fun keyboardRising(): Boolean

/** How open the composer is, 0 folded to 1 open. Read it in layout or drawing, not in composition. */
@Composable
internal fun rememberComposerOpenness(canFold: Boolean, holdsOpen: Boolean, focused: Boolean): () -> Float {
    val open = !canFold || holdsOpen || (focused && keyboardRising())
    val openness = animateFloatAsState(if (open) 1f else 0f, FoldSpec, label = "composerFold")
    return remember(openness) { { openness.value } }
}

private val FoldSpec = tween<Float>(durationMillis = 300, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))

/**
 * The text field over the button row. Folded, the two share one line: the field sits between the
 * leading and trailing buttons, and the settings line fades out under it. [trailing] narrows itself
 * with [foldSlot].
 */
@Composable
internal fun FoldingComposerRows(
    openness: () -> Float,
    field: @Composable () -> Unit,
    leading: @Composable () -> Unit,
    settings: @Composable () -> Unit,
    trailing: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(contents = listOf(leading, settings, trailing, field), modifier = modifier) { slots, constraints ->
        val open = openness().coerceIn(0f, 1f)
        val width = constraints.maxWidth
        val loose = Constraints(maxWidth = width, maxHeight = constraints.maxHeight)
        val lead = slots[0].single().measure(loose)
        val trail = slots[2].single().measure(loose)
        val between = (width - lead.width - trail.width).coerceAtLeast(0)
        val line = slots[1].single().measure(loose.copy(minWidth = between, maxWidth = between))
        val rowHeight = maxOf(lead.height, trail.height, line.height)
        val fieldWidth = lerp(between, width, open)
        val text = slots[3].single().measure(loose.copy(minWidth = fieldWidth, maxWidth = fieldWidth))
        val foldedHeight = maxOf(text.height, rowHeight)
        val height = lerp(foldedHeight, text.height + rowHeight, open)
        layout(width, height) {
            val rowY = lerp((foldedHeight - rowHeight) / 2, text.height, open)
            lead.place(0, rowY + (rowHeight - lead.height) / 2)
            line.placeWithLayer(lead.width, rowY + (rowHeight - line.height) / 2) { alpha = open }
            trail.place(width - trail.width, rowY + (rowHeight - trail.height) / 2)
            // Last, so the folded field takes the taps over the faded settings line.
            text.place(lerp(lead.width, 0, open), lerp((foldedHeight - text.height) / 2, 0, open))
        }
    }
}

/** A trailing button's slot, narrowed to [shown] of its width and faded, so the buttons after it slide over. */
internal fun Modifier.foldSlot(shown: () -> Float): Modifier = graphicsLayer {
    // Only while narrowed: a clip also clips the button's touch target, which reaches past its 40dp.
    clip = shown() < 1f
}.layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val amount = shown().coerceIn(0f, 1f)
    layout((placeable.width * amount).roundToInt(), placeable.height) {
        placeable.placeWithLayer(0, 0) { alpha = amount }
    }
}
