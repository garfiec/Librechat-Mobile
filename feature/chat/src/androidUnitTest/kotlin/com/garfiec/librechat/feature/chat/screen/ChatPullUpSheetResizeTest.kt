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
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [ChatPullUpSheetState.onHeightChanged]: a sheet caught mid-way by a resize keeps its visible
 * height (its top edge stays under the finger); a resting sheet keeps its anchor.
 */
class ChatPullUpSheetResizeTest {

    private fun anchors(height: Float) = DraggableAnchors {
        PullUpAnchor.Hidden at height
        PullUpAnchor.Revealed at 0f
    }

    /** A sheet measured at 1000 px, resting at [at] or moved there without the lock (nested scroll). */
    private fun sheet(at: Float = 1000f): ChatPullUpSheetState {
        val anchored = AnchoredDraggableState(PullUpAnchor.Hidden, anchors(1000f))
        anchored.dispatchRawDelta(at - anchored.offset)
        return ChatPullUpSheetState(
            anchored = anchored,
            fling = NoFling,
            minFlingVelocityPx = 1f,
            backdrop = null,
            height = mutableIntStateOf(1000),
            gesture = PullUpGesture(),
            interactionSource = MutableInteractionSource(),
            isDragged = mutableStateOf(false),
        )
    }

    private val ChatPullUpSheetState.visibleHeight get() = heightPx - anchored.offset

    @Test
    fun aGrowthUnderANestedScrollPullKeepsTheTopEdge() {
        // No lock is held here, so updateAnchors alone would snap the sheet to an anchor.
        val sheet = sheet(at = 600f)

        sheet.onHeightChanged(1247)

        assertEquals(847f, sheet.anchored.offset)
        assertEquals(400f, sheet.visibleHeight)
    }

    @Test
    fun aGrowthUnderADragKeepsTheTopEdge() = runTest {
        val sheet = sheet()
        val lift = CompletableDeferred<Unit>()
        var started = false
        val drag = launch {
            sheet.anchored.anchoredDrag {
                if (!started) dragTo(600f)
                started = true
                lift.await()
            }
        }
        runCurrent()

        sheet.onHeightChanged(1247)

        assertEquals(847f, sheet.anchored.offset)
        lift.complete(Unit)
        drag.join()
    }

    @Test
    fun aShrinkKeepsTheTopEdge() {
        val sheet = sheet(at = 950f)

        sheet.onHeightChanged(900)

        assertEquals(850f, sheet.anchored.offset)
        assertEquals(50f, sheet.visibleHeight)
    }

    @Test
    fun aShrinkBelowTheVisibleHeightOpensItFully() {
        val sheet = sheet(at = 100f)

        sheet.onHeightChanged(500)

        assertEquals(0f, sheet.anchored.offset)
    }

    @Test
    fun anOpenSheetStaysOpen() {
        val sheet = sheet(at = 0f)

        sheet.onHeightChanged(1247)

        assertEquals(0f, sheet.anchored.offset)
    }

    @Test
    fun aHiddenSheetStaysHiddenAtItsNewHeight() {
        val sheet = sheet(at = 1000f)

        sheet.onHeightChanged(1247)

        assertEquals(1247f, sheet.anchored.offset)
        assertEquals(PullUpAnchor.Hidden, sheet.anchored.currentValue)
    }

    @Test
    fun theSheetsOwnAnimationIsLeftToReAimItself() = runTest {
        val sheet = sheet(at = 600f)
        val frame = CompletableDeferred<Unit>()
        val hide = launch { withContext(BlockedClock(frame)) { sheet.hide() } }
        runCurrent()

        sheet.onHeightChanged(1247)

        assertEquals(600f, sheet.anchored.offset)
        hide.cancel()
    }

    private object NoFling : TargetedFlingBehavior {
        override suspend fun ScrollScope.performFling(
            initialVelocity: Float,
            onRemainingDistanceUpdated: (Float) -> Unit,
        ): Float = initialVelocity
    }

    /** Holds an animation at its first frame. */
    private class BlockedClock(private val frame: CompletableDeferred<Unit>) : MonotonicFrameClock {
        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
            frame.await()
            return onFrame(0L)
        }
    }
}
