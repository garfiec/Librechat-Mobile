package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.gestures.scrollBy
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

/** Whether [listState] rests within [NEAR_BOTTOM_BUFFER] of its tail. */
@Composable
internal fun rememberIsNearBottom(listState: LazyListState): State<Boolean> {
    val nearBottomThresholdPx = with(LocalDensity.current) { NEAR_BOTTOM_BUFFER.toPx() }
    // Keyed on the threshold, which is derived from LocalDensity: a keyless remember holds the
    // first composition's pixel value while the layout numbers it is compared against stay live,
    // so the buffer is measured in stale units after a density or font-scale change.
    return remember(listState, nearBottomThresholdPx) {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastItem = info.visibleItemsInfo.lastOrNull()
            val itemCount = info.totalItemsCount
            if (lastItem == null || itemCount == 0) {
                true
            } else {
                // Measured from the follower's OWN rest target, not from viewportEndOffset: that
                // baseline sits a whole bottom reserve below where the list can actually rest
                // (composer height plus the reading gap — 184dp at the floor, more once queued
                // ghost rows stack). Measure from it and the effective slack becomes
                // [NEAR_BOTTOM_BUFFER] PLUS that reserve, so the pin re-grabs the list after a
                // scroll-up of a quarter screen.
                val restOffset = info.viewportEndOffset - info.afterContentPadding
                lastItem.index >= itemCount - 2 &&
                    (lastItem.offset + lastItem.size - restOffset) < nearBottomThresholdPx
            }
        }
    }
}

/**
 * The scroll-away detector plus the streaming rules of a message list:
 *
 * 1. STREAMING START — Jump to bottom when [isStreaming] flips true.
 * 2. STREAMING FOLLOW — ONE per-frame follower runs for the whole run, easing a fraction of the
 *    remaining distance-to-bottom each frame from live layout. It is deliberately NOT driven off
 *    the streamed text. It yields to the user on a finger-down (isTouching), once the scroll-away
 *    latch is set, or while [holdFollow] is true; see the loop itself for the invariants it rests on.
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
) {
    // Read through updated state, not the captured parameter: the follower below runs for a whole
    // run inside one LaunchedEffect, so a plain capture would keep reporting whatever was true on
    // the frame the run started.
    val currentHoldFollow by rememberUpdatedState(holdFollow)

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
            (listState.isScrollInProgress && !state.programmaticScroll) to isNearBottom.value
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
                listState.scrollToItem(total - 1, scrollOffset = Int.MAX_VALUE)
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
                    if (state.isTouching || state.userScrolledUp || currentHoldFollow) {
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
                            listState.scrollToItem(itemCount - 1, scrollOffset = Int.MAX_VALUE)
                        } else {
                            // Rest the tail against the bottom reserve, NOT viewportEndOffset: that
                            // is the layout size minus *before*-content-padding, so it sits below
                            // where the list can actually rest and inflates every step by the whole
                            // reserve — the ease then never decelerates, it runs full speed into
                            // the scroll clamp.
                            val restOffset = info.viewportEndOffset - info.afterContentPadding
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
            // scrolling to show the complete response. Only auto-scroll if user hasn't scrolled away.
            wasStreaming = false
            if (!state.userScrolledUp) {
                delay(50)
                val total = listState.layoutInfo.totalItemsCount
                if (total > 0) {
                    listState.scrollToItem(total - 1, scrollOffset = Int.MAX_VALUE)
                }
            }
        }
    }
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
