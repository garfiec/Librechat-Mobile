package com.garfiec.librechat.feature.chat.screen

import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The #542 workaround in [ChatPullUpSheetState]: a resize under an untargeted drag leaves Compose
 * reporting an animation nothing runs, which makes every anchoredDraggable on the sheet swallow taps.
 * [ChatPullUpSheetStaleTargetTest] pins the Compose half; this covers the heal.
 */
class ChatPullUpSheetStaleFlagHealTest {

    private val isDragged = mutableStateOf(false)

    private fun anchors(height: Float) = DraggableAnchors {
        PullUpAnchor.Hidden at height
        PullUpAnchor.Revealed at 0f
    }

    private fun sheet(anchored: AnchoredDraggableState<PullUpAnchor>) = ChatPullUpSheetState(
        anchored = anchored,
        flingBehavior = NoFling,
        minFlingVelocityPx = 1f,
        backdrop = null,
        height = mutableIntStateOf(1000),
        gesture = PullUpGesture(),
        interactionSource = MutableInteractionSource(),
        isDragged = isDragged,
    )

    /** A finger drag that runs [path], during which the sheet's content resizes to 1247 px. */
    private fun TestScope.resizeDuringDrag(state: AnchoredDraggableState<PullUpAnchor>, vararg path: Float) {
        val lift = CompletableDeferred<Unit>()
        val drag = launch {
            state.anchoredDrag {
                dragTo(path.first())
                lift.await()
                path.drop(1).forEach { dragTo(it) }
            }
        }
        runCurrent()
        state.updateAnchors(anchors(1247f), newTarget = state.targetValue)
        runCurrent()
        lift.complete(Unit)
        runCurrent()
        check(drag.isCompleted)
    }

    @Test
    fun aResizeAtTheEndOfAPullIsHealedWithoutMovingTheSheet() = runTest {
        val anchored = AnchoredDraggableState(PullUpAnchor.Hidden, anchors(1000f))
        val sheet = sheet(anchored)
        resizeDuringDrag(anchored, 0f)
        assertTrue(sheet.hasStaleAnimationFlag)

        withContext(StillClock) { sheet.clearStaleAnimationFlag() }

        assertFalse(anchored.isAnimationRunning)
        assertFalse(sheet.hasStaleAnimationFlag)
        assertEquals(0f, anchored.offset)
        assertEquals(PullUpAnchor.Revealed, anchored.settledValue)
    }

    @Test
    fun aResizeEarlyInAPullIsHealedAndNoLongerClosesTheSheetOnTheNextResize() = runTest {
        // The stale target here is Hidden while the sheet sits open.
        val anchored = AnchoredDraggableState(PullUpAnchor.Hidden, anchors(1000f))
        val sheet = sheet(anchored)
        resizeDuringDrag(anchored, 800f, 0f)
        assertEquals(PullUpAnchor.Hidden, anchored.targetValue)
        assertTrue(sheet.hasStaleAnimationFlag)

        withContext(StillClock) { sheet.clearStaleAnimationFlag() }

        assertFalse(anchored.isAnimationRunning)
        assertEquals(PullUpAnchor.Revealed, anchored.targetValue)
        // What onSizeChanged does on the next resize: it must keep the open sheet open.
        anchored.updateAnchors(anchors(900f), newTarget = anchored.targetValue)
        assertEquals(PullUpAnchor.Revealed, anchored.currentValue)
        assertEquals(0f, anchored.offset)
    }

    @Test
    fun aFlingThatPausesForAFrameIsNotCutShort() = runTest {
        // A resize mid-fling restarts the fling, and the restarted animation's first frame doesn't
        // move. Snapping on that frame would drop a sheet heading open back to Hidden, its nearer anchor.
        val anchored = AnchoredDraggableState(PullUpAnchor.Hidden, anchors(1000f))
        val sheet = sheet(anchored)
        resizeDuringDrag(anchored, 900f)
        assertTrue(sheet.hasStaleAnimationFlag)

        val fling = ScriptedClock(anchored, 900f, 600f, 300f, 0f)
        withContext(fling) { sheet.clearStaleAnimationFlag() }

        assertEquals(PullUpAnchor.Revealed, anchored.settledValue)
        assertEquals(0f, anchored.offset)
        assertFalse(anchored.isAnimationRunning)
    }

    @Test
    fun aSheetLeftStillBetweenAnchorsIsHealedToTheNearerOne() = runTest {
        // Nothing will move it (a cancelled fling): heal anyway rather than leave every tap dead.
        val anchored = AnchoredDraggableState(PullUpAnchor.Hidden, anchors(1000f))
        val sheet = sheet(anchored)
        resizeDuringDrag(anchored, 900f, 400f)

        withContext(StillClock) { sheet.clearStaleAnimationFlag() }

        assertFalse(anchored.isAnimationRunning)
        assertEquals(PullUpAnchor.Revealed, anchored.settledValue)
    }

    @Test
    fun aFingerStillOnTheSheetIsNotStale() = runTest {
        val anchored = AnchoredDraggableState(PullUpAnchor.Hidden, anchors(1000f))
        val sheet = sheet(anchored)
        resizeDuringDrag(anchored, 0f)

        isDragged.value = true

        assertFalse(sheet.hasStaleAnimationFlag)
    }

    @Test
    fun theSheetsOwnHideIsNotStale() = runTest {
        // hide() is a targeted animation: it sets the flag itself, and healing it would cancel it.
        val anchored = AnchoredDraggableState(PullUpAnchor.Revealed, anchors(1000f))
        val sheet = sheet(anchored)
        val frame = CompletableDeferred<Unit>()
        val hide = launch { withContext(BlockedClock(frame)) { sheet.hide() } }
        runCurrent()

        assertTrue(anchored.isAnimationRunning)
        assertFalse(sheet.hasStaleAnimationFlag)
        hide.cancel()
    }

    @Test
    fun noFlagNoHeal() = runTest {
        val anchored = AnchoredDraggableState(PullUpAnchor.Revealed, anchors(1000f))
        assertFalse(sheet(anchored).hasStaleAnimationFlag)
    }

    private object NoFling : TargetedFlingBehavior {
        override suspend fun ScrollScope.performFling(
            initialVelocity: Float,
            onRemainingDistanceUpdated: (Float) -> Unit,
        ): Float = initialVelocity
    }

    /** Frames tick without time passing: enough for the stillness check, which only compares offsets. */
    private object StillClock : MonotonicFrameClock {
        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
            yield()
            return onFrame(0L)
        }
    }

    /** Moves the sheet to one scripted offset per frame (as a running fling would), then holds the last. */
    private class ScriptedClock(
        private val anchored: AnchoredDraggableState<PullUpAnchor>,
        vararg offsets: Float,
    ) : MonotonicFrameClock {
        private val frames = ArrayDeque(offsets.toList())

        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
            yield()
            frames.removeFirstOrNull()?.let { anchored.dispatchRawDelta(it - anchored.offset) }
            return onFrame(0L)
        }
    }

    /** Holds an animation at its first frame. */
    private class BlockedClock(private val frame: CompletableDeferred<Unit>) : MonotonicFrameClock {
        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
            frame.await()
            return onFrame(0L)
        }
    }
}
