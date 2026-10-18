package com.garfiec.librechat.feature.chat.screen

import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.snapTo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the Compose foundation behaviour behind #542: anchors that change while an *untargeted*
 * anchoredDrag (a finger drag or the modifier's fling) holds the lock leave `dragTarget` set after
 * the drag ends, so `isAnimationRunning` stays true and every `anchoredDraggable` on the state
 * claims each pointer-down. The sheet's `onSizeChanged` → `updateAnchors` is that anchor change.
 *
 * If one of these starts failing after a Compose bump, upstream changed the behaviour: re-check
 * whether the workaround is still needed.
 */
class ChatPullUpSheetStaleTargetTest {

    private fun anchors(height: Float) = DraggableAnchors {
        PullUpAnchor.Hidden at height
        PullUpAnchor.Revealed at 0f
    }

    @Test
    fun resizeDuringADragLeavesTheAnimationFlagSetAfterTheDragEnds() = runTest {
        val state = AnchoredDraggableState(PullUpAnchor.Hidden, anchors(1000f))
        val lift = CompletableDeferred<Unit>()
        // A finger drag that pulls the sheet fully open and is still down. The block re-runs when
        // the anchors change (restartable), so it must be idempotent.
        val drag = launch {
            state.anchoredDrag {
                dragTo(0f)
                lift.await()
            }
        }
        runCurrent()

        // The sheet's content grows while the finger is down.
        state.updateAnchors(anchors(1247f), newTarget = state.targetValue)
        runCurrent()
        lift.complete(Unit)
        drag.join()

        assertEquals(PullUpAnchor.Revealed, state.settledValue)
        assertEquals(0f, state.offset)
        assertTrue(state.isAnimationRunning, "settled, yet still reports an animation running")
    }

    @Test
    fun aResizeWithNoDragInProgressLeavesNoFlag() = runTest {
        // Control: with the lock free, updateAnchors snaps synchronously and records nothing.
        val state = AnchoredDraggableState(PullUpAnchor.Revealed, anchors(1000f))
        state.updateAnchors(anchors(1247f), newTarget = state.targetValue)

        assertFalse(state.isAnimationRunning)
        assertEquals(0f, state.offset)
    }

    @Test
    fun theStaleTargetCanDisagreeWithWhereTheSheetSettled() = runTest {
        val state = AnchoredDraggableState(PullUpAnchor.Hidden, anchors(1000f))
        val lift = CompletableDeferred<Unit>()
        // The resize lands early in the pull, while the offset is still nearer Hidden; the finger
        // then carries on to fully open.
        val drag = launch {
            state.anchoredDrag {
                dragTo(800f)
                lift.await()
                dragTo(0f)
            }
        }
        runCurrent()

        state.updateAnchors(anchors(1247f), newTarget = state.targetValue)
        runCurrent()
        lift.complete(Unit)
        drag.join()

        assertEquals(PullUpAnchor.Revealed, state.settledValue)
        assertEquals(PullUpAnchor.Hidden, state.targetValue, "targetValue is pinned to the stale target")
        assertTrue(state.isAnimationRunning)
    }

    @Test
    fun aTargetedSnapClearsTheStaleFlagWithoutMovingTheSheet() = runTest {
        // Back → hide() is a targeted animateTo; a no-op snap takes the same cleanup path.
        val state = AnchoredDraggableState(PullUpAnchor.Hidden, anchors(1000f))
        val lift = CompletableDeferred<Unit>()
        val drag = launch {
            state.anchoredDrag {
                dragTo(0f)
                lift.await()
            }
        }
        runCurrent()
        state.updateAnchors(anchors(1247f), newTarget = state.targetValue)
        runCurrent()
        lift.complete(Unit)
        drag.join()

        state.snapTo(state.settledValue)

        assertFalse(state.isAnimationRunning)
        assertEquals(0f, state.offset)
        assertEquals(PullUpAnchor.Revealed, state.settledValue)
    }
}
