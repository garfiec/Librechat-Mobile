package com.garfiec.librechat.shared.navigation

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import kotlin.math.abs
import kotlin.math.max

/**
 * Whether the platform needs [edgeSwipeBack]: iOS, where the start-edge swipe is back. Android's
 * system back gesture already reaches the dispatcher.
 */
internal expect val needsEdgeSwipeBack: Boolean

private val EdgeWidth = 20.dp

// UIKitNavigationEventInput's thresholds (Compose Multiplatform 1.12), in dp rather than points.
private const val COMMIT_SHARE = 0.3f
private const val COMMIT_VELOCITY_DP = 100f
private const val CANCEL_VELOCITY_DP = -10f

/** How a swipe in from the start edge ends, from its distance and speed away from the edge. */
internal object EdgeSwipeBackRules {
    /** How far along the back gesture is, 0 to 1. */
    fun progress(travelPx: Float, widthPx: Float): Float =
        if (widthPx <= 0f) 0f else (max(0f, travelPx) / widthPx).coerceAtMost(1f)

    /** Whether letting go now goes back (true) or stays (false). Velocity is in dp per second. */
    fun commits(travelPx: Float, widthPx: Float, velocityDpPerSecond: Float): Boolean = when {
        velocityDpPerSecond > COMMIT_VELOCITY_DP -> true
        velocityDpPerSecond < CANCEL_VELOCITY_DP -> false
        else -> widthPx > 0f && travelPx >= widthPx * COMMIT_SHARE
    }
}

/**
 * A swipe in from the start edge as back, fed to the navigation event dispatcher: the screen's
 * enabled back handler gets it (a dirty editor's discard prompt), else NavDisplay pops with its
 * predictive animation.
 *
 * Compose Multiplatform attaches its own edge-pan recognizer for this, which holds the touches
 * until it fails and so never lets this see a swipe it took. That recognizer can fail to fire at all,
 * which leaves the edge to the drawer's drag on pushed screens. This takes the swipe in Compose
 * instead whenever the recognizer lets the touches through.
 *
 * Watches the Initial pass and consumes only after the touch has moved away from the edge past
 * touch slop, so taps on the content under the edge (a top bar's close button) still land and
 * the drawer's drag, an ancestor, sees the swipe as taken. Off while [enabled] is false, which
 * leaves the edge to the drawer on a root screen.
 */
@Composable
internal fun Modifier.edgeSwipeBack(enabled: Boolean): Modifier {
    if (!needsEdgeSwipeBack) return this
    val dispatcher = LocalNavigationEventDispatcherOwner.current?.navigationEventDispatcher ?: return this
    val input = remember { DirectNavigationEventInput() }
    DisposableEffect(dispatcher, input) {
        dispatcher.addInput(input)
        onDispose { dispatcher.removeInput(input) }
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return pointerInput(enabled, rtl, input) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val width = size.width.toFloat()
            val edgePx = EdgeWidth.toPx()
            val fromEdge = if (rtl) down.position.x >= width - edgePx else down.position.x <= edgePx
            if (!fromEdge) return@awaitEachGesture
            val swipe = EdgeSwipe(down, rtl, width, input)
            if (!awaitSwipeStart(swipe, viewConfiguration.touchSlop)) return@awaitEachGesture
            try {
                trackSwipe(swipe)
            } finally {
                if (!swipe.settled) input.backCancelled()
            }
        }
    }
}

private class EdgeSwipe(
    val down: PointerInputChange,
    val rtl: Boolean,
    val width: Float,
    val input: DirectNavigationEventInput,
) {
    val velocity = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
    var settled = false

    /** Distance moved away from the edge. */
    fun travel(change: PointerInputChange): Float =
        if (rtl) down.position.x - change.position.x else change.position.x - down.position.x

    fun event(change: PointerInputChange, progress: Float) = NavigationEvent(
        touchX = change.position.x,
        touchY = change.position.y,
        progress = progress,
        swipeEdge = if (rtl) NavigationEvent.EDGE_RIGHT else NavigationEvent.EDGE_LEFT,
    )
}

/**
 * Waits for the touch to move away from the edge past [slop], then starts back. False when it
 * lifts first, turns vertical, heads back into the edge or someone else takes it.
 */
private suspend fun AwaitPointerEventScope.awaitSwipeStart(swipe: EdgeSwipe, slop: Float): Boolean {
    while (true) {
        val change = awaitPointerEvent(PointerEventPass.Initial).changes
            .firstOrNull { it.id == swipe.down.id } ?: return false
        swipe.velocity.addPosition(change.uptimeMillis, change.position)
        val dx = swipe.travel(change)
        val dy = abs(change.position.y - swipe.down.position.y)
        val gaveUp = !change.pressed || change.isConsumed || dx < -slop || (dy > slop && dy > abs(dx))
        if (gaveUp) return false
        if (dx > slop) {
            change.consume()
            swipe.input.backStarted(swipe.event(change, 0f))
            return true
        }
    }
}

/** Follows a started swipe to its release, then goes back or stays. */
private suspend fun AwaitPointerEventScope.trackSwipe(swipe: EdgeSwipe) {
    while (true) {
        val change = awaitPointerEvent(PointerEventPass.Initial).changes
            .firstOrNull { it.id == swipe.down.id } ?: return
        swipe.velocity.addPosition(change.uptimeMillis, change.position)
        val dx = swipe.travel(change)
        if (!change.pressed) {
            val vx = swipe.velocity.calculateVelocity().x.let { if (swipe.rtl) -it else it } / density
            if (EdgeSwipeBackRules.commits(dx, swipe.width, vx)) swipe.input.backCompleted() else swipe.input.backCancelled()
            swipe.settled = true
            return
        }
        change.consume()
        swipe.input.backProgressed(swipe.event(change, EdgeSwipeBackRules.progress(dx, swipe.width)))
    }
}
