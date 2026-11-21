package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Where the composer's chrome sends the vertical drags it doesn't use (`passVerticalDragsToThread`,
 * the steer and queue region's edge hand-off): the thread under the composer.
 *
 * Deltas and velocities are the finger's, positive downwards, as nested scroll reports them.
 */
interface ThreadDrags {
    /** Whether a thread is there to take a drag; the chrome keeps its own gestures otherwise. */
    val canTakeDrag: Boolean

    /** Moves the thread by a finger movement of [delta] px, inside one user scroll that stays open until [fling] or [cancel]. */
    fun drag(delta: Float)

    /** Ends the drag with a fling at [velocity] px/s. A fling without a drag still flings. */
    fun fling(velocity: Float)

    /** Ends the drag where it is. */
    fun cancel()
}

/**
 * The chat screen's [ThreadDrags]; `ChatContent` binds the list on screen ([BindThreadScroll]).
 * A drag is ONE `scroll(UserInput)` session from first delta to fling, so `isScrollInProgress` holds
 * throughout and `TailFollower` reads it as the user scrolling away; don't open one per delta.
 */
@Stable
class ThreadScrollHandle internal constructor(private val scope: CoroutineScope) : ThreadDrags {
    /** The list a drag scrolls, with the fling it ends in; null while no list is bound. */
    private var bound: Pair<ScrollableState, FlingBehavior>? = null
    private var session: Channel<ThreadDragEvent>? = null

    override val canTakeDrag: Boolean get() = bound != null

    internal fun bind(state: ScrollableState, fling: FlingBehavior) {
        if (bound?.first !== state) cancel()
        bound = state to fling
    }

    internal fun unbind(state: ScrollableState) {
        if (bound?.first !== state) return
        cancel()
        bound = null
    }

    override fun drag(delta: Float) {
        if (delta == 0f) return
        (session ?: open() ?: return).trySend(ThreadDragEvent.Delta(delta))
    }

    override fun fling(velocity: Float) {
        if (session == null && velocity == 0f) return
        val open = session ?: open() ?: return
        open.trySend(ThreadDragEvent.Fling(velocity))
        open.close()
        session = null
    }

    override fun cancel() {
        session?.close()
        session = null
    }

    private fun open(): Channel<ThreadDragEvent>? {
        val (state, fling) = bound ?: return null
        val events = Channel<ThreadDragEvent>(Channel.UNLIMITED)
        session = events
        // Undispatched, so the session holds the scroll before the first delta is read.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                state.scroll(MutatePriority.UserInput) {
                    for (event in events) {
                        when (event) {
                            // The list is laid out top to bottom: a finger moving down scrolls back.
                            is ThreadDragEvent.Delta -> scrollBy(-event.delta)
                            is ThreadDragEvent.Fling -> with(fling) { performFling(-event.velocity) }
                        }
                    }
                }
            } finally {
                // A finger on the list took the scroll: later deltas start a new session.
                if (session === events) session = null
                events.close()
            }
        }
        return events
    }
}

private sealed interface ThreadDragEvent {
    data class Delta(val delta: Float) : ThreadDragEvent
    data class Fling(val velocity: Float) : ThreadDragEvent
}

/** The chat screen's [ThreadScrollHandle]; null outside one (previews, the shared-chat viewer). */
val LocalThreadScrollHandle = staticCompositionLocalOf<ThreadScrollHandle?> { null }

@Composable
fun rememberThreadScrollHandle(): ThreadScrollHandle {
    val scope = rememberCoroutineScope()
    return remember(scope) { ThreadScrollHandle(scope) }
}

@Composable
fun BindThreadScroll(state: ScrollableState) {
    val handle = LocalThreadScrollHandle.current ?: return
    val fling = ScrollableDefaults.flingBehavior()
    DisposableEffect(handle, state, fling) {
        handle.bind(state, fling)
        onDispose { handle.unbind(state) }
    }
}

/** Which of Compare's lists the composer's drags scroll. */
enum class ThreadScrollTarget { PRIMARY, SECONDARY }

/**
 * The list a drag on the composer scrolls: the one it covers. On a phone that is the tab on screen;
 * side by side the composer runs under both panes, and pane A leads (with sync scroll on, B follows).
 */
internal fun threadScrollTarget(comparing: Boolean, wide: Boolean, activeTab: Int): ThreadScrollTarget =
    if (comparing && !wide && activeTab == 1) ThreadScrollTarget.SECONDARY else ThreadScrollTarget.PRIMARY
