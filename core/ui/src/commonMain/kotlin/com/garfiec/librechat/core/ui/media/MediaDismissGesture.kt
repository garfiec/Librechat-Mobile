package com.garfiec.librechat.core.ui.media

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.CancellationException
import kotlin.math.abs

/**
 * Callbacks for [dismissDrag]. One object rather than several lambdas, because it keys the
 * gesture: a recomposition that rebuilt the lambdas would restart a drag in progress.
 */
internal interface DismissDragHandler {
    /** Asked on touch-down; false leaves the whole gesture to the content. */
    fun canStart(): Boolean

    /**
     * Asked once touch slop is past and the drag runs mostly sideways, [dx] being its horizontal
     * travel: false leaves it to the content (a pager with a page that way).
     */
    fun claimsSideways(dx: Float): Boolean

    fun onStart()

    fun onDrag(delta: Offset)

    /** The finger lifted (or the gesture was cut off, with zero velocity). */
    fun onRelease(velocity: Offset)
}

/**
 * Claims a one-finger drag in any direction, except a mostly sideways one [DismissDragHandler.claimsSideways]
 * turns down. It watches the initial pass, ahead of the content, so a zoomable image or a pager below
 * never sees a drag it claimed; anything else (a page swipe, a pinch) passes through untouched.
 */
internal fun Modifier.dismissDrag(handler: DismissDragHandler): Modifier = pointerInput(handler) {
    // When the last gesture this let through ended; a touch-down soon after it is the second tap
    // of ZoomImage's double-tap-and-drag zoom, which a dismiss drag must not steal.
    var lastUnclaimedUp: Long? = null
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val secondTap = lastUnclaimedUp?.let { down.uptimeMillis - it <= viewConfiguration.doubleTapTimeoutMillis } ?: false
        val slop = if (secondTap || !handler.canStart()) null else awaitDismissSlop(down.id, handler)
        if (slop == null) {
            lastUnclaimedUp = awaitAllUp()
            return@awaitEachGesture
        }
        lastUnclaimedUp = null
        handler.onStart()
        // Catch up the slop the finger has already moved, so the image starts under it.
        handler.onDrag(slop)
        val velocity = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id }
                val delta = change?.positionChange() ?: Offset.Zero
                event.changes.forEach { it.consume() }
                if (change == null || !change.pressed) break
                velocity.addPosition(change.uptimeMillis, change.position)
                handler.onDrag(delta)
            }
        } catch (cancellation: CancellationException) {
            handler.onRelease(Offset.Zero)
            throw cancellation
        }
        val released = velocity.calculateVelocity()
        handler.onRelease(Offset(released.x, released.y))
    }
}

/** Waits for every finger to lift, and returns when the last one did. */
private suspend fun AwaitPointerEventScope.awaitAllUp(): Long {
    var event = currentEvent
    while (event.changes.any { it.pressed }) event = awaitPointerEvent(PointerEventPass.Initial)
    return event.changes.maxOfOrNull { it.uptimeMillis } ?: 0L
}

/**
 * Waits out touch slop. Returns the distance moved when [pointer], the only finger down, went
 * somewhere [handler] claims and nothing else took it; null otherwise.
 */
private suspend fun AwaitPointerEventScope.awaitDismissSlop(pointer: PointerId, handler: DismissDragHandler): Offset? {
    var total = Offset.Zero
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        if (event.changes.count { it.pressed } > 1) return null
        val change = event.changes.firstOrNull { it.id == pointer } ?: return null
        if (!change.pressed || change.isConsumed) return null
        total += change.positionChange()
        if (total.getDistance() > viewConfiguration.touchSlop) {
            if (abs(total.x) > abs(total.y) && !handler.claimsSideways(total.x)) return null
            change.consume()
            return total
        }
    }
}
