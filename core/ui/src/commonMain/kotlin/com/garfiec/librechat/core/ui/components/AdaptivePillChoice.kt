package com.garfiec.librechat.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
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
import kotlin.math.round
import kotlin.math.roundToInt

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

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, TrackShape)
            .padding(TrackPadding)
            .height(CellHeight),
    ) {
        val cellWidth = maxWidth / count
        val cellPx = with(density) { cellWidth.toPx() }
        val last = count - 1
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .draggable(
                    state = rememberDraggableState { delta -> thumb.dragBy(delta / cellPx, last) },
                    orientation = Orientation.Horizontal,
                    reverseDirection = rtl,
                    onDragStarted = { thumb.startDrag() },
                    onDragStopped = { velocity ->
                        val index = thumb.release(velocity / cellPx, flingPx / cellPx, last)
                        if (index != selectedIndex) onSelect(index)
                    },
                ),
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

    // Where the drag has carried the thumb. Kept apart from [position], whose snaps land a frame later.
    private var dragAt = 0f

    fun startDrag() {
        dragging = true
        dragAt = position.value
    }

    fun dragBy(segments: Float, last: Int) {
        dragAt = (dragAt + segments).coerceIn(0f, last.toFloat())
        scope.launch { position.snapTo(dragAt) }
    }

    /** Settles a drag on the segment [pillReleaseIndex] picks, and returns it; velocities are in segments per second. */
    fun release(velocity: Float, flingVelocity: Float, last: Int): Int =
        pillReleaseIndex(dragAt, velocity, flingVelocity, last).also { settle(it, velocity) }

    /** Springs to [index]; [velocity] is in segments per second. */
    fun settle(index: Int, velocity: Float = 0f) {
        dragging = false
        target = index
        scope.launch { position.animateTo(index.toFloat(), SettleSpring, velocity) }
    }
}

/** The segment a drag released at [at] settles on: the one it was flung toward, else the nearest. */
internal fun pillReleaseIndex(at: Float, velocity: Float, flingVelocity: Float, last: Int): Int = when {
    velocity > flingVelocity -> ceil(at)
    velocity < -flingVelocity -> floor(at)
    else -> round(at)
}.toInt().coerceIn(0, last)

private val SettleSpring =
    spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

// The thumb sits one track padding inside the track, so its corners are the track's less that padding.
private val TrackShape = RoundedCornerShape(12.dp)
private val ThumbShape = RoundedCornerShape(8.dp)
private val TrackPadding = 4.dp
private val CellHeight = 36.dp

// A release this fast picks the segment it's heading to, however far the thumb got.
private val FlingVelocity = 400.dp
