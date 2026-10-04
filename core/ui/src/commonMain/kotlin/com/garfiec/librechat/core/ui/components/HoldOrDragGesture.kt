package com.garfiec.librechat.core.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange

/** How a press began: a tap, a hold past the long-press timeout, a drag that is ours, or one that isn't. */
internal enum class HoldOrDrag { Tap, Hold, Drag, Yield }

/**
 * Watches the press that went down at [down] until the long-press timeout makes it a Hold, or it is
 * classified sooner. [claimMove] sees each move's offset from the down: it returns Drag (that move is
 * consumed, which cancels the press's own click), Yield, or null to keep waiting, when [onPending]
 * gets the move. The press yields if its pointer goes away and, with [yieldWhenConsumed], when a
 * handler earlier in [pass] has consumed the move.
 */
internal suspend fun AwaitPointerEventScope.awaitHoldOrDrag(
    down: PointerInputChange,
    pass: PointerEventPass,
    yieldWhenConsumed: Boolean,
    claimMove: (delta: Offset) -> HoldOrDrag?,
    onPending: (PointerInputChange) -> Unit = {},
): HoldOrDrag = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
    var result: HoldOrDrag? = null
    while (result == null) {
        val change = awaitPointerEvent(pass).changes.firstOrNull { it.id == down.id }
        result = when {
            change == null || (yieldWhenConsumed && change.isConsumed) -> HoldOrDrag.Yield
            !change.pressed -> HoldOrDrag.Tap
            else -> claimMove(change.position - down.position).also { claim ->
                if (claim == HoldOrDrag.Drag) change.consume()
                if (claim == null) onPending(change)
            }
        }
    }
    result
} ?: HoldOrDrag.Hold

/**
 * Follows a claimed press to its release. [onMove] gets every move; once the finger is past touch
 * slop from [down] (at once when [dragging]), [onDrag] gets each move too, which is then consumed.
 * [onRelease] gets the release only if the press dragged: a hold released without moving is left to
 * the press's own click.
 */
internal suspend fun AwaitPointerEventScope.trackHoldOrDrag(
    down: PointerInputChange,
    dragging: Boolean,
    onMove: (PointerInputChange) -> Unit = {},
    onDrag: (PointerInputChange) -> Unit,
    onRelease: (PointerInputChange) -> Unit,
) {
    val slop = viewConfiguration.touchSlop
    var isDragging = dragging
    while (true) {
        // Initial pass once claimed: a list's own scroll sits inside the gesture's modifier (a
        // LazyColumn's), so in the Main pass it would see the drag first and scroll, or hand it to the
        // sheet. Consumed here, neither starts.
        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: return
        if (!change.pressed) {
            if (isDragging) onRelease(change)
            return
        }
        onMove(change)
        if (!isDragging && (change.position - down.position).getDistance() > slop) isDragging = true
        if (isDragging) {
            onDrag(change)
            change.consume()
        }
    }
}
