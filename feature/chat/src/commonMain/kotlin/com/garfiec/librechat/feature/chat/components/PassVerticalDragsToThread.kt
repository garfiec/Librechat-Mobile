package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Velocity
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.sign

/**
 * Hands the vertical drags that start on the composer's chrome (its chips, steers, queue and
 * banners) to the thread under it ([LocalThreadScrollHandle]), fling included, so a swipe that
 * happens to start on a chip still scrolls the messages. Without it, whatever claims the touch first
 * (a horizontal chip row, a card) swallows the swipe, and only in some styles.
 *
 * It watches after its children (the Main pass, bottom-up), so anything inside that claims the
 * gesture keeps it: a horizontal scroll past its slop, the queue's long-press reorder once it has
 * started, an inner vertical scroll (whose edge hands off through [ThreadHandOffConnection]). Once
 * it has taken a drag it consumes it, which cancels the press of the chip under the finger.
 *
 * Trade-off: the area it covers doesn't pass taps through to the thread behind it; only drags
 * reach the thread from there.
 */
fun Modifier.passVerticalDragsToThread(): Modifier = this then PassVerticalDragsElement

private object PassVerticalDragsElement : ModifierNodeElement<PassVerticalDragsNode>() {
    override fun create() = PassVerticalDragsNode()

    override fun update(node: PassVerticalDragsNode) = Unit

    override fun InspectorInfo.inspectableProperties() {
        name = "passVerticalDragsToThread"
    }

    override fun equals(other: Any?): Boolean = other === this

    override fun hashCode(): Int = "passVerticalDragsToThread".hashCode()
}

private class PassVerticalDragsNode : DelegatingNode(), CompositionLocalConsumerModifierNode {
    init {
        delegate(SuspendingPointerInputModifierNode { forwardVerticalDrags { currentValueOf(LocalThreadScrollHandle) } })
    }
}

private suspend fun PointerInputScope.forwardVerticalDrags(threadDrags: () -> ThreadDrags?) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
        val drags = threadDrags()?.takeIf { it.canTakeDrag } ?: return@awaitEachGesture
        val gesture = PassThroughDrag(touchSlop = viewConfiguration.touchSlop)
        val velocity = VelocityTracker().apply { addPointerInputChange(down) }
        val maxVelocity = viewConfiguration.maximumFlingVelocity
        try {
            while (!gesture.isStoodDown) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null || !change.pressed) {
                    if (gesture.isForwarding) {
                        if (change == null || change.isConsumed) {
                            drags.cancel()
                        } else {
                            val released = velocity.calculateVelocity(Velocity(maxVelocity, maxVelocity))
                            drags.fling(released.y)
                            change.consume()
                        }
                    }
                    break
                }
                velocity.addPointerInputChange(change)
                val move = change.positionChangeIgnoreConsumed()
                val wasForwarding = gesture.isForwarding
                val forward = gesture.onMove(dx = move.x, dy = move.y, consumedByChild = change.isConsumed)
                when {
                    gesture.isForwarding -> {
                        change.consume()
                        drags.drag(forward)
                    }
                    wasForwarding -> drags.cancel()
                }
            }
        } catch (e: CancellationException) {
            if (gesture.isForwarding) drags.cancel()
            throw e
        }
    }
}

/**
 * Whose a drag on the chrome is, decided move by move. Pure, so the thresholds are testable without
 * a pointer harness.
 *
 * Waits until the finger has moved past [touchSlop], then takes the drag if it went mostly
 * vertically and stands down if it went sideways. Stands down for good the moment a child consumes
 * a move: a horizontal row, the queue's reorder or an inner scroll took the gesture.
 */
internal class PassThroughDrag(private val touchSlop: Float) {
    private var totalX = 0f
    private var totalY = 0f
    private var phase = Phase.WAITING

    val isForwarding: Boolean get() = phase == Phase.FORWARDING
    val isStoodDown: Boolean get() = phase == Phase.STOOD_DOWN

    /** The finger delta to pass to the thread for this move (0 while waiting or stood down). */
    fun onMove(dx: Float, dy: Float, consumedByChild: Boolean): Float {
        if (consumedByChild) {
            phase = Phase.STOOD_DOWN
            return 0f
        }
        return when (phase) {
            Phase.STOOD_DOWN -> 0f
            Phase.FORWARDING -> dy
            Phase.WAITING -> {
                totalX += dx
                totalY += dy
                when {
                    abs(totalY) > touchSlop && abs(totalY) > abs(totalX) -> {
                        phase = Phase.FORWARDING
                        // What lies past the slop, as a scrollable's own drag starts.
                        totalY - sign(totalY) * touchSlop
                    }
                    abs(totalX) > touchSlop -> {
                        phase = Phase.STOOD_DOWN
                        0f
                    }
                    else -> 0f
                }
            }
        }
    }

    private enum class Phase { WAITING, FORWARDING, STOOD_DOWN }
}

/**
 * Hands a capped inner scroll's leftovers to the thread: once the steer and queue region has
 * scrolled to its edge, the rest of the drag scrolls the messages, and so does a fling it can't
 * use. A drag the region takes back (the finger reverses) leaves the thread where it is.
 */
internal class ThreadHandOffConnection(private val threadDrags: ThreadDrags?) : NestedScrollConnection {
    private var handingOff = false

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput) return Offset.Zero
        val drags = threadDrags?.takeIf { it.canTakeDrag } ?: return Offset.Zero
        if (available.y == 0f) {
            if (handingOff && consumed.y != 0f) {
                handingOff = false
                drags.cancel()
            }
            return Offset.Zero
        }
        handingOff = true
        drags.drag(available.y)
        return Offset(0f, available.y)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (!handingOff) return Velocity.Zero
        handingOff = false
        // The drag ended on the thread, so the momentum is the thread's, not the region's.
        val drags = threadDrags?.takeIf { it.canTakeDrag } ?: return Velocity.Zero
        drags.fling(available.y)
        return Velocity(0f, available.y)
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        if (available.y == 0f) return Velocity.Zero
        val drags = threadDrags?.takeIf { it.canTakeDrag } ?: return Velocity.Zero
        // The region's own fling hit its edge: the thread carries on with what was left.
        drags.fling(available.y)
        return Velocity(0f, available.y)
    }
}
