package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive

/**
 * Who holds a message list's tail during a run: the per-frame follower, or the user. Shared by
 * the chat's [MessageList] and Compare's pane B, so both panes follow — and yield — the same way.
 */
@Stable
internal class TailFollowerState {
    /**
     * Stands the follower down while a finger rests on the list. Deliberately NOT a scroll-away
     * signal on its own — see [TailFollowEffects].
     */
    var isTouching by mutableStateOf(false)

    /**
     * Whether the user has deliberately scrolled away from the bottom. Reset when they scroll
     * back to it (via the FAB or manually).
     */
    var userScrolledUp by mutableStateOf(false)

    /**
     * True only while an auto-scroll of ours is running. Load-bearing: isScrollInProgress cannot
     * tell our scroll from the user's, so without this the scroll-away detector scores the
     * follower's own mid-growth scroll as intent and latches, silently killing the follow.
     */
    var programmaticScroll by mutableStateOf(false)
}

@Composable
internal fun rememberTailFollowerState(): TailFollowerState = remember { TailFollowerState() }

/** Feeds [TailFollowerState.isTouching]. Observes on the Initial pass so it never interferes with scrolling. */
internal fun Modifier.tailFollowerTouch(state: TailFollowerState): Modifier = pointerInput(state) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            state.isTouching = event.changes.any { it.pressed }
        }
    }
}

/**
 * Calls [onPress] on every pointer-down, on the Initial pass so it never interferes with the press.
 * An event, not a flag like [TailFollowerState.isTouching]: a tap's down and up can land between two
 * frames, and a snapshot of the flag then never sees it pressed.
 */
internal fun Modifier.onEveryPress(onPress: () -> Unit): Modifier = pointerInput(onPress) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.any { it.pressed && !it.previousPressed }) onPress()
        }
    }
}

/** Whether [listState] rests within [NEAR_BOTTOM_BUFFER] of its tail. */
@Composable
internal fun rememberIsNearBottom(listState: LazyListState, extraEndPx: Float = 0f): State<Boolean> {
    val nearBottomThresholdPx = rememberNearBottomThresholdPx()
    // Keyed on the threshold, which is derived from LocalDensity: a keyless remember holds the
    // first composition's pixel value while the layout numbers it is compared against stay live,
    // so the buffer is measured in stale units after a density or font-scale change.
    return remember(listState, nearBottomThresholdPx, extraEndPx) {
        derivedStateOf { listState.layoutInfo.restsNearTail(nearBottomThresholdPx, extraEndPx) }
    }
}

@Composable
internal fun rememberNearBottomThresholdPx(): Float = with(LocalDensity.current) { NEAR_BOTTOM_BUFFER.toPx() }

/** [extraEndPx] is reading room past the last line (see [scrollToTail]); the rest sits above it. */
internal fun LazyListLayoutInfo.restsNearTail(thresholdPx: Float, extraEndPx: Float = 0f): Boolean {
    val lastItem = visibleItemsInfo.lastOrNull()
    if (lastItem == null || totalItemsCount == 0) return true
    // Measured from the follower's OWN rest target, not from viewportEndOffset: that baseline sits
    // a whole bottom reserve below where the list can actually rest (composer height plus the
    // reading gap — 184dp at the floor, more once queued ghost rows stack). Measure from it and the
    // effective slack becomes [NEAR_BOTTOM_BUFFER] PLUS that reserve, so the pin re-grabs the list
    // after a scroll-up of a quarter screen.
    val restOffset = viewportEndOffset - afterContentPadding + extraEndPx
    return lastItem.index >= totalItemsCount - 2 &&
        (lastItem.offset + lastItem.size - restOffset) < thresholdPx
}

/**
 * The scroll-away detector plus the streaming rules of a message list:
 *
 * 1. STREAMING START — Jump to bottom when [isStreaming] flips true.
 * 2. STREAMING FOLLOW — ONE per-frame follower runs for the whole run, easing a fraction of the
 *    remaining distance-to-bottom each frame from live layout. It is deliberately NOT driven off
 *    the streamed text. It yields to the user on a finger-down (isTouching), once the scroll-away
 *    latch is set, or while [holdFollow] or [syncHold] is true; see the loop itself for the
 *    invariants it rests on.
 * 3. POST-STREAMING — When streaming ends, a brief layout-settle, then scroll to show the
 *    complete response unless the user scrolled away.
 */
@Composable
internal fun TailFollowEffects(
    listState: LazyListState,
    state: TailFollowerState,
    isNearBottom: State<Boolean>,
    isStreaming: Boolean,
    holdFollow: Boolean = false,
    /**
     * Whether Compare's scroll sync is holding this list level with the other pane. Unlike
     * [holdFollow], the scrolls it makes are the sync's, not the user's, so none of them latch
     * scroll-away: when the hold lifts the list follows its tail again. Read live, not passed as a
     * value: the sync starts holding and scrolls this list in the same step, before any
     * recomposition could deliver a new value, and the detector must already see the hold.
     */
    syncHold: () -> Boolean = { false },
    /** Reading room past the last line, which the follower's rest stays above (see [scrollToTail]). */
    extraEndPx: Float = 0f,
) {
    // Read through updated state, not the captured parameter: the follower below runs for a whole
    // run inside one LaunchedEffect, so a plain capture would keep reporting whatever was true on
    // the frame the run started.
    val currentHoldFollow by rememberUpdatedState(holdFollow)
    val currentSyncHold by rememberUpdatedState(syncHold)
    val currentExtraEndPx by rememberUpdatedState(extraEndPx)
    // The caller re-creates its near-bottom state when the threshold or the reading room changes
    // (Compare's room is measured after the first layout), so the detector must not keep the first one.
    val currentIsNearBottom by rememberUpdatedState(isNearBottom)

    // Detect user scroll-away: when the user drags or flings away from the bottom, set the flag.
    // When they return to the bottom (manually or via FAB), clear it.
    //
    // Intent is a SCROLL, not a touch — do not add [isTouching] back to this term. A bare
    // pointer-down moves nothing, but the follower stands down for as long as a finger is on the
    // list, so the tail runs away on its own at streaming speed under a resting finger; that drift
    // would score as scroll-away, and a long-press to select text would end the follow for the
    // rest of the run. A drag holds the scroll session for its whole duration, mid-drag pauses
    // included, so gesture latching loses nothing without the touch term.
    LaunchedEffect(listState, state) {
        snapshotFlow {
            (listState.isScrollInProgress && !state.programmaticScroll && !currentSyncHold()) to currentIsNearBottom.value
        }
            .collect { (userIsScrolling, nearBottom) ->
                if (userIsScrolling && !nearBottom) {
                    state.userScrolledUp = true
                } else if (!userIsScrolling && nearBottom) {
                    state.userScrolledUp = false
                }
            }
    }

    var wasStreaming by remember { mutableStateOf(false) }
    LaunchedEffect(isStreaming) {
        if (isStreaming) {
            wasStreaming = true
            // Reset scroll-away flag — user just sent a message, start at bottom
            state.userScrolledUp = false
            // Jump to bottom immediately (user just sent a message)
            val total = listState.layoutInfo.totalItemsCount
            if (total > 0) {
                listState.scrollToTail(total - 1, currentExtraEndPx)
            }
            // ONE continuous follower for the whole run, driven per frame — not a scroll per text
            // flush, which at the buffer's STREAMING_UI_UPDATE_INTERVAL_MS means ~20 instant
            // scrollToItem jumps a second.
            try {
                while (isActive) {
                    withFrameNanos { }
                    // A finger down, or a deliberate scroll away, hands control back to the user.
                    // isScrollInProgress is NOT consulted: our own scroll below sets it, so gating
                    // on it would stop the follower on its own first frame.
                    //
                    // A hold hands it back too. The chat holds while a run is paused for human
                    // review: the run stays open across the pause, so isStreaming alone keeps this
                    // loop pinning the tail every frame while the one thing it exists to follow —
                    // output — has stopped, and a follower still running would undo every scroll
                    // the instant the user's finger lifts.
                    if (state.isTouching || state.userScrolledUp || currentHoldFollow || currentSyncHold()) {
                        state.programmaticScroll = false
                        continue
                    }

                    val info = listState.layoutInfo
                    val itemCount = info.totalItemsCount
                    val lastItem = info.visibleItemsInfo.lastOrNull()
                    if (itemCount == 0 || lastItem == null) {
                        state.programmaticScroll = false
                        continue
                    }

                    // Held for as long as the follower is driving, not toggled per frame:
                    // isScrollInProgress stays true for a moment after a scroll call returns, and a
                    // per-frame false exposes that tail to the detector as a user scroll.
                    state.programmaticScroll = true
                    try {
                        if (lastItem.index < itemCount - 1) {
                            // The tail is off screen — a card was appended below the viewport (a
                            // tool call, an office preview, a review pause), so there is no
                            // on-screen distance to ease along; jump instead. Once per appended
                            // item, not per flush.
                            listState.scrollToTail(itemCount - 1, currentExtraEndPx)
                        } else {
                            // Rest the tail against the bottom reserve, NOT viewportEndOffset: that
                            // is the layout size minus *before*-content-padding, so it sits below
                            // where the list can actually rest and inflates every step by the whole
                            // reserve — the ease then never decelerates, it runs full speed into
                            // the scroll clamp.
                            val restOffset = info.viewportEndOffset - info.afterContentPadding + currentExtraEndPx
                            val distance = (lastItem.offset + lastItem.size - restOffset).toFloat()
                            if (distance > FOLLOW_SETTLE_PX) {
                                val step = (distance * FOLLOW_FRACTION)
                                    .coerceIn(FOLLOW_MIN_STEP_PX, distance)
                                listState.scrollBy(step)
                            }
                        }
                    } catch (e: CancellationException) {
                        // A user gesture takes the scroll mutex at UserInput priority, cancelling
                        // ours. That must not escape: this loop IS the follower, so letting it
                        // through ends auto-scroll for the whole run with no way to re-arm it. Only
                        // a cancellation of the effect itself may propagate.
                        state.programmaticScroll = false
                        if (!isActive) throw e
                    }
                }
            } finally {
                state.programmaticScroll = false
            }
        } else if (wasStreaming) {
            // Streaming just ended. The final messages are now in the list synchronously
            // (finalizeChatDisplay swaps the streaming bubble for the real AI message in the same
            // state update that clears isStreaming), so we only need a brief layout-settle before
            // scrolling to show the complete response. Only auto-scroll if user hasn't scrolled away,
            // and not while Compare's sync holds this pane level with the other: its scrolls never
            // latch scroll-away, so the jump would pull the reader's place to the end.
            wasStreaming = false
            if (!state.userScrolledUp && !currentSyncHold()) {
                delay(50)
                val total = listState.layoutInfo.totalItemsCount
                if (total > 0) {
                    listState.scrollToTail(total - 1, currentExtraEndPx)
                }
            }
        }
    }
}

/**
 * Scrolls to where a list's tail rests: its end, less [extraEndPx] of reading room. That room lets
 * a reader scroll the last lines up to where they read; the automatic scrolls stop short of it, so
 * the last line still lands just above the composer.
 */
internal suspend fun LazyListState.scrollToTail(lastIndex: Int, extraEndPx: Float = 0f) {
    if (extraEndPx > 0f) {
        // Before its first layout a list only records the jump, and the step back below would
        // be lost against the position it still has.
        snapshotFlow { layoutInfo.totalItemsCount }.first { it > 0 }
    }
    scrollToItem(lastIndex, scrollOffset = Int.MAX_VALUE)
    if (extraEndPx > 0f) scrollBy(-extraEndPx)
}

/**
 * One layout's view of where a list's tail rests: the viewport end, the line the tail rests on
 * (the viewport end less the bottom padding), whether the list sat near its tail, and how far the
 * tail sits below where [scrollToTail] would put it ([tailOverhang]).
 */
internal data class TailRest(val viewportEnd: Int, val restLine: Int, val nearTail: Boolean, val overhang: Int = 0)

/**
 * How many px the tail sits below where [scrollToTail] rests it; [Int.MAX_VALUE] when the last item
 * isn't laid out at all, zero or less when it rests there or the list doesn't fill the viewport.
 */
internal fun LazyListLayoutInfo.tailOverhang(extraEndPx: Float = 0f): Int {
    val lastItem = visibleItemsInfo.lastOrNull() ?: return 0
    if (lastItem.index < totalItemsCount - 1) return Int.MAX_VALUE
    val restOffset = viewportEndOffset - afterContentPadding + extraEndPx
    return (lastItem.offset + lastItem.size - restOffset).toInt()
}

/**
 * The next value of a list's tail pin. A chat that opens on its tail is pinned there, and holds it
 * while the open settles: messages above the tail measure taller and the bottom chrome measures in
 * after the open's scroll, each pushing the tail down over several frames, and only a pin outlasts
 * them all. The reader takes it back: any touch on the list or a find/jump placing a match ([touching])
 * and any scroll not of our own ([scrolling]) unpins it, so a tap that expands a card near the end
 * never jumps. A scroll of theirs that comes to rest near the tail pins it again.
 */
internal fun nextTailPin(pinned: Boolean, touching: Boolean, scrolling: Boolean, wasScrolling: Boolean, nearTail: Boolean): Boolean = when {
    touching || scrolling -> false
    wasScrolling -> nearTail
    else -> pinned
}

/** Whether a [pinned] list has had its tail pushed down past its rest and must be put back. */
internal fun holdsPinnedTail(pinned: Boolean, rest: TailRest): Boolean = pinned && rest.overhang > TAIL_REST_SLOP_PX

private const val TAIL_REST_SLOP_PX = 1

/**
 * Whether an unpinned list whose rest line moved up from [previous] to [current] (the keyboard
 * opening, the bottom chrome growing) should be put back on its tail: only when it rested on its
 * tail the frame before. A reader scrolled up stays put, even when the last items are on screen (a
 * short chat, a long last reply). A pinned list is held by [holdsPinnedTail] instead, which also
 * covers the messages themselves growing, which this can't see.
 */
internal fun reanchorsToTail(previous: TailRest?, current: TailRest, lastVisibleIndex: Int?, itemCount: Int): Boolean {
    if (previous == null || previous.restLine <= 0 || current.restLine >= previous.restLine) return false
    if (lastVisibleIndex == null || itemCount == 0 || lastVisibleIndex < itemCount - 2) return false
    return previous.nearTail
}

/** [scrollToTail], animated. */
internal suspend fun LazyListState.animateScrollToTail(lastIndex: Int, extraEndPx: Float = 0f) {
    if (extraEndPx <= 0f) {
        animateScrollToItem(lastIndex, scrollOffset = Int.MAX_VALUE)
        return
    }
    if (layoutInfo.visibleItemsInfo.none { it.index == lastIndex }) animateScrollToItem(lastIndex)
    val info = layoutInfo
    val last = info.visibleItemsInfo.lastOrNull { it.index == lastIndex } ?: return
    val restOffset = info.viewportEndOffset - info.afterContentPadding + extraEndPx
    animateScrollBy(last.offset + last.size - restOffset)
}

// ── Streaming follow tuning ───────────────────────────────────────────
// Fraction of the remaining distance-to-bottom consumed per frame. Recomputed from live layout
// every frame, so this is an exponential ease: 0.15 is a time constant near 100ms at 60fps.
private const val FOLLOW_FRACTION = 0.15f

/** Floor on the per-frame step, so the last sub-pixel gap closes instead of easing forever. */
private const val FOLLOW_MIN_STEP_PX = 0.5f

/** Distance below which the list counts as settled at the bottom and the follower idles. */
private const val FOLLOW_SETTLE_PX = 0.5f

/**
 * Slack below the list's resting position within which the follower keeps its pin. A gesture that
 * leaves it hands the list to the user for the rest of the run; settling back inside it re-arms the
 * follow. Larger and a deliberate scroll-up gets yanked back to the tail; smaller and a slight
 * gesture near the tail ends the follow when the user meant to stay pinned.
 */
private val NEAR_BOTTOM_BUFFER = 80.dp

/**
 * Reading gap held below a message list's last line, on top of the caller-measured bar reserve.
 * Both Compare panes hold the same gap, so their tails rest at the same height.
 */
internal val MESSAGE_LIST_BOTTOM_GAP = 24.dp
