package com.garfiec.librechat.feature.conversations.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/** One laid-out item of the lazy layout a [LazyReorderState] drags through, along its vertical axis. */
internal class ReorderSlot(val key: Any, val index: Int, val offset: Int, val size: Int)

/** What a [LazyReorderState] needs from a `LazyColumn` or a `LazyVerticalGrid`. */
internal interface ReorderLayout {
    val visibleItems: List<ReorderSlot>
    val viewportStartOffset: Int
    val viewportEndOffset: Int
    val firstVisibleItemIndex: Int
    val firstVisibleItemScrollOffset: Int

    /** Whether an item can be picked up now. A grid only reorders while it shows a single column. */
    val canReorder: Boolean get() = true

    suspend fun scrollBy(px: Float)

    fun requestScrollToItem(index: Int, scrollOffset: Int)
}

private class ListReorderLayout(private val state: LazyListState) : ReorderLayout {
    override val visibleItems: List<ReorderSlot>
        get() = state.layoutInfo.visibleItemsInfo.map { ReorderSlot(it.key, it.index, it.offset, it.size) }
    override val viewportStartOffset: Int get() = state.layoutInfo.viewportStartOffset
    override val viewportEndOffset: Int get() = state.layoutInfo.viewportEndOffset
    override val firstVisibleItemIndex: Int get() = state.firstVisibleItemIndex
    override val firstVisibleItemScrollOffset: Int get() = state.firstVisibleItemScrollOffset
    override suspend fun scrollBy(px: Float) {
        state.scrollBy(px)
    }
    override fun requestScrollToItem(index: Int, scrollOffset: Int) = state.requestScrollToItem(index, scrollOffset)
}

private class GridReorderLayout(private val state: LazyGridState) : ReorderLayout {
    override val visibleItems: List<ReorderSlot>
        get() = state.layoutInfo.visibleItemsInfo.map { ReorderSlot(it.key, it.index, it.offset.y, it.size.height) }
    override val viewportStartOffset: Int get() = state.layoutInfo.viewportStartOffset
    override val viewportEndOffset: Int get() = state.layoutInfo.viewportEndOffset
    override val firstVisibleItemIndex: Int get() = state.firstVisibleItemIndex
    override val firstVisibleItemScrollOffset: Int get() = state.firstVisibleItemScrollOffset

    // Two columns would need a drag in two dimensions; there the Move up / Move down actions remain.
    override val canReorder: Boolean get() = state.layoutInfo.maxSpan == 1
    override suspend fun scrollBy(px: Float) {
        state.scrollBy(px)
    }
    override fun requestScrollToItem(index: Int, scrollOffset: Int) = state.requestScrollToItem(index, scrollOffset)
}

/**
 * Long-press-and-drag reordering of the items of a lazy layout whose keys are [ids] (the drawer's
 * project folders, the Bookmarks index's cards). The keys must be strings no other item of the
 * layout uses.
 *
 * While an item is held the layout renders from [order], a local copy, so a reload landing mid-drag
 * cannot snap the list back under the finger. The held item is drawn at the finger
 * ([translationOf]) while its slot moves through the list; the others slide aside through
 * `animateItem`. Nothing is reported until the drop, and then only if the order changed.
 */
@Stable
internal class LazyReorderState(
    private val layout: ReorderLayout,
    private val scope: CoroutineScope,
    private val autoScrollEdgePx: Float,
) {
    /** The order shown while dragging and until the drop has been applied; null otherwise. */
    var order by mutableStateOf<List<String>?>(null)
        private set

    /** The item under the finger, or the one settling back into its slot after a drop. */
    var heldId by mutableStateOf<String?>(null)
        private set

    private var dragging by mutableStateOf(false)
    private var startOffset = 0
    private var dragDelta by mutableFloatStateOf(0f)
    private val settle = Animatable(0f)

    val isDragging: Boolean get() = dragging

    /** Whether a hold would pick an item up now; see [ReorderLayout.canReorder]. */
    val canReorder: Boolean get() = layout.canReorder

    fun shownIds(ids: List<String>): List<String> = order ?: ids

    /** How far the held item is drawn from its laid-out slot, vertically. */
    fun translationOf(id: String): Float {
        if (id != heldId) return 0f
        if (!dragging) return settle.value
        val item = itemFor(id) ?: return 0f
        return startOffset + dragDelta - item.offset
    }

    private val headerHeights = mutableMapOf<String, Int>()

    /**
     * The height of the part of item [id] a hold picks it up from, when that is less than the whole
     * item: a drawer folder's own row, without the chats an open folder shows under it.
     */
    fun onHeaderSizeChange(id: String, height: Int) {
        headerHeights[id] = height
    }

    /** Picks up the item under [position], if it is one of [ids] and the layout allows it now. */
    fun start(position: Offset, ids: List<String>): Boolean {
        if (!layout.canReorder) return false
        val y = position.y.toInt()
        val item = layout.visibleItems.firstOrNull {
            y in it.offset until it.offset + it.size
        } ?: return false
        val id = item.key as? String ?: return false
        if (id !in ids) return false
        if (y >= item.offset + (headerHeights[id] ?: item.size)) return false
        scope.launch { settle.snapTo(0f) }
        order = ids
        heldId = id
        startOffset = item.offset
        dragDelta = 0f
        dragging = true
        return true
    }

    fun drag(dy: Float) {
        dragDelta += dy
        swapIfPast()
    }

    /** Returns the new order if the drop changed it. */
    fun end(ids: List<String>): List<String>? {
        val id = heldId ?: return null
        val dropped = order
        val offset = translationOf(id)
        // Undispatched, so the item is already at the finger when the next frame reads the settle.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            settle.snapTo(offset)
            dragging = false
            settle.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
            if (!dragging) heldId = null
        }
        if (dropped == null || dropped == ids) {
            order = null
            return null
        }
        // Normally the list arrives in the dropped order and onItemsChanged clears the snapshot.
        // A drop the ViewModel finds already applied changes nothing, and nothing would clear it;
        // two frames is past the list update, so clearing then can't flash the pre-drop order.
        scope.launch {
            repeat(2) { withFrameNanos { } }
            if (!dragging && order === dropped) order = null
        }
        return dropped
    }

    fun cancel() {
        dragging = false
        heldId = null
        order = null
    }

    /** The drop has reached the list (or something newer replaced it): show the list's own order. */
    fun onItemsChanged() {
        if (!dragging) order = null
    }

    suspend fun autoScrollWhileDragging() {
        while (dragging) {
            val step = edgeScrollStep()
            if (step != 0f) {
                layout.scrollBy(step)
                swapIfPast()
            }
            withFrameNanos { }
        }
    }

    private fun edgeScrollStep(): Float {
        val id = heldId ?: return 0f
        val item = itemFor(id) ?: return 0f
        val top = item.offset + translationOf(id)
        val bottom = top + item.size
        val viewportStart = layout.viewportStartOffset
        val viewportEnd = layout.viewportEndOffset
        return when {
            top < viewportStart + autoScrollEdgePx -> -edgeSpeed(viewportStart + autoScrollEdgePx - top)
            bottom > viewportEnd - autoScrollEdgePx -> edgeSpeed(bottom - (viewportEnd - autoScrollEdgePx))
            else -> 0f
        }
    }

    private fun edgeSpeed(depth: Float): Float = (depth / autoScrollEdgePx).coerceIn(0f, 1f) * MAX_SCROLL_PX_PER_FRAME

    private fun swapIfPast() {
        val id = heldId ?: return
        val current = order ?: return
        val items = layout.visibleItems
        val held = items.firstOrNull { it.key == id } ?: return
        val middle = held.offset + translationOf(id) + held.size / 2f
        val target = items.firstOrNull { item ->
            val key = item.key as? String
            key != null && key != id && key in current && middle >= item.offset && middle < item.offset + item.size
        } ?: return
        val from = current.indexOf(id)
        val to = current.indexOf(target.key as String)
        // The layout still shows the order from before the last swap (an edge scroll shifts it
        // without a remeasure, and a second move can land in the same frame): swapping again would
        // put the item straight back.
        if ((held.index < target.index) != (from < to)) return
        // A lazy layout anchors its scroll position on the first visible item's key; moving that item
        // would drag the whole list along with it. Re-pinning the index keeps the viewport still.
        // requestScrollToItem applies at the next measure, with the move, and takes no scroll lock,
        // so it cannot cancel (or be cancelled by) the edge auto-scroll.
        val firstIndex = layout.firstVisibleItemIndex
        val firstOffset = layout.firstVisibleItemScrollOffset
        val anchorMoves = held.index == firstIndex || target.index == firstIndex
        order = current.toMutableList().apply { add(to, removeAt(from)) }
        if (anchorMoves) layout.requestScrollToItem(firstIndex, firstOffset)
    }

    private fun itemFor(id: String): ReorderSlot? = layout.visibleItems.firstOrNull { it.key == id }

    private companion object {
        const val MAX_SCROLL_PX_PER_FRAME = 24f
    }
}

@Composable
internal fun rememberLazyReorderState(listState: LazyListState): LazyReorderState =
    rememberLazyReorderState(listState) { ListReorderLayout(listState) }

@Composable
internal fun rememberLazyReorderState(gridState: LazyGridState): LazyReorderState =
    rememberLazyReorderState(gridState) { GridReorderLayout(gridState) }

@Composable
private fun rememberLazyReorderState(key: Any, layout: () -> ReorderLayout): LazyReorderState {
    val scope = rememberCoroutineScope()
    val edgePx = with(LocalDensity.current) { 48.dp.toPx() }
    val state = remember(key, scope, edgePx) { LazyReorderState(layout(), scope, edgePx) }
    LaunchedEffect(state, state.isDragging) {
        if (state.isDragging) state.autoScrollWhileDragging()
    }
    return state
}

/**
 * Long-press anywhere on an item to pick it up. Sits on the lazy layout rather than on each item,
 * so the gesture survives the item being moved through the list (a keyed item's own pointer input
 * can be torn down as it moves). [enabled] false leaves every gesture to the items and the list.
 *
 * Once an item is held, every change is consumed in the Initial pass, before the items see it: the
 * item's own click would otherwise fire on release (reopening the drawer folder just picked up, or
 * opening the card), and the list and the drawer would scroll or swipe under the drag.
 */
internal fun Modifier.lazyReorder(
    state: LazyReorderState,
    ids: () -> List<String>,
    onPickUp: () -> Unit,
    onDrop: (List<String>) -> Unit,
    enabled: Boolean = true,
): Modifier = if (!enabled) {
    this
} else {
    pointerInput(state) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val longPress = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
            if (!state.start(longPress.position, ids())) return@awaitEachGesture
            longPress.consume()
            onPickUp()
            var dropped = false
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == longPress.id } ?: break
                    if (!change.pressed) {
                        change.consume()
                        dropped = true
                        state.end(ids())?.let(onDrop)
                        break
                    }
                    val dy = change.positionChange().y
                    change.consume()
                    if (dy != 0f) state.drag(dy)
                }
            } finally {
                // Lost pointer, or the gesture itself cancelled: put the item back, or it stays
                // raised over a list frozen on the drag snapshot.
                if (!dropped) state.cancel()
            }
        }
    }
}

@Composable
internal fun rememberPickUpHaptic(onPickUp: () -> Unit): () -> Unit {
    val haptics = LocalHapticFeedback.current
    val current by rememberUpdatedState(onPickUp)
    return remember(haptics) {
        {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            current()
        }
    }
}
