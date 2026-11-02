package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.pdf.PdfDocumentHolder
import com.garfiec.librechat.core.ui.pdf.PdfPageContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * Android PDF surface backed by [android.graphics.pdf.PdfRenderer]. The bytes are staged to a
 * cache file (PdfRenderer needs a seekable ParcelFileDescriptor), then each page is rendered to a
 * bitmap on demand as it scrolls into view — off-screen pages are disposed by the [LazyColumn], so
 * memory tracks the visible window rather than the whole document.
 *
 * Zoom belongs to the whole document ([DocumentZoom]), from [MIN_ZOOM] (several pages on screen) to
 * [MAX_ZOOM]. It changes the width pages are laid out and rendered at, so a zoomed page is sharp up
 * to the holder's bitmap cap. Gestures, all on the container so they beat the list:
 * - Two fingers zoom around their centroid and pan.
 * - Double-tap and drag zooms with one finger around the tap, as Google Maps does: down zooms in,
 *   up zooms out. A double-tap alone zooms in, or back to fit width when zoomed.
 * - One finger pans freely, both axes, with a vertical fling. A drag past a page's side edge or
 *   the document's top or bottom moves the document on the screen ([DocumentZoom.panX],
 *   [DocumentZoom.panY]), so it can sit anywhere; the offset stays until dragged back.
 *
 * While fingers are down the zoom is only drawn ([DocumentZoom.gestureScale]); the layout follows on
 * release ([DocumentZoom.commit]), so pages re-render once per gesture rather than every frame.
 */
@Composable
actual fun PdfViewer(bytes: ByteArray, onRenderError: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val currentOnRenderError by rememberUpdatedState(onRenderError)

    // The producer owns the holder's lifecycle: it closes the same instance it published, via
    // awaitDispose. NonCancellable so a create() finishing after disposal is still closed rather
    // than leaking its fd/renderer.
    val holder by produceState<PdfDocumentHolder?>(null, bytes) {
        val fresh = withContext(Dispatchers.IO + NonCancellable) {
            PdfDocumentHolder.create(context, bytes)
        }
        value = fresh
        if (fresh == null) currentOnRenderError()
        awaitDispose { fresh?.close() }
    }

    val doc = holder ?: return
    val listState = rememberLazyListState()
    val zoom = remember(doc, listState) { DocumentZoom(listState) }
    val scope = rememberCoroutineScope()
    val fling = ScrollableDefaults.flingBehavior()
    val currentFling by rememberUpdatedState(fling)
    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { zoom.viewport = it }
            // Before the graphicsLayer, so the gestures read the finger in layout space.
            .pointerInput(zoom) { detectDoubleTapZoom(zoom) }
            .pointerInput(zoom) { detectPinchZoom(zoom) }
            .pointerInput(zoom) { detectZoomedDrag(zoom, { currentFling }, scope) }
            .graphicsLayer {
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = zoom.gestureScale
                scaleY = zoom.gestureScale
                translationX = zoom.gestureOffset.x
                translationY = zoom.gestureOffset.y
            },
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.shiftedList(zoom),
            verticalArrangement = Arrangement.spacedBy(PAGE_GAP),
            contentPadding = PaddingValues(vertical = PAGE_GAP),
        ) {
            items(count = doc.pageCount, key = { it }, contentType = { "pdf_page" }) { index ->
                Box(modifier = Modifier.zoomedPage(zoom)) {
                    PdfPageContent(doc = doc, index = index)
                }
            }
        }
    }
}

private const val MIN_ZOOM = 0.25f
private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2f

/** The share of the screen a document can always be moved by, past its edges or ends. */
private const val MIN_SCREEN_SHIFT = 0.5f
private val PAGE_GAP = 8.dp

/** Finger travel, in dp, that doubles (dragging down) or halves (up) the zoom of a double-tap drag. */
private const val DOUBLE_TAP_DRAG_DP_PER_DOUBLING = 120f

/** How far, in dp, the second tap may land from the first and still count as a double-tap. */
private const val DOUBLE_TAP_RADIUS_DP = 48f

/**
 * Lays a page out [DocumentZoom.scale] times the viewport's width, centred and shifted by
 * [DocumentZoom.panX]. It reports the viewport's width either way, so the list's own
 * layout is unchanged. Pan is read at placement, so panning re-places without re-measuring.
 */
private fun Modifier.zoomedPage(zoom: DocumentZoom): Modifier = layout { measurable, constraints ->
    val viewport = constraints.maxWidth
    val width = (viewport * zoom.scale).roundToInt().coerceAtLeast(1)
    val placeable = measurable.measure(Constraints.fixedWidth(width))
    layout(viewport, placeable.height) {
        placeable.placeRelative(((viewport - width) / 2f + zoom.panX).roundToInt(), 0)
    }
}

/**
 * Places the list [DocumentZoom.panY] down the screen. Shifted up, it grows by as much, so its
 * bottom stays at the screen's and the pages below still compose.
 */
private fun Modifier.shiftedList(zoom: DocumentZoom): Modifier = layout { measurable, constraints ->
    val shift = zoom.panY.roundToInt()
    val height = constraints.maxHeight + (-shift).coerceAtLeast(0)
    val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, height))
    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, shift) }
}

/**
 * The document's zoom: [scale], [panX] and [panY] are laid out; [gestureScale] and [gestureOffset] are the
 * in-flight gesture, drawn over the laid-out list (screen = layout × gestureScale + gestureOffset)
 * until [commit] folds them into the layout.
 */
@Stable
private class DocumentZoom(private val list: LazyListState) {
    var viewport by mutableStateOf(IntSize.Zero)
    var scale by mutableFloatStateOf(1f)
        private set
    var panX by mutableFloatStateOf(0f)
        private set

    /** How far the whole list sits down the screen (up when negative). */
    var panY by mutableFloatStateOf(0f)
        private set
    var gestureScale by mutableFloatStateOf(1f)
        private set
    var gestureOffset by mutableStateOf(Offset.Zero)
        private set

    /** Scales the in-flight gesture by [factor] around the screen point [focus], then moves it by [pan]. */
    fun gestureBy(factor: Float, focus: Offset, pan: Offset = Offset.Zero) {
        val next = (scale * gestureScale * factor).coerceIn(MIN_ZOOM, MAX_ZOOM) / scale
        gestureOffset = focus - (focus - gestureOffset) * (next / gestureScale) + pan
        gestureScale = next
    }

    /** Ends the in-flight gesture, keeping what is under [focus] (a screen point) where it is. */
    fun commitGesture(focus: Offset) {
        commit(scale * gestureScale, from = (focus - gestureOffset) / gestureScale, to = focus)
    }

    /**
     * Lays the document out at [target], so that the layout point [from] lands on the screen point
     * [to]. Vertically that is a scroll to the page under [from], at the same fraction down it;
     * horizontally, the pan. All of it lands in one frame: the gesture reset, the new width and the
     * scroll request are read by the same measure.
     */
    fun commit(target: Float, from: Offset, to: Offset) {
        val next = target.coerceIn(MIN_ZOOM, MAX_ZOOM)
        val width = viewport.width.toFloat()
        // The list sits panY down the screen, and item offsets start below its top content padding,
        // so an item's coordinates are the screen's less both.
        val padding = list.layoutInfo.beforeContentPadding
        val fromY = from.y - panY - padding
        val page = list.layoutInfo.visibleItemsInfo.let { items ->
            items.firstOrNull { fromY < it.offset + it.size } ?: items.lastOrNull()
        }
        val nextPanY = clampPanY(panY, next)
        if (page != null && page.size > 0 && width > 0f) {
            val down = (fromY - page.offset) / page.size
            val newHeight = page.size * next / scale
            list.requestScrollToItem(page.index, (down * newHeight - (to.y - nextPanY - padding)).roundToInt())

            val oldLeft = (width - width * scale) / 2f + panX
            val across = (from.x - oldLeft) / (width * scale)
            val newLeft = to.x - across * width * next
            scale = next
            panX = clampPan(newLeft - (width - width * next) / 2f)
        } else {
            scale = next
            panX = clampPan(panX)
        }
        panY = nextPanY
        gestureScale = 1f
        gestureOffset = Offset.Zero
    }

    fun panBy(dx: Float) {
        panX = clampPan(panX + dx)
    }

    /**
     * Moves the document [dy] with the finger. An offset is taken back first; then the list
     * scrolls; what it can't scroll, at either end of the document, shifts it on the screen.
     */
    fun dragBy(dy: Float) {
        var rest = dy
        if (panY != 0f && sign(rest) == -sign(panY)) {
            val back = if (abs(rest) > abs(panY)) -panY else rest
            panY += back
            rest -= back
        }
        if (rest != 0f) rest += list.dispatchRawDelta(-rest)
        if (rest != 0f) panY = clampPanY(panY + rest, scale)
    }

    suspend fun fling(velocity: Float, behavior: FlingBehavior) {
        list.scroll { with(behavior) { performFling(velocity) } }
    }

    /** Half the screen, or more when zoomed out: a quarter-size document can travel three quarters. */
    private fun clampPanY(value: Float, scale: Float): Float {
        val max = viewport.height * (1f - scale).coerceAtLeast(MIN_SCREEN_SHIFT)
        return value.coerceIn(-max, max)
    }

    /**
     * A page narrower than the screen slides until its edge meets the screen's. One as wide or
     * wider can also bring its edge in, up to half the screen.
     */
    private fun clampPan(value: Float): Float {
        val overhang = viewport.width * (scale - 1f) / 2f
        val max = if (scale < 1f) -overhang else overhang + viewport.width * MIN_SCREEN_SHIFT
        return value.coerceIn(-max, max)
    }
}

/**
 * Double-tap and drag zooms; a double-tap alone toggles between fit width and [DOUBLE_TAP_ZOOM].
 * Runs on the Initial pass and consumes the second press, so neither the list nor the drag sees it.
 */
private suspend fun PointerInputScope.detectDoubleTapZoom(zoom: DocumentZoom) = awaitEachGesture {
    val first = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
    val firstUp = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        waitForUpOrCancellation(PointerEventPass.Initial)
    } ?: return@awaitEachGesture
    if ((firstUp.position - first.position).getDistance() > viewConfiguration.touchSlop) return@awaitEachGesture

    val second = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
    } ?: return@awaitEachGesture
    if ((second.position - first.position).getDistance() > DOUBLE_TAP_RADIUS_DP.dp.toPx()) return@awaitEachGesture
    second.consume()

    val focus = second.position
    val pxPerDoubling = DOUBLE_TAP_DRAG_DP_PER_DOUBLING.dp.toPx()
    var dragging = false
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val change = event.changes.firstOrNull { it.id == second.id }
        if (change == null || event.changes.size > 1) {
            // A second finger hands the gesture to the pinch.
            if (dragging) zoom.commitGesture(focus)
            break
        }
        if (!change.pressed) {
            if (dragging) {
                zoom.commitGesture(focus)
            } else {
                val target = if (zoom.scale == 1f) DOUBLE_TAP_ZOOM else 1f
                zoom.commit(target, from = focus, to = focus)
            }
            change.consume()
            break
        }
        if (!dragging && abs(change.position.y - focus.y) > viewConfiguration.touchSlop) dragging = true
        if (dragging) {
            val dy = change.position.y - change.previousPosition.y
            zoom.gestureBy(2f.pow(dy / pxPerDoubling), focus)
        }
        change.consume()
    }
}

private suspend fun PointerInputScope.detectPinchZoom(zoom: DocumentZoom) = awaitEachGesture {
    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
    var pinching = false
    var focus = Offset.Zero
    do {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        if (event.changes.count { it.pressed } >= 2) {
            pinching = true
            focus = event.calculateCentroid()
            zoom.gestureBy(event.calculateZoom(), focus, event.calculatePan())
            event.changes.forEach { if (it.positionChanged()) it.consume() }
        } else if (pinching) {
            pinching = false
            zoom.commitGesture(focus)
        }
    } while (event.changes.any { it.pressed })
    if (pinching) zoom.commitGesture(focus)
}

/**
 * One finger pans across and scrolls the list, both at once, then flings the list. Claimed past touch slop
 * on the Initial pass, so the list's own drag never starts.
 */
private suspend fun PointerInputScope.detectZoomedDrag(
    zoom: DocumentZoom,
    fling: () -> FlingBehavior,
    scope: CoroutineScope,
) {
    var flingJob: Job? = null
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        flingJob?.cancel()
        val velocity = VelocityTracker()
        var travel = Offset.Zero
        var claimed = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id }
            if (change == null || event.changes.size > 1 || change.isConsumed) return@awaitEachGesture
            if (!change.pressed) break
            val delta = change.positionChange()
            if (!claimed) {
                travel += delta
                claimed = travel.getDistance() > viewConfiguration.touchSlop
            }
            if (claimed) {
                velocity.addPosition(change.uptimeMillis, change.position)
                zoom.panBy(delta.x)
                zoom.dragBy(delta.y)
                change.consume()
            }
        }
        if (claimed) {
            val fly = -velocity.calculateVelocity().y
            flingJob = scope.launch { zoom.fling(fly, fling()) }
        }
    }
}
