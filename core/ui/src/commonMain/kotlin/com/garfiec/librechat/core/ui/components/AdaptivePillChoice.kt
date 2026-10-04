package com.garfiec.librechat.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.glass.LiquidSegmentedControl
import com.garfiec.librechat.core.ui.theme.isLiquidGlass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * A single-choice row of text segments with a thumb that slides between them: the sliding pill of
 * the drawer's Chats/Projects toggle in Material, the iOS segmented control in Liquid Glass. Tapping
 * a segment selects it; dragging along the track scrubs the thumb, and the release settles on the
 * nearest segment (or the flung one). It fills its width, splitting it evenly between [options].
 */
@Composable
fun AdaptivePillChoice(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isLiquidGlass) {
        LiquidSegmentedControl(options, selectedIndex, onSelect, modifier)
        return
    }
    val count = options.size.coerceAtLeast(1)
    val scope = rememberCoroutineScope()
    val thumb = remember(scope) { PillThumb(scope, selectedIndex.coerceAtLeast(0)) }
    LaunchedEffect(thumb, selectedIndex) {
        if (!thumb.dragging && selectedIndex in options.indices && thumb.target != selectedIndex) {
            thumb.settle(selectedIndex)
        }
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val density = LocalDensity.current
    val flingPx = with(density) { FlingVelocity.toPx() }
    val currentSelected by rememberUpdatedState(selectedIndex)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val latestRtl by rememberUpdatedState(rtl)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, TrackShape)
            .padding(TrackPadding)
            .height(CellHeight),
    ) {
        val cellWidth = maxWidth / count
        val cellPx = with(density) { cellWidth.toPx() }
        val last = (count - 1).toFloat()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .pointerInput(thumb, count, cellPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var start = 0f
                        var slop = 0f
                        var at = 0f
                        val direction = if (latestRtl) -1f else 1f
                        val tracker = VelocityTracker()
                        tracker.addPointerInputChange(down)
                        var dragging = false
                        var settled = false
                        try {
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                tracker.addPointerInputChange(change)
                                if (!change.pressed) break
                                val dx = (change.position.x - down.position.x) * direction
                                if (!dragging) {
                                    if (abs(dx) <= viewConfiguration.touchSlop) continue
                                    dragging = true
                                    // Taken here, not at touch-down: a tap's settle may still be moving it.
                                    start = thumb.position.value
                                    // Fixed at the claim: re-signing it as dx crosses zero would jump the thumb.
                                    slop = sign(dx) * viewConfiguration.touchSlop
                                    thumb.startDrag()
                                }
                                // Consumed so the segment's tap cancels, and nothing behind scrolls.
                                change.consume()
                                at = (start + (dx - slop) / cellPx).coerceIn(0f, last)
                                thumb.drag(at)
                            }
                            if (dragging) {
                                val velocity = tracker.calculateVelocity().x * direction
                                val index = when {
                                    velocity > flingPx -> ceil(at)
                                    velocity < -flingPx -> floor(at)
                                    else -> at.roundToInt().toFloat()
                                }.toInt()
                                thumb.settle(index, velocity / cellPx)
                                settled = true
                                if (index != currentSelected) currentOnSelect(index)
                            }
                        } finally {
                            // Cancelled mid-drag: never leave the thumb parked between segments.
                            if (dragging && !settled) thumb.settle(currentSelected)
                        }
                    }
                },
        ) {
            Box(
                modifier = Modifier
                    .width(cellWidth)
                    .fillMaxHeight()
                    // offset {} already mirrors x in RTL; negating it here would flip it twice.
                    .offset { IntOffset((cellPx * thumb.position.value).roundToInt(), 0) }
                    .background(MaterialTheme.colorScheme.secondaryContainer, ThumbShape),
            )
            Row(modifier = Modifier.selectableGroup()) {
                options.forEachIndexed { index, label ->
                    val onThumb = (1f - abs(thumb.position.value - index)).coerceIn(0f, 1f)
                    PillSegment(
                        label = label,
                        selected = index == selectedIndex,
                        onThumb = onThumb,
                        onClick = {
                            thumb.settle(index)
                            if (index != selectedIndex) onSelect(index)
                        },
                        modifier = Modifier.width(cellWidth),
                    )
                }
            }
        }
    }
}

/** One label of [AdaptivePillChoice]; its colour follows how much of the thumb is under it. */
@Composable
private fun PillSegment(
    label: String,
    selected: Boolean,
    onThumb: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = lerp(
        MaterialTheme.colorScheme.onSurfaceVariant,
        MaterialTheme.colorScheme.onSecondaryContainer,
        onThumb,
    )
    Box(
        modifier = modifier
            .fillMaxHeight()
            // No ripple: the thumb is the feedback, and a ripple would flash under a drag's start.
            .selectable(
                selected = selected,
                interactionSource = null,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = color,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Where the thumb is, in segments: 0 under the first, 1 under the second, and between while it moves. */
@Stable
private class PillThumb(private val scope: CoroutineScope, index: Int) {
    val position = Animatable(index.toFloat())

    var target = index
        private set

    var dragging = false
        private set

    fun startDrag() {
        dragging = true
    }

    fun drag(to: Float) {
        scope.launch { position.snapTo(to) }
    }

    /** Springs to [index]; [velocity] is in segments per second. */
    fun settle(index: Int, velocity: Float = 0f) {
        dragging = false
        target = index
        scope.launch { position.animateTo(index.toFloat(), SettleSpring, velocity) }
    }
}

private val SettleSpring =
    spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

// The thumb sits one track padding inside the track, so its corners are the track's less that padding.
private val TrackShape = RoundedCornerShape(12.dp)
private val ThumbShape = RoundedCornerShape(8.dp)
private val TrackPadding = 4.dp
private val CellHeight = 36.dp

// A release this fast picks the segment it's heading to, however far the thumb got.
private val FlingVelocity = 400.dp
