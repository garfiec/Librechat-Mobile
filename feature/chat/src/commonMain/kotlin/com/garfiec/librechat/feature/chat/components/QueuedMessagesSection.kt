package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.cd_cancel_queued_message
import com.garfiec.librechat.feature.chat.resources.cd_reorder_queued_message
import com.garfiec.librechat.feature.chat.resources.queued_attachment_only
import com.garfiec.librechat.feature.chat.resources.queued_turn_needs_attention
import com.garfiec.librechat.feature.chat.viewmodel.QueuedMessage
import com.garfiec.librechat.feature.chat.viewmodel.QueuedTurnServerState
import kotlinx.coroutines.isActive
import org.jetbrains.compose.resources.stringResource

/**
 * The "ghost" queue pinned directly above the composer: dimmed previews of follow-up messages
 * the user has queued, waiting to auto-send (FIFO) as each reply completes. These are NOT part
 * of the message tree — they live purely in [QueuedMessage] UI state. Hosted by the chat input
 * (not the scrolling message list) so the list's auto-scroll-to-bottom can't make them bounce
 * as the reply streams.
 *
 * Tap a row to pull it back into the composer for editing; the × cancels it; long-press anywhere
 * on a row (the drag handle marks the affordance) and drag to reorder.
 *
 * The drag gesture is owned by the **container** Column — a single [pointerInput] that never gets
 * torn down as rows reorder — rather than by per-row nodes (which Compose detaches when a keyed
 * child moves, killing an in-progress gesture). The container hit-tests the grabbed row from
 * measured row bounds and swaps with a neighbour once the finger passes that neighbour's midpoint,
 * so variable-height rows reorder correctly. No external reorderable dependency.
 *
 * Once the long press has picked a row up, the gesture is the section's: the region's own scroll
 * never starts, so holding the row near an edge scrolls it ([reorderAutoScrollStep]).
 */
@Composable
fun QueuedMessagesSection(
    queuedMessages: List<QueuedMessage>,
    onEdit: (String) -> Unit,
    onCancel: (String) -> Unit,
    onReorder: (fromIndex: Int, toIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
    fontSizeMultiplier: Float = 1f,
    /** The scrolling region the section sits in, if any; a reorder drag auto-scrolls it at its edges. */
    reorderScroll: ReorderScrollRegion? = null,
) {
    if (queuedMessages.isEmpty()) return

    // Measured per-row geometry (localId -> top Y / height, in this Column's coordinate space),
    // recorded by each row via onGloballyPositioned and read by the container's drag hit-testing.
    val rowTops = remember { mutableStateMapOf<String, Float>() }
    val rowHeights = remember { mutableStateMapOf<String, Float>() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    // Live translation of the dragged row from its laid-out slot, so it follows the finger.
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    // Snapshot the list for the gesture closure so it always reads the current order.
    val currentQueue by rememberUpdatedState(queuedMessages)
    val currentOnReorder by rememberUpdatedState(onReorder)
    // The finger, in this Column's space, and where that space starts in the region's content.
    var pointerY by remember { mutableFloatStateOf(0f) }
    var sectionTop by remember { mutableFloatStateOf(0f) }

    // Swap with a neighbour once the dragged row crosses its midpoint. A server-owned neighbour is not
    // swappable (the reorder is refused for BOTH endpoints); settle the offset only after a swap that
    // happened, or the row jumps a full row height back under a still finger.
    fun settleSwap(direction: Float) {
        val id = draggingId ?: return
        val list = currentQueue
        val from = list.indexOfFirst { it.localId == id }
        if (from < 0) return
        if (direction > 0 && from < list.lastIndex && list[from + 1].server == null) {
            val nextHeight = rowHeights[list[from + 1].localId] ?: 0f
            if (dragOffsetY > nextHeight / 2f) {
                currentOnReorder(from, from + 1)
                dragOffsetY -= nextHeight
            }
        } else if (direction < 0 && from > 0 && list[from - 1].server == null) {
            val prevHeight = rowHeights[list[from - 1].localId] ?: 0f
            if (dragOffsetY < -prevHeight / 2f) {
                currentOnReorder(from, from - 1)
                dragOffsetY += prevHeight
            }
        }
    }

    if (draggingId != null && reorderScroll != null) {
        val density = LocalDensity.current
        val edgePx = with(density) { REORDER_EDGE.toPx() }
        val stepPx = with(density) { REORDER_MAX_STEP.toPx() }
        LaunchedEffect(reorderScroll, edgePx, stepPx) {
            while (isActive) {
                withFrameNanos { }
                val viewport = reorderScroll.viewport() ?: continue
                val step = reorderAutoScrollStep(
                    pointerY = sectionTop + pointerY,
                    viewportTop = viewport.start,
                    viewportBottom = viewport.endInclusive,
                    edge = edgePx,
                    maxStep = stepPx,
                )
                if (step == 0f) continue
                // The content moves under a still finger, so the row moves with it to stay there.
                val moved = reorderScroll.scrollContentBy(step)
                if (moved != 0f) {
                    pointerY += moved
                    dragOffsetY += moved
                    settleSwap(moved)
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .onPlaced { sectionTop = it.positionInParent().y }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        // The gesture lives on the container, so hiding a server-owned row's
                        // handle does not make it ungrabbable — the same check the reorder is
                        // refused by has to be made here, or the row is picked up and then lurches
                        // (see onDrag) against a reorder that silently never happens.
                        draggingId = currentQueue.firstOrNull { item ->
                            if (item.server != null) return@firstOrNull false
                            val top = rowTops[item.localId] ?: return@firstOrNull false
                            val height = rowHeights[item.localId] ?: 0f
                            offset.y >= top && offset.y < top + height
                        }?.localId
                        dragOffsetY = 0f
                        pointerY = offset.y
                    },
                    onDragEnd = {
                        draggingId = null
                        dragOffsetY = 0f
                    },
                    onDragCancel = {
                        draggingId = null
                        dragOffsetY = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        pointerY = change.position.y
                        if (draggingId == null) return@detectDragGesturesAfterLongPress
                        dragOffsetY += dragAmount.y
                        settleSwap(dragAmount.y)
                    },
                )
            },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        queuedMessages.forEach { item ->
            val isDragging = draggingId == item.localId
            QueuedMessageRow(
                item = item,
                isDragging = isDragging,
                fontSizeMultiplier = fontSizeMultiplier,
                onTap = { onEdit(item.localId) },
                onCancel = { onCancel(item.localId) },
                modifier = Modifier
                    .onGloballyPositioned { coords ->
                        rowTops[item.localId] = coords.positionInParent().y
                        rowHeights[item.localId] = coords.size.height.toFloat()
                    }
                    // Lift the dragged row above its peers and let it follow the finger.
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer { translationY = if (isDragging) dragOffsetY else 0f },
            )
        }
    }
}

@Composable
private fun QueuedMessageRow(
    item: QueuedMessage,
    isDragging: Boolean,
    fontSizeMultiplier: Float,
    onTap: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDragging) 0.95f else 0.55f),
        tonalElevation = if (isDragging) 6.dp else 0.dp,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onTap)
                .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        ) {
            // No handle on a server-owned row: the backend runs those in its own sequence, so
            // offering a drag would show an order it will not honour.
            if (item.server == null) {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = stringResource(Res.string.cd_reorder_queued_message),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
            }

            if (item.attachments.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Default.AttachFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 2.dp),
            ) {
                // Attachment-only queued items have no text — show a placeholder so the row isn't blank.
                val isPlaceholder = item.text.isBlank()
                Text(
                    text = if (isPlaceholder) stringResource(Res.string.queued_attachment_only) else item.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isPlaceholder) 0.6f else 0.85f),
                    fontSize = 14.sp * fontSizeMultiplier,
                )

                // A row the server refused, or one whose outcome was never revealed, blocks the whole
                // queue by design — it is held for the user rather than resent. Without a cue the
                // follow-up queue simply stops draining with nothing on screen to explain why. On a
                // line of its own, under the message, so neither squeezes the other.
                if (item.needsAttention) {
                    Text(
                        text = stringResource(Res.string.queued_turn_needs_attention),
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp * fontSizeMultiplier,
                    )
                }
            }

            IconButton(
                onClick = onCancel,
                modifier = Modifier.alpha(0.8f),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(Res.string.cd_cancel_queued_message),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * Whether this row is waiting on the user rather than on the server.
 *
 * `Rejected` is a refusal the server proved never committed; `Indeterminate` and an expired
 * `Uncertain` are outcomes it cannot resolve. All three stop the queue draining, so all three have
 * to say something.
 */
private val QueuedMessage.needsAttention: Boolean
    get() = when (server?.status) {
        QueuedTurnServerState.Status.Rejected,
        QueuedTurnServerState.Status.Indeterminate,
        -> true
        QueuedTurnServerState.Status.Uncertain -> server.reconciliationExpired
        else -> false
    }

/** A scrolling region a reorder drag can scroll. [reversed] is `verticalScroll(reverseScrolling = true)`. */
@Stable
class ReorderScrollRegion(val state: ScrollState, val reversed: Boolean) {
    /** The part of the region's content on screen, in content pixels; null before the first layout. */
    fun viewport(): ClosedFloatingPointRange<Float>? {
        val size = state.viewportSize
        if (size <= 0) return null
        val top = (if (reversed) state.maxValue - state.value else state.value).toFloat()
        return top..(top + size)
    }

    /** Scrolls so the content moves up by [dy] (positive shows what is below); returns how far it went. */
    suspend fun scrollContentBy(dy: Float): Float =
        if (reversed) -state.scrollBy(-dy) else state.scrollBy(dy)
}

/**
 * How far to scroll the region this frame while a row is held at [pointerY] (content pixels): up to
 * [maxStep] px, faster the deeper into the [edge] band at either end, or past it. Negative scrolls up.
 * Nothing in the middle, so a row dropped mid-region doesn't creep.
 */
internal fun reorderAutoScrollStep(
    pointerY: Float,
    viewportTop: Float,
    viewportBottom: Float,
    edge: Float,
    maxStep: Float,
): Float {
    if (edge <= 0f || viewportBottom <= viewportTop) return 0f
    val fromTop = pointerY - viewportTop
    val fromBottom = viewportBottom - pointerY
    return when {
        fromTop < edge && fromTop <= fromBottom -> -maxStep * ((edge - fromTop) / edge).coerceIn(0f, 1f)
        fromBottom < edge -> maxStep * ((edge - fromBottom) / edge).coerceIn(0f, 1f)
        else -> 0f
    }
}

/** How close to the region's edge a held row starts scrolling it, and its fastest step per frame. */
private val REORDER_EDGE = 32.dp
private val REORDER_MAX_STEP = 12.dp
