package com.garfiec.librechat.feature.chat.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.feature.chat.resources.*
import com.garfiec.librechat.feature.chat.resources.Res
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.roundToInt

// The chrome the two docked human-review panels share: [AskUserQuestionPanel] and
// [ToolApprovalPanel] page through the items of one pause the same way, so they look and behave
// the same way while doing it.

/** Same breakpoint the comparison panes use to go side by side. */
internal val PAUSE_PANEL_WIDE_MIN_WIDTH = 600.dp

/** How long a pick that moves the panel on stays visible first, so it is seen landing. */
internal const val PAUSE_PANEL_AUTO_ADVANCE_DELAY_MS = 250L

/**
 * Where a complete pick takes the panel once [PAUSE_PANEL_AUTO_ADVANCE_DELAY_MS] has passed, which
 * the layout decides.
 */
enum class PausePanelAutoAdvance {
    /** Compact: on to the next item still open, or submit once none is left. */
    AdvanceOrSubmit,

    /** Wide: on to the next tab only. The wide layout never submits on a pick: Continue does. */
    NextTab,
}

/** The item a panel shows: the recorded active one, or the first while none is recorded. */
internal fun pausePanelActiveIndex(ids: List<String>, activeId: String?): Int =
    ids.indexOfFirst { it == activeId }.coerceAtLeast(0)

/**
 * The compact layout's `‹ ● ● ━ ○ ›`: one dot per item, filled once the item is done, open while
 * it isn't, and stretched into a short bar for the one on screen. Dragging along the dots scrubs
 * through the items the way iOS's home-screen page dots do; the arrows step one at a time. Inert
 * while the panel is collapsed.
 */
@Composable
internal fun PausePanelPager(
    index: Int,
    count: Int,
    done: List<Boolean>,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
    previousLabel: String,
    nextLabel: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onSelect(index - 1) }, enabled = enabled && index > 0) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = previousLabel)
        }
        PausePanelPageDots(index = index, count = count, done = done, enabled = enabled, onSelect = onSelect)
        IconButton(onClick = { onSelect(index + 1) }, enabled = enabled && index < count - 1) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = nextLabel)
        }
    }
}

@Composable
private fun PausePanelPageDots(
    index: Int,
    count: Int,
    done: List<Boolean>,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val currentIndex by rememberUpdatedState(index)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val pageOf = stringResource(Res.string.ask_user_question_page_of, index + 1, count)
    // The dots are ~20dp apart, far too tight to drag across without overshooting. A scrub instead
    // moves one item per fixed step of travel from where it started, shrunk only when the whole
    // range would not fit across the window.
    val windowWidth = LocalWindowInfo.current.containerSize.width
    val scrubStep = with(LocalDensity.current) {
        val range = windowWidth * SCRUB_RANGE_WINDOW_FRACTION / (count - 1).coerceAtLeast(1)
        range.coerceIn(SCRUB_MIN_STEP.toPx(), SCRUB_STEP.toPx())
    }

    Box(
        modifier = Modifier
            .testTag(PAUSE_PANEL_PAGE_DOTS_TAG)
            .heightIn(min = 48.dp)
            .clearAndSetSemantics { stateDescription = pageOf }
            .pointerInput(count, enabled, isRtl, scrubStep) {
                if (!enabled) return@pointerInput
                // A tap lands on the dot under the finger: equal slots across the whole row.
                fun slotAt(x: Float): Int {
                    val slot = (x / size.width * count).toInt().coerceIn(0, count - 1)
                    return if (isRtl) count - 1 - slot else slot
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startIndex = currentIndex
                    var shown = currentIndex
                    var scrubbing = false
                    fun scrubTo(x: Float): Int {
                        val travel = (x - down.position.x) * if (isRtl) -1f else 1f
                        return (startIndex + (travel / scrubStep).roundToInt()).coerceIn(0, count - 1)
                    }
                    fun show(slot: Int) {
                        if (slot == shown) return
                        shown = slot
                        currentOnSelect(slot)
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            // The `n of N` label has no dots to land on, so a tap there does nothing.
                            if (!scrubbing && count <= MAX_PAGE_DOTS) show(slotAt(change.position.x))
                            break
                        }
                        if (!scrubbing && abs(change.position.x - down.position.x) > viewConfiguration.touchSlop) {
                            scrubbing = true
                        }
                        if (scrubbing) {
                            change.consume()
                            show(scrubTo(change.position.x))
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (count > MAX_PAGE_DOTS) {
            Text(
                text = pageOf,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                repeat(count) { i ->
                    Box(modifier = Modifier.width(PAGE_DOT_SLOT_WIDTH), contentAlignment = Alignment.Center) {
                        PageDot(current = i == index, done = done.getOrElse(i) { false })
                    }
                }
            }
        }
    }
}

@Composable
private fun PageDot(current: Boolean, done: Boolean) {
    val colors = MaterialTheme.colorScheme
    val width by animateDpAsState(
        targetValue = if (current) PAGE_BAR_WIDTH else PAGE_DOT_SIZE,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
    )
    val fill by animateColorAsState(
        when {
            done -> colors.primary
            current -> colors.onSurfaceVariant
            else -> Color.Transparent
        },
    )
    val stroke by animateColorAsState(if (current || done) Color.Transparent else colors.outline)
    Box(
        modifier = Modifier
            .size(width = width, height = PAGE_DOT_SIZE)
            .clip(CircleShape)
            .background(fill)
            .border(PAGE_DOT_STROKE, stroke, CircleShape),
    )
}

/**
 * The wide layout's page swipe, a small pager over [page]: a horizontal drag that starts on a
 * page's swipe handle (the modifier [page] receives, which it puts on its answer controls) moves
 * the whole page with its neighbour alongside, and a drag past a quarter of the width (or a fling)
 * carries the neighbour in. Past the first or last item it only stretches, so a swipe can never
 * submit. Any other page change (a tab, Back / Next, the auto-advance beat) slides the same way,
 * the old page leaving as the new one arrives.
 *
 * Only the handle starts a swipe: the question, its description and a tool's arguments are for
 * reading, and a text field's drags place its cursor.
 *
 * The area takes the height of the page on screen, so a taller neighbour is clipped while it slides.
 *
 * It remembers which page is on screen, so the caller must key it by its items: a pause replaced
 * by another (one pause's resume racing the run's next) would otherwise open on the old pause's
 * page — past the end of a shorter one.
 */
@Composable
internal fun PausePanelSwipeArea(
    index: Int,
    count: Int,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    page: @Composable (index: Int, swipeHandle: Modifier) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // The sign of an offset that points toward the next item: a leftward drag moves on in LTR.
    val forward = if (isRtl) 1f else -1f
    val flingThreshold = with(LocalDensity.current) { SWIPE_FLING_VELOCITY.toPx() }
    val currentCount by rememberUpdatedState(count)
    val currentOnSelect by rememberUpdatedState(onSelect)
    var width by remember { mutableIntStateOf(0) }
    // The page on screen. A swipe moves it ahead of [index] so the hand-over lands in the same frame
    // as the offset reset, instead of after a round trip through the caller.
    var shown by remember { mutableIntStateOf(index) }
    val offset = remember { mutableFloatStateOf(0f) }
    // The page sliding out during a change made elsewhere; otherwise the neighbour follows the drag.
    var leaving by remember { mutableStateOf<Int?>(null) }
    val neighbour by remember {
        derivedStateOf {
            leaving ?: offset.floatValue.takeIf { it != 0f }?.let { o ->
                (shown + if (o * forward > 0) 1 else -1).takeIf { it in 0 until currentCount }
            }
        }
    }
    val motion = remember { SwipeMotion() }

    LaunchedEffect(index) {
        if (index == shown) return@LaunchedEffect
        val from = shown
        val side = if (index > from) 1f else -1f
        motion.job?.cancel()
        Snapshot.withMutableSnapshot {
            shown = index
            leaving = from
            offset.floatValue = side * -forward * width
        }
        motion.job = scope.launch {
            try {
                animate(offset.floatValue, 0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { v, _ ->
                    offset.floatValue = v
                }
            } finally {
                leaving = null
            }
        }
    }

    fun settle(velocity: Float) {
        val w = width.toFloat()
        val towardNext = offset.floatValue * forward
        val step = when {
            towardNext > w * SWIPE_COMMIT_FRACTION || velocity * forward > flingThreshold -> 1
            towardNext < -w * SWIPE_COMMIT_FRACTION || velocity * forward < -flingThreshold -> -1
            else -> 0
        }
        val target = shown + step
        motion.job = scope.launch {
            if (step == 0 || target !in 0 until currentCount) {
                animate(offset.floatValue, 0f, initialVelocity = velocity, animationSpec = spring()) { v, _ ->
                    offset.floatValue = v
                }
                return@launch
            }
            animate(offset.floatValue, step * forward * w, initialVelocity = velocity, animationSpec = spring()) { v, _ ->
                offset.floatValue = v
            }
            // The neighbour is now exactly where the page was: make it the page, in one frame.
            Snapshot.withMutableSnapshot {
                shown = target
                offset.floatValue = 0f
            }
            currentOnSelect(target)
        }
    }

    fun drag(dx: Float) {
        val towardNext = (offset.floatValue + dx) * forward > 0
        val canMove = if (towardNext) shown < currentCount - 1 else shown > 0
        offset.floatValue += if (canMove) dx else dx * SWIPE_EDGE_RESISTANCE
    }

    val swipeHandle = Modifier
        .pointerInput(enabled, isRtl) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                // The handle moves with the page it drags, so its local positions stand still under
                // the finger; velocity is tracked on the finger's accumulated travel instead.
                val velocity = VelocityTracker()
                velocity.addPosition(down.uptimeMillis, Offset.Zero)
                var travel = Offset.Zero
                var fingerTravel = Offset.Zero
                var dragging = false
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        if (dragging) settle(velocity.calculateVelocity().x)
                        return@awaitEachGesture
                    }
                    // Claimed by a descendant before this crossed its slop: leave it alone.
                    if (!dragging && change.isConsumed) break
                    val delta = change.positionChange()
                    fingerTravel += delta
                    velocity.addPosition(change.uptimeMillis, fingerTravel)
                    if (!dragging) {
                        travel += delta
                        if (abs(travel.x) > viewConfiguration.touchSlop && abs(travel.x) > abs(travel.y)) {
                            dragging = true
                            motion.job?.cancel()
                        } else if (abs(travel.y) > viewConfiguration.touchSlop) {
                            break
                        }
                    }
                    if (dragging) {
                        change.consume()
                        drag(delta.x)
                    }
                }
                if (dragging) settle(0f)
            }
        }

    // Read once per composition: the placement below runs on every drag frame without recomposing.
    val pages = listOfNotNull(shown, neighbour)
    Layout(
        content = {
            // One call site in a loop, so a page keeps its composition (scroll position, focus) as
            // it moves between neighbour and shown.
            for (i in pages) {
                key(i) { Box { page(i, swipeHandle) } }
            }
        },
        modifier = modifier
            .testTag(PAUSE_PANEL_SWIPE_AREA_TAG)
            .onSizeChanged { width = it.width }
            .clipToBounds(),
    ) { measurables, constraints ->
        val pageConstraints = constraints.copy(minHeight = 0)
        val placeables = measurables.map { it.measure(pageConstraints) }
        val current = placeables.first()
        val w = constraints.maxWidth
        layout(w, current.height) {
            val o = offset.floatValue.roundToInt()
            current.place(o, 0)
            placeables.getOrNull(1)?.let { other ->
                val side = if (pages[1] > pages[0]) 1 else -1
                other.place(o + (side * -forward * w).roundToInt(), 0)
            }
        }
    }
}

/** [PausePanelSwipeArea]'s bookkeeping, none of which should recompose anything. */
private class SwipeMotion {
    var job: Job? = null
}

/**
 * The wide layout's one tab per item, marked once that item is done. Dragging along the tabs
 * selects the one under the finger, as the compact layout's dots do; a tap still selects one.
 *
 * The scrub takes the drag ahead of the row's own scroll, so a row too long to fit cannot be
 * dragged to scroll — it follows the selection instead, as it does for a tap.
 */
@Composable
internal fun PausePanelTabs(
    labels: List<String>,
    done: List<Boolean>,
    activeIndex: Int,
    onSelect: (Int) -> Unit,
    doneLabel: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val haptics = LocalHapticFeedback.current
    val currentIndex by rememberUpdatedState(activeIndex)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val tabLayout = remember { TabScrubLayout() }

    ScrollableTabRow(
        selectedTabIndex = activeIndex,
        edgePadding = 0.dp,
        containerColor = Color.Transparent,
        divider = {},
        modifier = modifier
            .testTag(PAUSE_PANEL_TABS_TAG)
            .onPlaced { tabLayout.row = it }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    // The Initial pass, so the scrub sees the drag before the row's scroll and the
                    // tabs' clicks do; nothing is consumed until it is a drag, so a tap still lands.
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var shown = currentIndex
                    var scrubbing = false
                    while (true) {
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes
                            .firstOrNull { it.id == down.id } ?: break
                        if (!scrubbing && abs(change.position.x - down.position.x) > viewConfiguration.touchSlop) {
                            scrubbing = true
                        }
                        if (scrubbing) change.consume()
                        if (!change.pressed) break
                        val tab = if (scrubbing) tabLayout.tabAt(change.position.x) else null
                        if (tab != null && tab != shown) {
                            shown = tab
                            currentOnSelect(tab)
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                }
            },
    ) {
        labels.forEachIndexed { index, label ->
            Tab(
                selected = index == activeIndex,
                onClick = { onSelect(index) },
                enabled = enabled,
                // Rounds the hover/press highlight to match the rest of the app.
                modifier = Modifier
                    .clip(MaterialTheme.shapes.medium)
                    .onPlaced { tabLayout.tabs[index] = it },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (done.getOrElse(index) { false }) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = doneLabel,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = MAX_TAB_LABEL_WIDTH),
                        )
                    }
                },
            )
        }
    }
}

/** Where [PausePanelTabs]' tabs sit, for the scrub to hit-test against. Never read in composition. */
private class TabScrubLayout {
    var row: LayoutCoordinates? = null
    val tabs = mutableMapOf<Int, LayoutCoordinates>()

    /** The tab under [x] in the row's coordinates, or the nearest one past either end. */
    fun tabAt(x: Float): Int? {
        val row = row?.takeIf { it.isAttached } ?: return null
        return tabs.entries
            .filter { it.value.isAttached }
            .map { (index, tab) ->
                val left = row.localPositionOf(tab, Offset.Zero).x
                index to (left..left + tab.size.width)
            }
            .minByOrNull { (_, span) ->
                when {
                    x < span.start -> span.start - x
                    x > span.endInclusive -> x - span.endInclusive
                    else -> 0f
                }
            }
            ?.first
    }
}

@Composable
internal fun PausePanelCollapseToggle(
    collapsed: Boolean,
    onCollapsedChange: (Boolean) -> Unit,
    minimizeLabel: String,
    restoreLabel: String,
) {
    IconButton(onClick = { onCollapsedChange(!collapsed) }) {
        Icon(
            imageVector = if (collapsed) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = if (collapsed) restoreLabel else minimizeLabel,
        )
    }
}

/**
 * Laid over a collapsed panel so the whole card is one target that expands it. The controls
 * underneath are inert while collapsed (switching to an item you can't see is meaningless), and a
 * tap that lands on one should still open the panel rather than do nothing.
 */
@Composable
internal fun BoxScope.PausePanelExpandOverlay(restoreLabel: String, onExpand: () -> Unit) {
    Box(
        modifier = Modifier
            .matchParentSize()
            .clickable(onClickLabel = restoreLabel, role = Role.Button, onClick = onExpand)
            .semantics { contentDescription = restoreLabel },
    )
}

/**
 * The one-line stand-in the thread keeps for a docked pause, so the reply still reads as waiting
 * on the user.
 */
@Composable
internal fun PausePanelWaitingMarker(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val MAX_TAB_LABEL_WIDTH = 140.dp

internal const val PAUSE_PANEL_PAGE_DOTS_TAG = "pause_panel_page_dots"
internal const val PAUSE_PANEL_SWIPE_AREA_TAG = "pause_panel_swipe_area"
internal const val PAUSE_PANEL_TABS_TAG = "pause_panel_tabs"

/** Past this many items the dots would crowd the title out, so the pager reads `n of N` instead. */
private const val MAX_PAGE_DOTS = 10
private val PAGE_DOT_SIZE = 6.dp
private val PAGE_BAR_WIDTH = 16.dp
private val PAGE_DOT_SLOT_WIDTH = 20.dp
private val PAGE_DOT_STROKE = 1.5.dp

/** Finger travel per item while scrubbing the dots. */
private val SCRUB_STEP = 48.dp
private val SCRUB_MIN_STEP = 16.dp

/** The most of the window a scrub across every item may need. */
private const val SCRUB_RANGE_WINDOW_FRACTION = 0.8f

private const val SWIPE_COMMIT_FRACTION = 0.25f
private val SWIPE_FLING_VELOCITY = 800.dp
private const val SWIPE_EDGE_RESISTANCE = 0.3f
