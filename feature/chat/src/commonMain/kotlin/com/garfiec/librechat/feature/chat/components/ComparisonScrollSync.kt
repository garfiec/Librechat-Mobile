package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.garfiec.librechat.feature.chat.util.FollowerMove
import com.garfiec.librechat.feature.chat.util.SyncItem
import com.garfiec.librechat.feature.chat.util.SyncViewport
import com.garfiec.librechat.feature.chat.util.followerMove
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

internal enum class ComparisonPane { A, B }

/**
 * Keeps Compare's two side-by-side panes level as either one scrolls (see `ScrollSyncMapping`).
 * The pane the user last touched leads; the other follows.
 *
 * While a run streams, each pane normally follows its own tail and the sync stands aside: both
 * sit at their ends, which the mapping holds level anyway. Once the user scrolls the lead away
 * from its tail, [holdingTails] stands both tail followers down and the sync drives the other pane
 * as the replies grow; bringing the lead back to its tail hands both back to their followers.
 */
@Stable
internal class ComparisonScrollSync(private val primary: LazyListState, private val secondary: LazyListState) {
    var lead by mutableStateOf(ComparisonPane.A)

    var holdingTails by mutableStateOf(false)
        private set

    // Where the sync last left each pane. A follower found anywhere else was moved by something
    // other than the sync (a find-in-page jump, the scroll-to-bottom button, the keyboard), and
    // takes the lead so the other pane follows it. Plain fields: only the drive loop reads them.
    private val lastSet = arrayOfNulls<Pair<Int, Int>>(2)
    private val heights = arrayOf(ItemHeights(), ItemHeights())
    private var previousLead: ComparisonPane? = null
    private var previousLeadPosition: Pair<Int, Int>? = null

    // The last jump, and where the lead stood when it was made. A jump forces a layout, which
    // starts the next pass; the same jump again from the same lead position made no progress, and
    // repeating it would hold the main thread in that loop.
    private var lastJump: Pair<FollowerMove.ToItem, Pair<Int, Int>>? = null

    private fun state(pane: ComparisonPane) = if (pane == ComparisonPane.A) primary else secondary

    private fun forget() {
        lastSet.fill(null)
        previousLead = null
        previousLeadPosition = null
        lastJump = null
    }

    internal suspend fun step(isStreaming: Boolean, nearTailThresholdPx: Float) {
        val leadPane = lead
        val followerPane = leadPane.other()
        val leader = state(leadPane)
        val follower = state(followerPane)
        val leaderInfo = leader.layoutInfo

        holdingTails = isStreaming && !leaderInfo.restsNearTail(nearTailThresholdPx)
        if (isStreaming && !holdingTails) {
            forget()
            return
        }

        val expected = lastSet[followerPane.ordinal]
        if (expected != null && follower.position() != expected) {
            forget()
            lead = followerPane
            return
        }

        val position = leader.position()
        val previous = previousLeadPosition
        // The pass after a jump refines it, whichever way that goes.
        val refiningJump = lastJump != null
        val direction = if (previousLead == leadPane && previous != null && !refiningJump) compare(position, previous) else 0
        previousLead = leadPane
        previousLeadPosition = position

        val followerHeights = heights[followerPane.ordinal]
        val move = followerMove(
            leaderInfo.toSyncViewport(heights[leadPane.ordinal]),
            follower.layoutInfo.toSyncViewport(followerHeights),
            direction,
            estimateSize = followerHeights::height,
        )
        if (move is FollowerMove.ToItem && lastJump == (move to position)) return
        lastJump = (move as? FollowerMove.ToItem)?.let { it to position }
        if (move == FollowerMove.None) return
        try {
            when (move) {
                is FollowerMove.By -> follower.scrollBy(move.px)
                is FollowerMove.ToItem -> follower.scrollToItem(move.index, move.scrollOffsetPx)
                FollowerMove.None -> Unit
            }
        } catch (e: CancellationException) {
            // A gesture on the follower takes its scroll mutex and cancels ours; that pane is
            // about to lead. Only a cancellation of the sync itself may propagate.
            if (!currentCoroutineContext().isActive) throw e
        } finally {
            lastSet[followerPane.ordinal] = follower.position()
        }
    }

    internal fun release() {
        holdingTails = false
        forget()
    }
}

@Composable
internal fun rememberComparisonScrollSync(
    primary: LazyListState,
    secondary: LazyListState,
    enabled: Boolean,
    isStreaming: Boolean,
): ComparisonScrollSync {
    val sync = remember(primary, secondary) { ComparisonScrollSync(primary, secondary) }
    val nearTailThresholdPx = rememberNearBottomThresholdPx()
    val currentIsStreaming by rememberUpdatedState(isStreaming)
    LaunchedEffect(sync, enabled, nearTailThresholdPx) {
        if (!enabled) {
            sync.release()
            return@LaunchedEffect
        }
        // A new layoutInfo is published on every layout pass of either pane, so this runs once per
        // frame in which something moved or resized — and not at all while both are still. Our own
        // move re-triggers it once, and that pass finds nothing left to do.
        snapshotFlow { Triple(sync.lead, primary.layoutInfo, secondary.layoutInfo) }
            .collect { sync.step(currentIsStreaming, nearTailThresholdPx) }
    }
    return sync
}

/**
 * Hands the lead to [pane] the moment a finger lands on it, before the gesture scrolls anything.
 * Tracked while unlinked too, so re-linking aligns to the pane the user was just reading rather
 * than to whichever one they touched before unlinking.
 */
internal fun Modifier.comparisonSyncLead(sync: ComparisonScrollSync, pane: ComparisonPane): Modifier =
    pointerInput(sync, pane) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.any { it.pressed }) sync.lead = pane
            }
        }
    }

private fun ComparisonPane.other() = if (this == ComparisonPane.A) ComparisonPane.B else ComparisonPane.A

private fun LazyListState.position() = firstVisibleItemIndex to firstVisibleItemScrollOffset

private fun compare(a: Pair<Int, Int>, b: Pair<Int, Int>): Int =
    compareValuesBy(a, b, { it.first }, { it.second })

/**
 * The last measured height of each item a pane has laid out, so the sync knows how far the pane
 * is from its top and its end after the first or last item has scrolled off.
 */
private class ItemHeights {
    private val byIndex = HashMap<Int, Pair<Any, Int>>()

    fun record(info: LazyListLayoutInfo) {
        // A different item at a seen index means the list itself changed (another branch, a
        // reload): every recorded height may now belong to something else.
        if (info.visibleItemsInfo.any { item -> byIndex[item.index]?.let { it.first != item.key } == true }) {
            byIndex.clear()
        }
        byIndex.keys.removeAll { it >= info.totalItemsCount }
        for (item in info.visibleItemsInfo) byIndex[item.index] = item.key to item.size
    }

    fun height(index: Int): Float? = byIndex[index]?.second?.toFloat()

    fun total(indices: IntRange): Float? {
        var sum = 0
        for (i in indices) sum += byIndex[i]?.second ?: return null
        return sum.toFloat()
    }
}

private fun LazyListLayoutInfo.toSyncViewport(heights: ItemHeights): SyncViewport {
    heights.record(this)
    val first = visibleItemsInfo.firstOrNull()?.index ?: 0
    val last = visibleItemsInfo.lastOrNull()?.index ?: (totalItemsCount - 1)
    return SyncViewport(
        startOffset = viewportStartOffset.toFloat(),
        endOffset = viewportEndOffset.toFloat(),
        afterContentPadding = afterContentPadding.toFloat(),
        items = visibleItemsInfo.map { SyncItem(it.index, it.offset.toFloat(), it.size.toFloat()) },
        totalItemsCount = totalItemsCount,
        heightAbove = heights.total(0 until first),
        heightBelow = heights.total(last + 1 until totalItemsCount),
    )
}
