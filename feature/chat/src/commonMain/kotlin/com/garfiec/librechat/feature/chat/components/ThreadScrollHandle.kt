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
 * The [ThreadDrags] of the chat screen. `ChatScreen` creates it, above both the thread and the
 * composer; `ChatContent`, which owns the lists, binds whichever list is on screen
 * ([BindThreadScroll]).
 *
 * A drag is ONE `scroll(UserInput) { scrollBy }` session from its first delta to its fling, as a
 * finger on the list makes, so the list reports `isScrollInProgress` for the whole of it: that is
 * what the tail follower reads as the user scrolling away mid-reply (`TailFollower`), and what
 * takes the scroll from the follower instead of fighting it frame by frame. A finger on the list
 * itself cancels the session the same way it would cancel a fling.
 */
@Stable
class ThreadScrollHandle internal constructor(private val scope: CoroutineScope) : ThreadDrags {
    private var target: ScrollableState? = null
    private var flingBehavior: FlingBehavior? = null
    private var session: Channel<ThreadDragEvent>? = null

    override val canTakeDrag: Boolean get() = target != null

    internal fun bind(state: ScrollableState, fling: FlingBehavior) {
        if (target !== state) cancel()
        target = state
        flingBehavior = fling
    }

    internal fun unbind(state: ScrollableState) {
        if (target !== state) return
        cancel()
        target = null
        flingBehavior = null
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
        val state = target ?: return null
        val fling = flingBehavior ?: return null
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

/** Points [LocalThreadScrollHandle] at [state] while this is composed. */
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
