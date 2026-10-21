package com.garfiec.librechat.feature.chat.util

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Different-rate synchronized scrolling for Compare's two panes, as in a side-by-side diff
 * (Meld, CodeMirror's merge view, JetBrains): the panes scroll together, and where one side's
 * content is taller the other moves proportionally slower, so corresponding parts stay level.
 *
 * Both panes list the same thread, item for item (`buildComparisonDisplayMessages` is a 1:1 map),
 * so item `i` is the anchor region in both. A point at a fraction of the way through item `i` on
 * one side maps to the same fraction of item `i` on the other. Everything is in each list's own
 * layout coordinates: a lazy list knows nothing of what is off screen, so no total height is used.
 */

/** One laid-out item: [offset] is its top in the list's layout coordinates, [size] its height (px). */
internal data class SyncItem(val index: Int, val offset: Float, val size: Float) {
    val end: Float get() = offset + size
}

/**
 * A pane's visible window, from `LazyListLayoutInfo`: [startOffset]/[endOffset] are the viewport
 * edges (content padding included), [items] the visible items in order.
 */
internal data class SyncViewport(
    val startOffset: Float,
    val endOffset: Float,
    val afterContentPadding: Float,
    val items: List<SyncItem>,
    val totalItemsCount: Int,
    /**
     * Total height of the items above the first visible one, when every one of them has been
     * measured; null when not. Without it the distance to the top is unknown as soon as the first
     * item scrolls off, and a one-line first prompt scrolls off well inside the top ramp: the held
     * line would snap from near the top to [READING_LINE] in one frame, and the other pane jump.
     */
    val heightAbove: Float? = if (items.firstOrNull()?.index == 0) 0f else null,
    /** Total height of the items below the last visible one, when every one has been measured. */
    val heightBelow: Float? = if (items.lastOrNull()?.index == totalItemsCount - 1) 0f else null,
) {
    val height: Float get() = endOffset - startOffset

    /** How far the list is scrolled from its very top, or null when that's unknown (so: far). */
    fun distanceFromTop(): Float? {
        val first = items.firstOrNull() ?: return null
        return heightAbove?.let { max(0f, it - first.offset) }
    }

    /** How far the list can still scroll toward its end, or null when that's unknown (so: far). */
    fun distanceToEnd(): Float? {
        val last = items.lastOrNull() ?: return null
        return heightBelow?.let { max(0f, last.end + it + afterContentPadding - endOffset) }
    }

    fun referenceLine(syncpoint: Float): Float = startOffset + syncpoint * height
}

/**
 * Where on the viewport the two panes are held level, as a fraction of its height. Meld's ramp
 * around [READING_LINE] rather than Meld's middle: the reading line while far from either end,
 * easing to the top over the first [READING_LINE] of a screen and to the bottom over the rest, so
 * both panes reach their top together and their end together.
 */
internal fun syncpoint(distanceFromTop: Float?, distanceToEnd: Float?, viewportHeight: Float): Float {
    if (viewportHeight <= 0f) return 0f
    val above = READING_LINE * viewportHeight
    val below = viewportHeight - above
    val top = if (distanceFromTop == null) 1f else min(1f, distanceFromTop / above)
    val end = if (distanceToEnd == null) 0f else max(0f, 1f - distanceToEnd / below)
    return (READING_LINE * top + (1f - READING_LINE) * end).coerceIn(0f, 1f)
}

/**
 * Where the panes are held level mid-thread, as a fraction of the viewport. A quarter of the way
 * down, rather than Meld's middle: a reader reading downward sits in the upper part of the
 * screen, and a lower line leaves what they read next misaligned below it.
 */
internal const val READING_LINE = 1f / 4f

/**
 * A point in the thread: [fraction] of the way through item [index], plus [overshootPx] beyond
 * the item when the point lies in the content padding before the first item or after the last.
 * Padding is the same in both panes, so the overshoot carries over unscaled.
 */
internal data class AnchorPosition(val index: Int, val fraction: Float, val overshootPx: Float = 0f)

internal fun SyncViewport.locate(line: Float): AnchorPosition? {
    if (items.isEmpty()) return null
    val first = items.first()
    if (line < first.offset) return AnchorPosition(first.index, 0f, line - first.offset)
    val item = items.lastOrNull { it.offset <= line } ?: first
    if (line >= item.end) return AnchorPosition(item.index, 1f, line - item.end)
    val fraction = if (item.size > 0f) (line - item.offset) / item.size else 0f
    return AnchorPosition(item.index, fraction)
}

internal sealed interface FollowerMove {
    /** Scroll the follower by [px]; its target item is laid out. */
    data class By(val px: Float) : FollowerMove

    /** The target item isn't laid out: jump to it, then refine with [By] once it is. */
    data class ToItem(val index: Int, val scrollOffsetPx: Int) : FollowerMove

    data object None : FollowerMove
}

/**
 * How far to move [follower] so it sits level with [master] — the same point of the thread on the
 * same line of both viewports. [estimateSize] is the last measured height of a follower item that
 * isn't laid out, used only to aim the jump that brings it into view.
 *
 * [masterDirection] is the sign of the master's latest scroll (0 when unknown). A move against it
 * is dropped: within the end ramps a follower whose region is under half the master's height
 * would otherwise creep backward while the master scrolls forward (Meld does). The ramp still
 * lands both ends together, so nothing is lost by holding still instead.
 */
internal fun followerMove(
    master: SyncViewport,
    follower: SyncViewport,
    masterDirection: Int = 0,
    tolerancePx: Float = 1f,
    estimateSize: (Int) -> Float? = { null },
): FollowerMove {
    val move = unguardedFollowerMove(master, follower, tolerancePx, estimateSize)
    return if (move is FollowerMove.By && masterDirection != 0 && move.px * masterDirection < 0) {
        FollowerMove.None
    } else {
        move
    }
}

private fun unguardedFollowerMove(
    master: SyncViewport,
    follower: SyncViewport,
    tolerancePx: Float,
    estimateSize: (Int) -> Float?,
): FollowerMove {
    if (follower.totalItemsCount == 0 || follower.height <= 0f) return FollowerMove.None
    val sp = syncpoint(master.distanceFromTop(), master.distanceToEnd(), master.height)
    val anchor = master.locate(master.referenceLine(sp)) ?: return FollowerMove.None
    val followerLine = follower.referenceLine(sp)

    // A point past the follower's last item (its streaming tail holds fewer items) maps to its end.
    if (anchor.index >= follower.totalItemsCount) {
        val last = follower.items.lastOrNull() ?: return FollowerMove.None
        if (last.index != follower.totalItemsCount - 1) {
            return FollowerMove.ToItem(follower.totalItemsCount - 1, Int.MAX_VALUE)
        }
        return by(last.end + anchor.overshootPx.coerceAtLeast(0f) - followerLine, tolerancePx)
    }

    val target = follower.items.firstOrNull { it.index == anchor.index }
    if (target != null) {
        return by(target.offset + anchor.fraction * target.size + anchor.overshootPx - followerLine, tolerancePx)
    }
    // scrollToItem places the item's top at -scrollOffset. The jump must land with the item on
    // screen, or the next pass finds it missing again and jumps again — each jump forcing a layout
    // that starts the next pass, with the main thread never let go. So aim within the item only
    // with its measured height; unmeasured, land it at the top and let the next pass refine.
    val size = estimateSize(anchor.index) ?: return FollowerMove.ToItem(anchor.index, 0)
    val itemTopOnViewport = followerLine - anchor.fraction * size - anchor.overshootPx
    val offset = (-itemTopOnViewport).roundToInt().coerceIn(0, (size.roundToInt() - 1).coerceAtLeast(0))
    return FollowerMove.ToItem(anchor.index, offset)
}

private fun by(delta: Float, tolerancePx: Float): FollowerMove =
    if (abs(delta) <= tolerancePx) FollowerMove.None else FollowerMove.By(delta)
