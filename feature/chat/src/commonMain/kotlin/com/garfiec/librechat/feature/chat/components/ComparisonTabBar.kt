package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.ui.components.AdaptiveTabRow
import com.garfiec.librechat.core.ui.components.selectedTabIndex
import com.garfiec.librechat.feature.chat.resources.*
import com.garfiec.librechat.feature.chat.resources.Res
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * Phone-layout comparison view with tabs and a horizontal pager.
 * Tab 0 = primary model, Tab 1 = secondary model.
 * The pager allows swiping between the two conversation panes.
 *
 * @param primaryModelName Display name for the primary model tab
 * @param secondaryModelName Display name for the secondary model tab
 * @param primaryContent Composable content for the primary conversation pane
 * @param secondaryContent Composable content for the secondary conversation pane
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ComparisonTabBar(
    primaryModelName: String,
    secondaryModelName: String,
    primaryContent: @Composable () -> Unit,
    secondaryContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onContinueWithPrimary: (() -> Unit)? = null,
    onContinueWithSecondary: (() -> Unit)? = null,
    onTabChange: ((Int) -> Unit)? = null,
) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            onTabChange?.invoke(page)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        AdaptiveTabRow(
            titles = listOf(primaryModelName, secondaryModelName),
            selectedIndex = pagerState.selectedTabIndex(),
            onSelect = { page -> coroutineScope.launch { pagerState.animateScrollToPage(page) } },
            // Model ids often share a long prefix and differ at the end.
            titleOverflow = TextOverflow.MiddleEllipsis,
            dotColors = listOf(ComparisonLane.A.accent, ComparisonLane.B.accent),
            // Each tab is a whole pane, so the underline marks the whole tab.
            fullWidthIndicator = true,
        )

        val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        val flingThresholdPx = with(LocalDensity.current) { PageFlingThreshold.toPx() }
        val childOverscroll = remember(pagerState, isRtl, flingThresholdPx) {
            FirstPageForwardOverscroll(pagerState, isRtl, flingThresholdPx)
        }
        HorizontalPager(
            state = pagerState,
            // On the first page the pager would claim the swipe that opens the drawer; see below.
            userScrollEnabled = pagerState.settledPage != 0,
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .nestedScroll(childOverscroll)
                .firstPageForwardDrag(pagerState, isRtl, flingThresholdPx, coroutineScope),
        ) { page ->
            Column(modifier = Modifier.fillMaxSize()) {
                val continueCallback = when (page) {
                    0 -> onContinueWithPrimary
                    1 -> onContinueWithSecondary
                    else -> null
                }
                if (continueCallback != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        TextButton(onClick = continueCallback) {
                            Text(stringResource(Res.string.keep_this_answer))
                        }
                    }
                }
                Box(modifier = Modifier.weight(1f)) {
                    when (page) {
                        0 -> primaryContent()
                        1 -> secondaryContent()
                    }
                }
            }
        }
    }
}

/**
 * The first page's swipe to the second, standing in for the pager's own scrolling there.
 *
 * A pager claims every horizontal drag, including one toward a page it doesn't have, and the
 * drawer's drag sits above it and only sees what the pager leaves. So on the first page the pager
 * doesn't scroll, and this claims only a drag toward the second page: one the other way goes
 * unconsumed to the drawer, as it does on an ordinary chat. A drag that starts anywhere else is
 * left to the pager.
 */
private fun Modifier.firstPageForwardDrag(
    pagerState: PagerState,
    isRtl: Boolean,
    flingThresholdPx: Float,
    scope: CoroutineScope,
): Modifier = pointerInput(pagerState, isRtl, flingThresholdPx) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (pagerState.settledPage != 0 || pagerState.isScrollInProgress) return@awaitEachGesture
        val velocity = VelocityTracker().apply { addPointerInputChange(down) }
        val slop = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
            val delta = toPagerDelta(over, isRtl)
            if (delta > 0f) {
                change.consume()
                velocity.addPointerInputChange(change)
                pagerState.dispatchRawDelta(delta)
            }
        } ?: return@awaitEachGesture
        horizontalDrag(slop.id) { change ->
            velocity.addPointerInputChange(change)
            pagerState.dispatchRawDelta(toPagerDelta(change.position.x - change.previousPosition.x, isRtl))
            change.consume()
        }
        val target = settleTarget(pagerState, toPagerDelta(velocity.calculateVelocity().x, isRtl), flingThresholdPx)
        scope.launch { pagerState.animateScrollToPage(target) }
    }
}

/**
 * The same forward swipe when a horizontally scrolling child (a code block, a log) takes the drag:
 * with the pager's scrolling off on the first page nothing else would take the child's leftover, so
 * a swipe over one at its end would never reach the second page. Once it has moved the pager it
 * keeps the drag, as the pager's own nested scrolling does.
 */
private class FirstPageForwardOverscroll(
    private val pagerState: PagerState,
    private val isRtl: Boolean,
    private val flingThresholdPx: Float,
) : NestedScrollConnection {
    private var engaged = false

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (!engaged || pagerState.currentPageOffsetFraction == 0f) return Offset.Zero
        return Offset(toPagerDelta(pagerState.dispatchRawDelta(toPagerDelta(available.x, isRtl)), isRtl), 0f)
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput) return Offset.Zero
        val delta = toPagerDelta(available.x, isRtl)
        if (!engaged) {
            if (delta <= 0f || pagerState.settledPage != 0 || pagerState.isScrollInProgress) return Offset.Zero
            engaged = true
        }
        return Offset(toPagerDelta(pagerState.dispatchRawDelta(delta), isRtl), 0f)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (!engaged) return Velocity.Zero
        engaged = false
        pagerState.animateScrollToPage(settleTarget(pagerState, toPagerDelta(available.x, isRtl), flingThresholdPx))
        return available
    }
}

/** Pager deltas are positive toward the next page; a finger moves the other way in LTR. Self-inverse. */
private fun toPagerDelta(dx: Float, isRtl: Boolean): Float = if (isRtl) dx else -dx

private fun settleTarget(pagerState: PagerState, forwardVelocity: Float, flingThresholdPx: Float): Int {
    val progress = pagerState.currentPage + pagerState.currentPageOffsetFraction
    return when {
        forwardVelocity > flingThresholdPx -> 1
        forwardVelocity < -flingThresholdPx -> 0
        progress >= 0.5f -> 1
        else -> 0
    }
}

private val PageFlingThreshold = 400.dp
