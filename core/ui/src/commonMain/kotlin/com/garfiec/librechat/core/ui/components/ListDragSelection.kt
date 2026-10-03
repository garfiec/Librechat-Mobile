package com.garfiec.librechat.core.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/**
 * Press-drag-release across a list's rows, in place: press a row and drag up or down, and the
 * highlight springs from row to row under the finger; lifting over a row selects it, lifting off
 * every row does nothing. Taps are left to the rows' own clicks. The highlight is also the rows'
 * pressed state: it shows on the touched row at once and fades if the press ends as a tap or a
 * swipe, so a row applying [listDragTarget] should keep its click's press ripple off (its focus and
 * hover indication can stay).
 *
 * Only a vertical drag is taken, so it can sit inside a horizontal swipe (a drawer): whichever axis
 * passes touch slop first wins. Holding past the long-press timeout takes the gesture whatever the
 * direction. Gestures a child consumes first (its own drag) are left to it.
 *
 * The container applies [listDragSelection]; each selectable row applies [listDragTarget], and its
 * leading icon [dragSelectRowIcon].
 */
@Stable
class ListDragSelection internal constructor(
    private val haptics: HapticFeedback,
    internal val highlight: DragHighlight,
) {
    internal var container: LayoutCoordinates? = null
    internal var color by mutableStateOf(Color.Unspecified)

    internal fun held() = haptics.performHapticFeedback(HapticFeedbackType.LongPress)
}

@Composable
fun rememberListDragSelection(): ListDragSelection {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = DRAG_HIGHLIGHT_ALPHA)
    return remember(scope, haptics) { ListDragSelection(haptics, DragHighlight(scope, haptics)) }
        .also { it.color = color }
}

private enum class Claim { Tap, Hold, Drag, Yield }

/** On the layout holding the rows; draws the highlight behind them. With [state] null it does nothing. */
fun Modifier.listDragSelection(state: ListDragSelection?): Modifier =
    if (state == null) this else dragSelectGesture(state)

private fun Modifier.dragSelectGesture(state: ListDragSelection): Modifier =
    onPlaced { state.container = it }
        .drawBehind { drawHighlight(state.highlight, state.container, state.color) }
        .pointerInput(state) {
            val slop = viewConfiguration.touchSlop
            val holdMillis = viewConfiguration.longPressTimeoutMillis
            val highlight = state.highlight
            fun toScreen(local: Offset): Offset? =
                state.container?.takeIf { it.isAttached }?.localToScreen(local)?.takeIf { it.isSpecified }

            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val start = toScreen(down.position)?.let(highlight::targetAt) ?: return@awaitEachGesture
                highlight.reset()
                // The pressed state, before the gesture is known to be a drag-select.
                highlight.hover(start, tick = false)

                // Main pass, so a child's own drag (the account avatar's swipe) consumes first and
                // wins, and this runs before the drawer's horizontal drag above it.
                val claim = withTimeoutOrNull(holdMillis) {
                    var result: Claim? = null
                    while (result == null) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                        val delta = change?.let { it.position - down.position } ?: Offset.Zero
                        result = when {
                            change == null || change.isConsumed -> Claim.Yield
                            !change.pressed -> Claim.Tap
                            abs(delta.y) > slop && abs(delta.y) > abs(delta.x) -> {
                                change.consume()
                                Claim.Drag
                            }
                            abs(delta.x) > slop -> Claim.Yield
                            else -> null
                        }
                    }
                    result
                } ?: Claim.Hold

                if (claim == Claim.Hold || claim == Claim.Drag) {
                    if (claim == Claim.Hold) state.held()
                    // A hold released without moving is the row's own click, already on its way.
                    var dragging = claim == Claim.Drag
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            if (dragging && highlight.select() != null) change.consume()
                            break
                        }
                        if (!dragging && (change.position - down.position).getDistance() > slop) dragging = true
                        if (dragging) {
                            highlight.hover(toScreen(change.position)?.let(highlight::targetAt))
                            change.consume()
                        }
                    }
                }
                highlight.hover(null)
            }
        }

/** On a selectable row inside [listDragSelection]: a drag that ends over it calls [onSelect]. */
fun Modifier.listDragTarget(state: ListDragSelection?, onSelect: () -> Unit): Modifier =
    dragHighlightTarget(state?.highlight, onSelect)
