package com.garfiec.librechat.feature.conversations.drawer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
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

/**
 * **Switchboard-only.** Long-press-and-drag reordering of the drawer's project folders. LibreChat
 * keeps no project order; the result is saved on the device (see `ProjectRepository.saveProjectOrder`).
 *
 * While a folder is held the rows render from [order], a local copy, so a reload landing mid-drag
 * cannot snap the list back under the finger. The held row is drawn at the finger
 * ([translationOf]) while its slot moves through the list; the other folders slide aside through
 * `animateItem`. Nothing is reported until the drop, and then only if the order changed.
 */
@Stable
internal class ProjectFolderReorderState(
    private val listState: LazyListState,
    private val scope: CoroutineScope,
    private val autoScrollEdgePx: Float,
) {
    /** The order shown while dragging and until the drop has been applied; null otherwise. */
    var order by mutableStateOf<List<String>?>(null)
        private set

    /** The folder under the finger, or the one settling back into its slot after a drop. */
    var heldId by mutableStateOf<String?>(null)
        private set

    private var dragging by mutableStateOf(false)
    private var startOffset = 0
    private var dragDelta by mutableFloatStateOf(0f)
    private val settle = Animatable(0f)

    val isDragging: Boolean get() = dragging

    fun shownIds(projectIds: List<String>): List<String> = order ?: projectIds

    /** How far the held row is drawn from its laid-out slot. */
    fun translationOf(id: String): Float {
        if (id != heldId) return 0f
        if (!dragging) return settle.value
        val item = itemFor(id) ?: return 0f
        return startOffset + dragDelta - item.offset
    }

    private val headerHeights = mutableMapOf<String, Int>()

    /** The folder row's own height, without the chats an open folder shows under it. */
    fun onHeaderSizeChange(id: String, height: Int) {
        headerHeights[id] = height
    }

    /**
     * Picks up the folder whose row is under [position], if there is one. A press on one of an open
     * folder's chats is the chat's own long-press, not a pick-up.
     */
    fun start(position: Offset, projectIds: List<String>): Boolean {
        val y = position.y.toInt()
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull {
            y in it.offset until it.offset + it.size
        } ?: return false
        val id = item.key as? String ?: return false
        if (id !in projectIds) return false
        if (y >= item.offset + (headerHeights[id] ?: item.size)) return false
        scope.launch { settle.snapTo(0f) }
        order = projectIds
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
    fun end(projectIds: List<String>): List<String>? {
        val id = heldId ?: return null
        val dropped = order
        val offset = translationOf(id)
        // Undispatched, so the row is already at the finger when the next frame reads the settle.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            settle.snapTo(offset)
            dragging = false
            settle.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
            if (!dragging) heldId = null
        }
        if (dropped == null || dropped == projectIds) {
            order = null
            return null
        }
        // Normally the list arrives in the dropped order and onProjectsChanged clears the snapshot.
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
    fun onProjectsChanged() {
        if (!dragging) order = null
    }

    suspend fun autoScrollWhileDragging() {
        while (dragging) {
            val step = edgeScrollStep()
            if (step != 0f) {
                listState.scrollBy(step)
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
        val info = listState.layoutInfo
        val viewportStart = info.viewportStartOffset
        val viewportEnd = info.viewportEndOffset
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
        val held = itemFor(id) ?: return
        val middle = held.offset + translationOf(id) + held.size / 2f
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            val key = item.key as? String
            key != null && key != id && key in current && middle >= item.offset && middle < item.offset + item.size
        } ?: return
        val from = current.indexOf(id)
        val to = current.indexOf(target.key as String)
        // The layout still shows the order from before the last swap (an edge scroll shifts it
        // without a remeasure, and a second move can land in the same frame): swapping again would
        // put the row straight back.
        if ((held.index < target.index) != (from < to)) return
        // LazyColumn anchors its scroll position on the first visible item's key; moving that item
        // would drag the whole list along with it. Re-pinning the index keeps the viewport still.
        // requestScrollToItem applies at the next measure, with the move, and takes no scroll lock,
        // so it cannot cancel (or be cancelled by) the edge auto-scroll.
        val firstIndex = listState.firstVisibleItemIndex
        val firstOffset = listState.firstVisibleItemScrollOffset
        val anchorMoves = held.index == firstIndex || target.index == firstIndex
        order = current.toMutableList().apply { add(to, removeAt(from)) }
        if (anchorMoves) listState.requestScrollToItem(firstIndex, firstOffset)
    }

    private fun itemFor(id: String): LazyListItemInfo? =
        listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }

    private companion object {
        const val MAX_SCROLL_PX_PER_FRAME = 24f
    }
}

@Composable
internal fun rememberProjectFolderReorderState(listState: LazyListState): ProjectFolderReorderState {
    val scope = rememberCoroutineScope()
    val edgePx = with(LocalDensity.current) { 48.dp.toPx() }
    val state = remember(listState, scope, edgePx) { ProjectFolderReorderState(listState, scope, edgePx) }
    LaunchedEffect(state, state.isDragging) {
        if (state.isDragging) state.autoScrollWhileDragging()
    }
    return state
}

/**
 * Long-press anywhere on a folder row to pick it up. Sits on the LazyColumn rather than on each row,
 * so the gesture survives the row being moved through the list (a keyed row's own pointer input can
 * be torn down as it moves).
 *
 * Once a folder is held, every change is consumed in the Initial pass, before the rows see it: the
 * row's own click would otherwise fire on release and reopen the folder pick-up just closed, and
 * the list and the drawer would scroll or swipe under the drag.
 */
internal fun Modifier.projectFolderReorder(
    state: ProjectFolderReorderState,
    projectIds: () -> List<String>,
    onPickUp: () -> Unit,
    onDrop: (List<String>) -> Unit,
): Modifier = pointerInput(state) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val longPress = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
        if (!state.start(longPress.position, projectIds())) return@awaitEachGesture
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
                    state.end(projectIds())?.let(onDrop)
                    break
                }
                val dy = change.positionChange().y
                change.consume()
                if (dy != 0f) state.drag(dy)
            }
        } finally {
            // Lost pointer, or the gesture itself cancelled: put the row back, or it stays raised
            // over a list frozen on the drag snapshot.
            if (!dropped) state.cancel()
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
