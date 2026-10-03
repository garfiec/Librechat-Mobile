package com.garfiec.librechat.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Press-drag-release on a menu's opener, iOS style. A tap opens the menu on release. Holding swells
 * the opener until the long-press timeout, then pops the menu open; dragging opens it at once.
 * With the menu open, a highlight springs between rows under the finger, the hovered row lifts, and
 * the panel leans toward the finger and stretches past its far edge. Lifting over a row selects it
 * (the menu folds into that row); lifting well away from the menu cancels it.
 *
 * The opener applies [menuDragAnchor] (and [menuDragPressEffect] on what should swell), the
 * menu takes the state through `AdaptiveDropdownMenu(dragSelection = …)`, and each selectable row
 * applies [menuDragTarget]. They meet in screen coordinates: the whole gesture stays with the opener
 * that took the down (and the popup fallback is a separate window), so rows are hit-tested here.
 */
@Stable
class MenuDragSelection internal constructor(
    private val scope: CoroutineScope,
    private val haptics: HapticFeedback,
) {
    internal var anchor: LayoutCoordinates? = null

    /** The menu panel's untransformed frame; set by the menu while it is open. */
    internal var panel: LayoutCoordinates? = null
    internal val highlight = DragHighlight(scope, haptics)

    /** The finger in screen coordinates while dragging, else null. */
    internal var finger by mutableStateOf<Offset?>(null)
        private set

    /** The finger is far enough from the menu that lifting it cancels. */
    internal var cancelArmed by mutableStateOf(false)
        private set

    /** The row a drag selected, in screen coordinates; the closing menu folds into it. */
    internal var selected by mutableStateOf<Rect?>(null)
        private set

    /** The opener's swell: 1 fully grown, 0 at rest; [PopSpec] overshoots below 0 as it settles. */
    internal val press = Animatable(0f)

    /**
     * The menu is open — however it was opened. The opener is never swollen or leaning while it
     * is: it springs back to size as the menu opens ([popBack]).
     */
    internal var menuOpen by mutableStateOf(false)
        private set

    /** This open came from a hold or drag ([opened]) rather than the opener's own tap. */
    private var openedByGesture = false

    internal fun menuOpenChanged(open: Boolean) {
        if (open == menuOpen) return
        menuOpen = open
        if (!open) {
            openedByGesture = false
            return
        }
        scope.launch { pull.animateTo(Offset.Zero, SettleSpec) }
        if (!openedByGesture) popBack()
    }

    /**
     * Springs the opener back to size, growing it first if a quick tap or early drag beat the swell,
     * so the bounce reads.
     */
    private fun popBack() {
        scope.launch {
            if (press.value < DIP_THRESHOLD) press.animateTo(1f, DipSpec)
            press.animateTo(0f, PopSpec)
        }
    }

    /** The opener's lean toward the finger, in px. */
    internal val pull = Animatable(Offset.Zero, Offset.VectorConverter)

    /** Clears the previous gesture's leftovers; the menu calls this as it opens. */
    internal fun reset() {
        selected = null
        highlight.reset()
    }

    internal fun pressStart(holdMillis: Long) {
        if (menuOpen) return
        scope.launch { press.animateTo(1f, tween(holdMillis.toInt(), easing = LinearOutSlowInEasing)) }
    }

    internal fun opened(byHold: Boolean) {
        openedByGesture = true
        if (byHold) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch { pull.animateTo(Offset.Zero, SettleSpec) }
        popBack()
    }

    internal fun lean(target: Offset) {
        if (openedByGesture || menuOpen) return
        scope.launch { pull.animateTo(target, TrackSpec) }
    }

    internal fun track(localToAnchor: Offset, cancelDistance: Float) {
        val onScreen = anchor?.takeIf { it.isAttached }?.localToScreen(localToAnchor) ?: return
        if (!onScreen.isSpecified) return
        finger = onScreen
        val frame = panel?.screenBounds()
        val armed = frame != null &&
            frame.distanceTo(onScreen) > cancelDistance &&
            anchor?.screenBounds()?.contains(onScreen) != true
        if (armed != cancelArmed) {
            cancelArmed = armed
            if (armed) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
        }
        highlight.hover(if (armed) null else highlight.targetAt(onScreen))
    }

    /** Ends a drag over a row: selects it and returns true. */
    internal fun release(): Boolean {
        selected = highlight.select() ?: return false
        return true
    }

    internal fun finish() {
        finger = null
        cancelArmed = false
        if (selected == null) highlight.hover(null)
        // A hold or drag already started the opener's bounce; letting go mustn't restart it.
        if (openedByGesture) return
        scope.launch { press.animateTo(0f, PopSpec) }
        scope.launch { pull.animateTo(Offset.Zero, SettleSpec) }
    }
}

@Composable
fun rememberMenuDragSelection(): MenuDragSelection {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    return remember(scope, haptics) { MenuDragSelection(scope, haptics) }
}

private enum class HoldOutcome { Tap, Hold, Drag, Cancel }

/**
 * On the opener (the layout the menu anchors to). [onOpen] opens the menu on a hold or a drag; a tap
 * is left to the opener's own click. Watches in the initial pass without consuming the down; once
 * the finger drags past touch slop the moves are consumed, which cancels that click. [onCancel]
 * closes the menu when a drag is released far from it.
 */
fun Modifier.menuDragAnchor(
    state: MenuDragSelection,
    enabled: Boolean = true,
    onOpen: () -> Unit,
    onCancel: () -> Unit,
): Modifier {
    if (!enabled) return this
    return onPlaced { state.anchor = it }.pointerInput(state) {
        val slop = viewConfiguration.touchSlop
        val holdMillis = viewConfiguration.longPressTimeoutMillis
        val leanCap = LeanCap.toPx()
        val cancelDistance = CancelDistance.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val center = Offset(size.width / 2f, size.height / 2f)
            fun PointerInputChange.leanToward() = state.lean(rubberVector(position - center, leanCap, ANCHOR_LEAN_SOFTNESS))

            state.pressStart(holdMillis)
            val outcome = withTimeoutOrNull(holdMillis) {
                var result: HoldOutcome? = null
                while (result == null) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                    result = when {
                        change == null -> HoldOutcome.Cancel
                        !change.pressed -> HoldOutcome.Tap
                        (change.position - down.position).getDistance() > slop -> {
                            change.consume()
                            HoldOutcome.Drag
                        }
                        else -> {
                            change.leanToward()
                            null
                        }
                    }
                }
                result
            } ?: HoldOutcome.Hold

            if (outcome == HoldOutcome.Hold || outcome == HoldOutcome.Drag) {
                onOpen()
                state.opened(byHold = outcome == HoldOutcome.Hold)
                var dragging = outcome == HoldOutcome.Drag
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        if (dragging) {
                            val armed = state.cancelArmed
                            if (state.release()) {
                                change.consume()
                            } else if (armed) {
                                change.consume()
                                onCancel()
                            }
                        }
                        break
                    }
                    change.leanToward()
                    if (!dragging && (change.position - down.position).getDistance() > slop) dragging = true
                    if (dragging) {
                        state.track(change.position, cancelDistance)
                        change.consume()
                    }
                }
            }
            state.finish()
        }
    }
}

/** On the opener's visible chip: swells while held and leans toward the finger. */
fun Modifier.menuDragPressEffect(state: MenuDragSelection): Modifier = graphicsLayer {
    val scale = 1f + SWELL_DEPTH * state.press.value
    scaleX = scale
    scaleY = scale
    val pull = state.pull.value
    translationX = pull.x
    translationY = pull.y
}

/** On a selectable menu row: a drag that ends over it calls [onSelect]; it lifts while highlighted. */
fun Modifier.menuDragTarget(state: MenuDragSelection?, onSelect: () -> Unit): Modifier =
    dragHighlightTarget(state?.highlight, onSelect)

private fun Rect.distanceTo(point: Offset): Float {
    val dx = max(max(left - point.x, 0f), point.x - right)
    val dy = max(max(top - point.y, 0f), point.y - bottom)
    return sqrt(dx * dx + dy * dy)
}

/**
 * iOS-style rubber band: grows ~linearly for small [distance], approaches [cap] for large ones.
 * Lower [softness] needs more travel to reach the cap.
 */
internal fun rubberBand(distance: Float, cap: Float, softness: Float): Float =
    if (distance <= 0f) 0f else cap * (1f - 1f / (distance * softness / cap + 1f))

internal fun rubberVector(vector: Offset, cap: Float, softness: Float): Offset {
    val length = vector.getDistance()
    if (length == 0f) return Offset.Zero
    return vector * (rubberBand(length, cap, softness) / length)
}

private val LeanCap = 4.dp
private val CancelDistance = 48.dp
private const val ANCHOR_LEAN_SOFTNESS = 0.1f
private const val SWELL_DEPTH = 0.15f

private val TrackSpec = spring<Offset>(dampingRatio = 0.9f, stiffness = 1500f)
private val SettleSpec = spring<Offset>(dampingRatio = 0.6f, stiffness = 500f)

/** Underdamped, so the released opener pops back past rest before settling. */
private val PopSpec = spring<Float>(dampingRatio = 0.45f, stiffness = 600f)

private val DipSpec = tween<Float>(durationMillis = 90, easing = LinearOutSlowInEasing)
private const val DIP_THRESHOLD = 0.5f
