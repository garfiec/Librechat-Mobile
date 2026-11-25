package com.garfiec.librechat.feature.chat.screen

import androidx.compose.foundation.gestures.AnchoredDragScope
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The #542 workaround in [ChatPullUpSheetState]: a resize under an untargeted drag or fling leaves
 * Compose reporting an animation nothing runs, which makes every anchoredDraggable on the sheet
 * swallow taps. It is healed when a fling lands. [ChatPullUpSheetStaleTargetTest] pins the Compose
 * half; this covers the heal.
 */
class ChatPullUpSheetStaleFlagHealTest {

    private val isDragged = mutableStateOf(false)

    private fun anchors(height: Float) = DraggableAnchors {
        PullUpAnchor.Hidden at height
        PullUpAnchor.Revealed at 0f
    }

    private fun sheet(anchored: AnchoredDraggableState<PullUpAnchor>, fling: TargetedFlingBehavior = NoFling) =
        ChatPullUpSheetState(
            anchored = anchored,
            fling = fling,
            minFlingVelocityPx = 1f,
            backdrop = null,
            height = mutableIntStateOf(1000),
            gesture = PullUpGesture(),
            interactionSource = MutableInteractionSource(),
            isDragged = isDragged,
        )

    /** A finger drag that runs [path], during which the sheet's content resizes to 1247 px. */
    private fun TestScope.resizeDuringDrag(sheet: ChatPullUpSheetState, vararg path: Float) {
        val lift = CompletableDeferred<Unit>()
        var started = false
        val drag = launch {
            sheet.anchored.anchoredDrag {
                // Restartable on anchors, like the real drag, which re-subscribes to deltas rather
                // than replaying a position.
                if (!started) dragTo(path.first())
                started = true
                lift.await()
                path.drop(1).forEach { dragTo(it) }
            }
        }
        runCurrent()
        sheet.onHeightChanged(1247)
        Snapshot.sendApplyNotifications()
        runCurrent()
        lift.complete(Unit)
        runCurrent()
        check(drag.isCompleted)
    }

    /** The release fling, as AnchoredDraggableNode runs it: the sheet's [flingBehavior] inside an untargeted anchoredDrag. */
    private fun TestScope.fling(sheet: ChatPullUpSheetState): Job = launch {
        sheet.anchored.anchoredDrag {
            with(sheet.flingBehavior) { scrollScope(sheet.anchored).performFling(0f) }
        }
    }

    @Test
    fun aResizeAtTheEndOfAPullIsHealedWhenTheFlingLands() = runTest {
        val anchored = AnchoredDraggableState(PullUpAnchor.Hidden, anchors(1000f))
        val sheet = sheet(anchored)
        resizeDuringDrag(sheet, 0f)
        assertTrue(sheet.hasStaleAnimationFlag)

        fling(sheet)
        runCurrent()
        assertEquals(1, sheet.flingsEnded)
        sheet.healStaleAnimationFlag()

        assertFalse(anchored.isAnimationRunning)
        assertEquals(0f, anchored.offset)
        assertEquals(PullUpAnchor.Revealed, anchored.settledValue)
    }

    @Test
    fun aResizeEarlyInAPullIsHealedAndNoLongerClosesTheSheetOnTheNextResize() = runTest {
        // The stale target here is Hidden while the sheet ends up open.
        val anchored = AnchoredDraggableState(PullUpAnchor.Hidden, anchors(1000f))
        val sheet = sheet(anchored)
        resizeDuringDrag(sheet, 800f, 0f)
        assertEquals(PullUpAnchor.Hidden, anchored.targetValue)

        fling(sheet)
        runCurrent()
        sheet.healStaleAnimationFlag()

        assertFalse(anchored.isAnimationRunning)
        assertEquals(PullUpAnchor.Revealed, anchored.targetValue)
        sheet.onHeightChanged(900)
        assertEquals(PullUpAnchor.Revealed, anchored.currentValue)
        assertEquals(0f, anchored.offset)
    }

    @Test
    fun aRestartedFlingIsCountedOnceItLandsAndNotBefore() = runTest {
        val anchored = AnchoredDraggableState(PullUpAnchor.Hidden, anchors(1000f))
        val runs = GatedFling(anchored, target = 0f, midway = 500f)
        val sheet = sheet(anchored, runs)
        fling(sheet)
        runCurrent()
        assertEquals(500f, anchored.offset)

        // The content resizes mid-fling: the lock is held, so the flag goes stale and the fling restarts.
        sheet.onHeightChanged(1247)
        Snapshot.sendApplyNotifications()
        runCurrent()
        assertEquals(2, runs.started, "the resize restarts the fling")
        assertEquals(0, sheet.flingsEnded, "the cancelled run is not counted")
        assertTrue(sheet.hasStaleAnimationFlag, "stale while the restarted fling is still running")

        runs.release()
        runCurrent()
        assertEquals(1, sheet.flingsEnded)
        sheet.healStaleAnimationFlag()

        assertFalse(anchored.isAnimationRunning)
        assertEquals(0f, anchored.offset)
    }

    @Test
    fun aCancelledFlingIsNotCounted() = runTest {
        val anchored = AnchoredDraggableState(PullUpAnchor.Hidden, anchors(1000f))
        val runs = GatedFling(anchored, target = 0f, midway = 500f)
        val sheet = sheet(anchored, runs)
        val job = fling(sheet)
        runCurrent()

        job.cancel()
        runCurrent()

        assertEquals(0, sheet.flingsEnded)
    }

    @Test
    fun aHealHeldOffByAFingerHappensAtTheNextFling() = runTest {
        val anchored = AnchoredDraggableState(PullUpAnchor.Hidden, anchors(1000f))
        val sheet = sheet(anchored)
        resizeDuringDrag(sheet, 0f)

        isDragged.value = true
        fling(sheet)
        runCurrent()
        sheet.healStaleAnimationFlag()
        assertTrue(anchored.isAnimationRunning, "a finger is down: left alone")

        isDragged.value = false
        fling(sheet)
        runCurrent()
        assertEquals(2, sheet.flingsEnded)
        sheet.healStaleAnimationFlag()
        assertFalse(anchored.isAnimationRunning)
    }

    @Test
    fun aFingerStillOnTheSheetIsNotStale() = runTest {
        val anchored = AnchoredDraggableState(PullUpAnchor.Hidden, anchors(1000f))
        val sheet = sheet(anchored)
        resizeDuringDrag(sheet, 0f)

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

    /**
     * Flings to [target] by way of [midway], where every run waits until [release]. A restart (from a
     * resize) starts a new run from wherever the sheet is.
     */
    private class GatedFling(
        private val anchored: AnchoredDraggableState<PullUpAnchor>,
        private val target: Float,
        private val midway: Float,
    ) : TargetedFlingBehavior {
        var started = 0
            private set
        private val gate = CompletableDeferred<Unit>()

        fun release() = gate.complete(Unit)

        override suspend fun ScrollScope.performFling(
            initialVelocity: Float,
            onRemainingDistanceUpdated: (Float) -> Unit,
        ): Float {
            started++
            if (started == 1) scrollBy(midway - anchored.offset)
            gate.await()
            scrollBy(target - anchored.offset)
            return 0f
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

/** Mirrors AnchoredDraggableNode.fling's scroll scope: a fling's pixels become anchored drags. */
private fun AnchoredDragScope.scrollScope(anchored: AnchoredDraggableState<PullUpAnchor>) = object : ScrollScope {
    override fun scrollBy(pixels: Float): Float {
        val before = anchored.offset
        val after = (before + pixels).coerceIn(anchored.anchors.minPosition(), anchored.anchors.maxPosition())
        dragTo(after)
        return after - before
    }
}
