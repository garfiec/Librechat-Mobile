package com.garfiec.librechat.feature.chat.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.common.ChatLayoutConstants
import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.MinimalFeedback
import com.garfiec.librechat.core.model.PendingAction
import com.garfiec.librechat.core.ui.glass.LocalGlassBackdrop
import com.garfiec.librechat.core.ui.glass.glassBackdropSource
import com.garfiec.librechat.core.ui.theme.isSurfaceDark
import com.garfiec.librechat.feature.chat.resources.*
import com.garfiec.librechat.feature.chat.util.MessageNode
import com.garfiec.librechat.feature.chat.util.hasEditablePart
import com.garfiec.librechat.feature.chat.util.withPreviews
import com.garfiec.librechat.feature.chat.viewmodel.ActiveToolCall
import com.garfiec.librechat.feature.chat.viewmodel.JumpFlash
import com.garfiec.librechat.feature.chat.viewmodel.MessageEditingState
import com.garfiec.librechat.feature.chat.viewmodel.SearchFocusRequest
import com.garfiec.librechat.feature.chat.viewmodel.SearchMatch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageList(
    displayMessages: List<MessageNode>,
    isStreaming: Boolean,
    streamingContent: String,
    onSiblingNavigation: (parentMessageId: String, siblingIndex: Int) -> Unit,
    onEditMessage: (messageId: String) -> Unit,
    onRegenerateMessage: (messageId: String) -> Unit,
    onCopyMessage: (messageId: String) -> Unit,
    modifier: Modifier = Modifier,
    /** The live reply's reasoning, shown collapsed above its text. See [StreamingMessageBubble]. */
    streamingThinking: String = "",
    activeToolCalls: List<ActiveToolCall> = emptyList(),
    /** Calls whose sandbox is cold-booting; see `MessagesState.sandboxStartingToolCallIds`. */
    sandboxStartingToolCallIds: Set<String> = emptySet(),
    streamingAttachments: List<Attachment> = emptyList(),
    onFeedback: (messageId: String, feedback: MinimalFeedback?) -> Unit = { _, _ -> },
    /** `interface.feedback`. Gated here rather than at each caller so every list inherits it. */
    feedbackEnabled: Boolean = true,
    /**
     * The response that just took over from the streaming bubble, from `ChatUiState`. Read from
     * state rather than derived here from `isStreaming`: a UI-side derivation can only run in an
     * effect, which commits AFTER the composition that first renders the finalized message — by
     * then its activity groups have already chosen to collapse and nothing re-opens them.
     */
    justSettledMessageId: String? = null,
    /**
     * False hides the actions that start a run (edit, regenerate, continue): Compare setup sends
     * nothing until it ends, so they would do nothing.
     */
    canResubmit: Boolean = true,
    /** The cut-short reply's chip ([StoppedReplyFooter]), under that reply; null shows none. */
    stoppedReply: StoppedReplyAction? = null,
    /**
     * Hides every message action except Copy and branch switching, whatever callbacks are passed.
     */
    readOnly: Boolean = false,
    onReadAloud: (messageId: String) -> Unit = {},
    onFork: (messageId: String) -> Unit = {},
    currentlyReadingMessageId: String? = null,
    /**
     * Which message is being edited, with its drafts and their baselines. One value rather than an
     * id beside them, so the two cannot disagree about which row the drafts belong to.
     */
    editing: MessageEditingState = MessageEditingState(),
    onEditTextChange: (String) -> Unit = {},
    onEditPartTextChange: (Int, String) -> Unit = { _, _ -> },
    onEditSave: () -> Unit = {},
    onEditRerun: () -> Unit = {},
    onEditCancel: () -> Unit = {},
    baseUrl: String = "",
    fontSizeMultiplier: Float = 1.0f,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    userAvatarUrl: String? = null,
    userName: String? = null,
    selectedEndpoint: String? = null,
    streamingSenderName: String = "Assistant",
    showImageDescriptions: Boolean = true,
    chatLayoutStyle: String = ChatLayoutConstants.THREAD,
    showAvatars: Boolean = true,
    showBubbles: Boolean = false,
    useKatex: Boolean = false,
    searchQuery: String? = null,
    searchMatchIndices: List<SearchMatch> = emptyList(),
    currentSearchMatchIndex: Int = 0,
    searchFocusRequest: SearchFocusRequest? = null,
    onSearchScrollHandle: () -> Unit = {},
    /** A jumped-to message with nothing to highlight in it flashes instead. */
    jumpFlash: JumpFlash? = null,
    /** A jump to a message is under way: the first scroll waits for it rather than going to the bottom. */
    jumpPending: Boolean = false,
    // Scrollable bottom inset that keeps the latest content clear of the overlaid input bar. The
    // caller measures the actual bar height (which grows as queued ghost rows stack above the
    // composer) so streaming text rests above the ghosts rather than behind them. It's a
    // LazyColumn contentPadding, not a solid spacer: content still scrolls up *behind* the
    // translucent ghosts. Defaults to the single-line bar's reserve.
    bottomContentPadding: Dp = 160.dp,
    // Where the scroll-to-bottom button floats above the list's bottom edge. Null floats it just
    // above the input bar, 30dp into [bottomContentPadding]. A caller with something else at the
    // bottom (the shared-chat viewer's Continue FAB) passes a value that clears it.
    scrollToBottomPadding: Dp? = null,
    // Scrollable top inset mirroring [bottomContentPadding] for the floating top bar: the first
    // message rests below the bar yet still scrolls up *behind* its translucent scrim. The caller
    // measures the bar's actual height (status bar + chips). Defaults to 0 for callers without an
    // overlaid bar (e.g. comparison panes that sit under a separate header).
    topContentPadding: Dp = 0.dp,
    /**
     * The live human-review pause, or null. Rendered at the tail of the streaming section — the
     * run is unfinished, so it belongs to the reply in progress, not after it: a tool approval as
     * its resolve card, a question as a one-line marker pointing at the panel docked above the
     * composer. Only the callers that can resolve one pass it; the comparison panes leave it null.
     */
    pendingAction: PendingAction? = null,
    /**
     * Whether this list records itself into the screen's glass backdrop. Comparison mode shows two
     * lists, and two sources writing one backdrop would overwrite each other, so its panes opt out.
     */
    recordsGlassBackdrop: Boolean = true,
    /**
     * Hoisted by a caller that swaps one list for another showing the same thread (the single list
     * and Compare's pane A), so the swap keeps the reader's place.
     */
    listState: LazyListState = rememberLazyListState(),
    /** Compare's scroll sync is holding this pane level with the other; its tail follower stands down. */
    syncHold: () -> Boolean = { false },
    /**
     * Reading room past the last line, so a reader can scroll the end of a reply up to where they
     * read. Automatic scrolls stop short of it (see [scrollToTail]).
     */
    extraEndSpace: Dp = 0.dp,
) {
    val extraEndPx = with(LocalDensity.current) { extraEndSpace.toPx() }
    val coroutineScope = rememberCoroutineScope()
    val follower = rememberTailFollowerState()
    // Height (px) of the floating top bar occluding the list's top edge. The search fine-tune adds
    // this so a focused match settles below the bar rather than flush against (or under) it. Zero
    // for callers without an overlaid bar (e.g. comparison panes), leaving their behavior unchanged.
    val topContentPaddingPx = with(LocalDensity.current) { topContentPadding.toPx() }
    var lastNavigatedParentKey by remember { mutableStateOf<String?>(null) }

    // A live `ask_user_question` pause is rendered by the docked AskUserQuestionPanel, not as a
    // tool card.
    // Attribution is by the pause's own tool_call_id where the server sends one, so a sibling ask
    // call that is genuinely still running keeps its card instead of being hidden by association.
    val pausedAskToolCallId = pendingAction?.payload?.toolCallId
    // Fallback attribution for payloads without a tool_call_id: the pause's own question text
    // (a batched pause keeps `question` populated with its first item as a display fallback).
    val pausedAskQuestion = pendingAction?.payload?.let { payload ->
        payload.question?.question ?: payload.questions?.firstOrNull()?.question
    }?.takeIf { it.isNotBlank() }
    val renderedToolCalls = remember(activeToolCalls, pausedAskToolCallId, pausedAskQuestion) {
        activeToolCalls.withoutUnansweredQuestions(pausedAskToolCallId, pausedAskQuestion)
    }
    // One "Running X" row per skill the streaming turn picked, until the final message brings
    // the real cards. The turn's user message is the last displayed node (fresh send, regenerate
    // and edit alike), and a resync that already carries a skill's card retires its row.
    val pendingSkills = remember(isStreaming, displayMessages, renderedToolCalls) {
        if (!isStreaming) {
            emptyList()
        } else {
            pendingSkillRows(
                manualSkills = displayMessages.lastOrNull()?.message?.takeIf { it.isCreatedByUser }?.manualSkills,
                liveSkillNames = renderedToolCalls.filter { isSkillToolCall(it.name) }.mapNotNull { parseSkillName(it.input) },
            )
        }
    }
    val streamingToolCallCount = if (isStreaming) renderedToolCalls.size else 0
    val totalItemCount = displayMessages.size + streamingToolCallCount + if (isStreaming) 1 else 0

    // True while the focus sits on something inside the list — an inline message edit.
    // The keyboard handler below stands down for those; foundation brings a focused node into
    // view on its own, and unlike this file it knows which node that is.
    var listHasFocus by remember { mutableStateOf(false) }

    val isNearBottom = rememberIsNearBottom(listState, extraEndPx)

    // ── Auto-scroll behavior ──────────────────────────────────────────
    //
    // 1. INITIAL LOAD — Scroll to the most recent message once on the
    //    first non-empty emission. Never fires again for this instance.
    //
    // 2. STREAMING START — Jump to bottom when isStreaming flips true.
    //
    // 3. STREAMING FOLLOW — ONE per-frame follower runs for the whole
    //    run, easing a fraction of the remaining distance-to-bottom each
    //    frame from live layout. It is deliberately NOT driven off
    //    streamingContent. It yields to the user on a finger-down
    //    (isTouching), once the scroll-away latch is set, or while the
    //    run is paused for human review; see the loop itself
    //    (TailFollower.kt) for the invariants it rests on.
    //
    // 4. POST-STREAMING — When streaming ends, a 300ms delay lets the
    //    Room observer replace the streaming bubble with the real AI
    //    message, then we scroll to show the complete response.
    //
    // Auto-scroll does NOT fire for branch switching, message deletion,
    // or any other display-list change — only streaming and initial load.
    // ─────────────────────────────────────────────────────────────────

    // ── Search focus scroll ───────────────────────────────────────────
    // Two-phase, race-free jump to the focused search occurrence:
    //   Phase 1: scrollToItem composes the target message (a LazyColumn item
    //            composes fully even when taller than the viewport), like a
    //            browser's instant find-in-page jump. It lands the target itself
    //            first in view: the list holds its first visible item fixed, and
    //            messages render asynchronously (markdown, code highlighting), so
    //            landing with the message above first would let that message grow and
    //            push the target out of view.
    //   Phase 2: the focused segment reports its live LayoutCoordinates plus
    //            the match's rect within it on every position pass (keyed
    //            state, never a consumable flag — reports for a stale target
    //            are simply ignored). From the latest report for the CURRENT
    //            request the match is placed on the browser-style anchor line
    //            (~1/3 down the usable viewport): a far move instantly and
    //            relative to the target message (so growth above can't carry it
    //            off), a short one animated. That repeats a few times while the
    //            messages around it finish rendering.
    var listCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var focusedMatchReport by remember { mutableStateOf<FocusedSearchMatchReport?>(null) }
    val currentOnSearchScrollHandled by rememberUpdatedState(onSearchScrollHandle)

    // Drop the held LayoutCoordinates once search closes.
    LaunchedEffect(searchQuery) {
        if (searchQuery == null) focusedMatchReport = null
    }

    LaunchedEffect(searchFocusRequest) {
        val request = searchFocusRequest ?: return@LaunchedEffect
        if (request.messageIndex !in displayMessages.indices) return@LaunchedEffect

        // A jump that opens a chat issues its request before the list's first layout, when the
        // viewport still reads 0 and the anchor line would sit at the very top.
        snapshotFlow { listState.layoutInfo.viewportSize.height }.first { it > 0 }
        val viewportHeight = listState.layoutInfo.viewportSize.height.toFloat()
        val anchorLine = topContentPaddingPx + (viewportHeight - topContentPaddingPx) / 3f

        // Only jump when the target message isn't laid out yet. When it's already composed
        // (next/prev within the same or a visible message), phase 2 moves straight to the match
        // with no teleport-then-reverse.
        val alreadyLaidOut = listState.layoutInfo.visibleItemsInfo.any { it.index == request.messageIndex }
        if (!alreadyLaidOut) listState.scrollToItem(request.messageIndex)

        var placed = false
        for (pass in 0 until SEARCH_FOCUS_SETTLE_PASSES) {
            val report = withTimeoutOrNull(SEARCH_FOCUS_REPORT_TIMEOUT_MS) {
                snapshotFlow { focusedMatchReport }
                    .first { it != null && it.matches(request) && it.coordinates.isAttached }
            } ?: break
            val listCoords = listCoordinates?.takeIf { it.isAttached } ?: break
            placed = true
            val matchTop = listCoords.localPositionOf(report.coordinates, Offset(0f, report.matchRect.top)).y
            val delta = matchTop - anchorLine
            val tolerance = if (pass == 0) 1f else SEARCH_FOCUS_SETTLE_TOLERANCE_PX
            if (abs(delta) <= tolerance) {
                if (pass > 0) break
            } else {
                val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == request.messageIndex }
                if (target != null && abs(delta) > viewportHeight / 2) {
                    // Relative to the target, which stays first in view when the match is near its top.
                    val withinTarget = matchTop - target.offset
                    listState.scrollToItem(request.messageIndex, (withinTarget - anchorLine).toInt().coerceAtLeast(0))
                } else {
                    listState.animateScrollBy(delta, SearchFocusScrollSpec)
                }
            }
            delay(SEARCH_FOCUS_SETTLE_MS)
        }
        // No match to place (folded away, or a jump's flash-only fallback): keep the target first in
        // view, in case something above it moved while waiting. Also when it was already laid out but
        // starts above the viewport: a chat that opened at its bottom shows only a long target's tail.
        val targetStartsAbove = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == request.messageIndex }
            ?.let { it.offset < listState.layoutInfo.viewportStartOffset + topContentPaddingPx } == true
        if (!placed && (!alreadyLaidOut || targetStartsAbove)) {
            listState.scrollToItem(request.messageIndex)
        }
        currentOnSearchScrollHandled()
    }

    // Determine the currently focused SearchMatch
    val currentSearchMatch: SearchMatch? = if (searchMatchIndices.isNotEmpty() &&
        currentSearchMatchIndex in searchMatchIndices.indices
    ) {
        searchMatchIndices[currentSearchMatchIndex]
    } else {
        null
    }

    // Build a set of matching message indices for O(1) lookup
    val searchMatchMessageIndexSet = remember(searchMatchIndices) {
        searchMatchIndices.map { it.messageIndex }.toSet()
    }

    // Saveable so it survives the list leaving/re-entering composition (e.g. the
    // full-screen artifact route disposes the chat entry); otherwise the initial
    // scroll re-fires on return and overrides the restored scroll position.
    var hasScrolledToBottom by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(displayMessages.size, jumpPending) {
        if (!hasScrolledToBottom && displayMessages.isNotEmpty() && !jumpPending) {
            // A chat opened by a jump to one message starts there, not at the bottom: the jump is
            // waited out, and only one that gave up without a message to focus lands at the bottom,
            // and only if the user hasn't scrolled the list while it waited.
            val untouched = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
            if (searchFocusRequest == null && untouched) listState.scrollToTail(totalItemCount - 1, extraEndPx)
            hasScrolledToBottom = true
        }
    }

    // A run pausing for human review changes NOTHING the other scroll effects key on:
    // displayMessages.size, streamingContent and isStreaming are all unchanged. Without this the
    // waiting marker is appended below the viewport and the user is left looking at a live cursor
    // on a run that will never produce another token.
    LaunchedEffect(pendingAction?.actionId) {
        if (pendingAction?.actionId != null) {
            follower.userScrolledUp = false
            val total = listState.layoutInfo.totalItemsCount
            // Flagged like the follower's own scrolls: this one animates from above the tail, so
            // every frame of it reads to the scroll-away detector as a user dragging away from
            // the bottom — the one thing that would latch the follower off for the rest of the run.
            if (total > 0) {
                follower.programmaticScroll = true
                try {
                    listState.animateScrollToTail(total - 1, extraEndPx)
                } finally {
                    follower.programmaticScroll = false
                }
            }
        }
    }

    ClearExpansionStatesAfterReply(isStreaming, ExpansionPane.PRIMARY)

    TailFollowEffects(
        listState = listState,
        state = follower,
        isNearBottom = isNearBottom,
        isStreaming = isStreaming,
        // A run paused for human review holds the follower: the marker is brought into view
        // once by its own effect above, and after that the list is the user's.
        holdFollow = pendingAction != null,
        syncHold = syncHold,
        extraEndPx = extraEndPx,
    )

    // ── Keyboard scroll ────────────────────────────────────────────
    // When the soft keyboard (IME) opens, the Scaffold's imePadding()
    // shrinks the viewport. We detect this by observing the
    // LazyColumn's viewportEndOffset via snapshotFlow. When it
    // shrinks and the user was near the bottom, we scroll down to
    // keep the latest messages visible above the input box.
    //
    // It runs ONLY while nothing inside the list holds focus. This
    // exists for the composer, which sits outside the list, so the
    // list learns about the keyboard from nothing but its own
    // shrinking viewport. A field INSIDE the list — an inline message
    // edit — needs no help: foundation scrolls a newly
    // focused node into view by itself, and it knows where the focus
    // is, which this does not. Both running means this one wins and
    // jumps to the tail of the last item, scrolling the user off the
    // very field they tapped.
    //
    // This approach is more reliable than observing WindowInsets.ime
    // directly because:
    //  - It fires AFTER the layout has actually resized (no timing
    //    issues with keyboard animation).
    //  - It avoids known issues with WindowInsets.isImeVisible on
    //    targetSdk 35.
    //  - It naturally handles any cause of viewport shrinkage, not
    //    just keyboard appearance.
    // ───────────────────────────────────────────────────────────────
    // Not while a find or jump scroll is placing a match: opening the find bar shrinks the
    // viewport too, and a scroll to the bottom then would interrupt that scroll and cancel it.
    val currentFocusRequest by rememberUpdatedState(searchFocusRequest)
    LaunchedEffect(Unit) {
        var previousViewportEnd = 0
        snapshotFlow {
            listState.layoutInfo.viewportEndOffset
        }.collect { viewportEnd ->
            if (previousViewportEnd > 0 && viewportEnd < previousViewportEnd && !listHasFocus && currentFocusRequest == null) {
                // Viewport shrank (e.g., keyboard opened). If user was
                // near the bottom, scroll to keep them there.
                val info = listState.layoutInfo
                val lastVisible = info.visibleItemsInfo.lastOrNull()
                val itemCount = info.totalItemsCount
                if (lastVisible != null && itemCount > 0 && lastVisible.index >= itemCount - 2) {
                    coroutineScope.launch {
                        listState.scrollToTail(itemCount - 1, extraEndPx)
                    }
                }
            }
            previousViewportEnd = viewportEnd
        }
    }

    val pullToRefreshState = rememberPullToRefreshState()
    val resubmit = canResubmit && !readOnly
    // Parsed once per list; `contentType` below runs on every measure pass.
    val wakeups = remember(displayMessages) { wakeupsByMessageId(displayMessages) }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
        state = pullToRefreshState,
        // The list extends under the floating top bar; offset the refresh indicator by the same
        // top inset so it appears in clear space below the bar rather than behind its scrim.
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullToRefreshState,
                isRefreshing = isRefreshing,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = topContentPadding),
            )
        },
    ) {
        val previewOverrides = LocalPreviewOverrides.current
        val liveAttachments = remember(streamingAttachments, previewOverrides) {
            streamingAttachments.withPreviews(previewOverrides)
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .glassBackdropSource(LocalGlassBackdrop.current.takeIf { recordsGlassBackdrop })
                .onFocusChanged { listHasFocus = it.hasFocus }
                .onGloballyPositioned { listCoordinates = it }
                .tailFollowerTouch(follower),
            state = listState,
            // The bottom reserve is the measured bar height PLUS a reading gap. The gap must stay
            // here rather than in the follower's target: a follower-only gap is reclaimed by the
            // scroll-to-bottom when the run settles, putting a jump at completion (the #169
            // flicker class).
            contentPadding = PaddingValues(
                top = topContentPadding + 8.dp,
                bottom = bottomContentPadding + MESSAGE_LIST_BOTTOM_GAP + extraEndSpace,
            ),
        ) {
            itemsIndexed(
                items = displayMessages,
                // Key by the conversation SLOT (parent in the active path), not the message's
                // own server-assigned id: the slot is stable across the streaming→final swap
                // and regenerate siblings, so the trailing streaming reply (keyed to the same
                // slot below) and its finalized message stay ONE in-place item and the list
                // keeps its scroll anchor at completion. Unique within the path (one node/level).
                key = { _, node -> node.treeParentKey },
                contentType = { _, node ->
                    // A wake-up is a user message that renders as a system row; its own type keeps
                    // slot reuse from handing it a user bubble's composition, or the reverse.
                    val role = when {
                        !node.message.isCreatedByUser -> "assistant"
                        wakeups.wakeupOf(node, isEditing = editing.editingMessageId == node.message.messageId) != null -> "system"
                        else -> "user"
                    }
                    "${chatLayoutStyle}_$role"
                },
            ) { index, node ->
                val isMatch = index in searchMatchMessageIndexSet
                val isCurrent = currentSearchMatch != null && index == currentSearchMatch.messageIndex
                // Which occurrence within this message is focused (-1 when this isn't the current match).
                val focusedOccurrenceInMessage = if (isCurrent) currentSearchMatch.occurrenceInMessage else -1
                val isJumpTarget = jumpFlash?.messageIndex == index
                val flash = rememberJumpFlash(jumpFlash?.takeIf { isJumpTarget && it.fade }?.requestId)
                val flashColor = jumpFlashColor()
                // Outlined while find-in-page is open on it (closing it clears the target); a target with no
                // match to find gets the fading flash instead, so nothing is left drawn on it.
                val jumpOutline = if (isJumpTarget && jumpFlash?.fade == false) {
                    Modifier.jumpTargetOutline(MaterialTheme.colorScheme.primary)
                } else {
                    Modifier
                }

                // Last message parses markdown synchronously; see [LocalImmediateMarkdown].
                // Publish the focus-request nonce only around the current match so a collapsible
                // block re-arms its auto-expand on every navigation (even back to the same match);
                // non-current bubbles see 0L and don't recompose on navigation.
                CompositionLocalProvider(
                    LocalImmediateMarkdown provides (index == displayMessages.lastIndex),
                    LocalSearchFocusNonce provides if (isCurrent) searchFocusRequest?.requestId ?: 0L else 0L,
                    LocalSuppressGroupAutoCollapse provides (node.message.messageId == justSettledMessageId),
                    LocalFeedbackEnabled provides (!isStreaming && !readOnly),
                    LocalExpansionScope provides expansionScope(ExpansionPane.PRIMARY, node.message.parentMessageId),
                ) {
                // Built here, where the message is in hand: whether a rerun is possible, and
                // whether the changes can ride it, are both questions about THIS message's parts
                // and parent. Null on every other row (see MessageEditSession).
                // Memoized: the artifact check parses markdown, and this subtree recomposes on
                // every streaming flush. Everything else in this file that scans content parts is
                // wrapped the same way.
                val editable = remember(node.message.content, node.message.text) {
                    hasEditablePart(node.message)
                }
                val editSession = if (editing.editingMessageId == node.message.messageId) {
                    rememberMessageEditSession(
                        message = node.message,
                        // The edited message is on the active path, so its parent is too — no need
                        // for the whole tree to resolve the turn a rerun would replay.
                        displayed = displayMessages,
                        editing = editing,
                        saveAllowed = !isStreaming && !readOnly,
                        canStartRun = resubmit,
                        onTextChange = onEditTextChange,
                        onPartTextChange = onEditPartTextChange,
                        onSave = onEditSave,
                        onRerun = onEditRerun,
                        onCancel = onEditCancel,
                    )
                } else {
                    null
                }
                val systemEvent = wakeups.wakeupOf(node, isEditing = editing.editingMessageId == node.message.messageId)
                if (systemEvent != null) {
                    SystemEventRow(
                        display = systemEvent,
                        messageId = node.message.messageId,
                        fontSizeMultiplier = fontSizeMultiplier,
                        useKatex = useKatex,
                        siblingIndex = node.siblingIndex,
                        siblingCount = node.siblingCount,
                        onSiblingNavigation = { newIndex ->
                            lastNavigatedParentKey = node.treeParentKey
                            onSiblingNavigation(node.treeParentKey, newIndex)
                        },
                        onCopy = { onCopyMessage(node.message.messageId) },
                        onEdit = if (resubmit && editable) {
                            { onEditMessage(node.message.messageId) }
                        } else {
                            null
                        },
                        onReadAloud = if (readOnly) null else ({ onReadAloud(node.message.messageId) }),
                        isReading = currentlyReadingMessageId == node.message.messageId,
                        onFork = if (readOnly) null else ({ onFork(node.message.messageId) }),
                    )
                } else {
                MessageBubble(
                    message = node.message,
                    modifier = jumpOutline.jumpFlashBackground(flash, flashColor),
                    siblingIndex = node.siblingIndex,
                    siblingCount = node.siblingCount,
                    onSiblingNavigation = { newIndex ->
                        lastNavigatedParentKey = node.treeParentKey
                        onSiblingNavigation(node.treeParentKey, newIndex)
                    },
                    showActionsInitially = lastNavigatedParentKey == node.treeParentKey,
                    onEdit = if (resubmit && editable) {
                        { onEditMessage(node.message.messageId) }
                    } else {
                        null
                    },
                    onRegenerate = if (resubmit && !node.message.isCreatedByUser) {
                        { onRegenerateMessage(node.message.messageId) }
                    } else {
                        null
                    },
                    onCopy = { onCopyMessage(node.message.messageId) },
                    onFeedback = if (!node.message.isCreatedByUser && feedbackEnabled && !readOnly) {
                        { feedback -> onFeedback(node.message.messageId, feedback) }
                    } else {
                        null
                    },
                    onReadAloud = if (readOnly) null else ({ onReadAloud(node.message.messageId) }),
                    onFork = if (readOnly) null else ({ onFork(node.message.messageId) }),
                    baseUrl = baseUrl,
                    fontSizeMultiplier = fontSizeMultiplier,
                    isReading = currentlyReadingMessageId == node.message.messageId,
                    currentFeedback = node.message.feedback?.rating,
                    editSession = editSession,
                    userAvatarUrl = userAvatarUrl,
                    userName = userName,
                    selectedEndpoint = selectedEndpoint,
                    showImageDescriptions = showImageDescriptions,
                    chatLayoutStyle = chatLayoutStyle,
                    showAvatars = showAvatars,
                    showBubbles = showBubbles,
                    useKatex = useKatex,
                    searchQuery = searchQuery,
                    isSearchMatch = isMatch,
                    isCurrentSearchMatch = isCurrent,
                    searchFocusedOccurrence = focusedOccurrenceInMessage,
                    onFocusedOccurrencePosition = if (isCurrent && searchQuery != null) {
                        { coordinates, matchRect ->
                            // Only record while a jump is pending; the focused node keeps reporting
                            // on every later scroll pass, and consuming those would churn state.
                            if (searchFocusRequest != null) {
                                focusedMatchReport = FocusedSearchMatchReport(
                                    requestId = searchFocusRequest.requestId,
                                    coordinates = coordinates,
                                    matchRect = matchRect,
                                )
                            }
                        }
                    } else {
                        null
                    },
                )
                if (resubmit && stoppedReply?.messageId == node.message.messageId) {
                    StoppedReplyFooter(
                        action = stoppedReply,
                        modifier = Modifier.streamingContentColumnPadding(chatLayoutStyle, showAvatars, showBubbles),
                    )
                }
                }
                }
            }

            if (isStreaming) {
                // Key the reply to the slot its finalized message will occupy: it attaches to
                // the last message in the truncated path, whose id is the finalized node's
                // treeParentKey (see itemsIndexed key above). Sharing that key makes the swap
                // an in-place content update rather than a scroll-resetting remove+add.
                val streamingSlotKey = displayMessages.lastOrNull()?.message?.messageId
                    ?: "streaming_message"
                // Workspace changes get their own item; tool cards, office previews and memory
                // take the rest. The bubble's markdown images resolve against all of them.
                val streamingWorkspace = partitionWorkspaceChanges(liveAttachments)
                // The message the reply answers, which its saved message names as its parent.
                val replyScope = expansionScope(ExpansionPane.PRIMARY, displayMessages.lastOrNull()?.message?.messageId)
                item(key = streamingSlotKey) {
                    ProvideMarkdownMedia(liveAttachments, baseUrl) {
                        StreamingMessageBubble(
                            streamingContent = streamingContent,
                            senderName = streamingSenderName,
                            senderIconUrl = null,
                            fontSizeMultiplier = fontSizeMultiplier,
                            selectedEndpoint = selectedEndpoint,
                            chatLayoutStyle = chatLayoutStyle,
                            showAvatars = showAvatars,
                            showBubbles = showBubbles,
                            useKatex = useKatex,
                            streamingThinking = streamingThinking,
                            // Inside the reply, under its sender row, where the finalized
                            // message draws the cards that replace them.
                            pendingSkills = pendingSkills,
                        )
                    }
                }

                if (renderedToolCalls.isNotEmpty()) {
                    items(
                        items = renderedToolCalls,
                        key = { "tool_call_${it.id}" },
                        contentType = { "tool_call" },
                    ) { toolCall ->
                        CompositionLocalProvider(LocalExpansionScope provides replyScope) {
                            StreamingToolCallCard(
                                toolCall = toolCall,
                                modifier = Modifier.streamingContentColumnPadding(chatLayoutStyle, showAvatars, showBubbles),
                                baseUrl = baseUrl,
                                streamingAttachments = streamingWorkspace.inline,
                                showImageDescriptions = showImageDescriptions,
                                sandboxStarting = toolCall.id in sandboxStartingToolCallIds,
                                isLatestStep = toolCall.id == renderedToolCalls.lastOrNull()?.id,
                                replyLength = streamingContent.length,
                            )
                        }
                    }
                }

                if (streamingWorkspace.changes.isNotEmpty()) {
                    item(key = "streaming_workspace_changes", contentType = "workspace_changes") {
                        CompositionLocalProvider(LocalExpansionScope provides replyScope) {
                            WorkspaceChangesSection(
                                changes = streamingWorkspace.changes,
                                baseUrl = baseUrl,
                                stateKey = "streaming",
                                modifier = Modifier.streamingContentColumnPadding(chatLayoutStyle, showAvatars, showBubbles),
                                live = true,
                            )
                        }
                    }
                }

                // Live office-doc previews (v0.8.6) once ready. Pending and failed ones are rows
                // under their tool call, or here when no rendered call claims them; see
                // isInlineOfficePreview.
                val streamingCallIds = renderedToolCalls.map { it.id }
                val officeAttachments = streamingWorkspace.inline.filter(::isInlineOfficePreview)
                if (officeAttachments.isNotEmpty() ||
                    unclaimedOfficePreviewRows(streamingWorkspace.inline, streamingCallIds).isNotEmpty()
                ) {
                    item(key = "streaming_office_previews") {
                        OfficePreviewAttachments(
                            attachments = streamingWorkspace.inline,
                            renderedToolCallIds = streamingCallIds,
                            baseUrl = baseUrl,
                            modifier = Modifier.streamingContentColumnPadding(chatLayoutStyle, showAvatars, showBubbles),
                            isDarkTheme = isSurfaceDark(),
                        )
                    }
                }

                val orphanMemory = collectUnrenderedMemoryArtifacts(
                    streamingWorkspace.inline,
                    streamingCallIds,
                )
                if (orphanMemory.isNotEmpty()) {
                    // Keyed per reply, so the next run's line never restores this one's open state.
                    item(key = "streaming_memory_artifacts_$streamingSlotKey") {
                        CompositionLocalProvider(LocalExpansionScope provides replyScope) {
                            MemoryArtifactsSection(
                                artifacts = orphanMemory,
                                modifier = Modifier.streamingContentColumnPadding(chatLayoutStyle, showAvatars, showBubbles),
                                stateKey = memoryArtifactsStateKey(streamingWorkspace.inline, fallback = ""),
                                onManage = LocalOpenMemories.current,
                                live = true,
                            )
                        }
                    }
                }

                // Human-review pause (v0.8.8): the run is waiting on the user, whose controls are
                // docked by the composer (AskUserQuestionPanel, ToolApprovalPanel). The tail of the
                // still-unfinished reply keeps a one-line marker so it reads as waiting rather than
                // stalled; the continuation streams back into the bubble above.
                if (pendingAction != null) {
                    item(key = "pending_action_${pendingAction.actionId}") {
                        val markerModifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        if (pendingAction.isAskUserQuestion) {
                            AskUserQuestionWaitingMarker(modifier = markerModifier)
                        } else {
                            ToolApprovalWaitingMarker(modifier = markerModifier)
                        }
                    }
                }
            }
        }

        ScrollToBottomButton(
            listState = listState,
            follower = follower,
            extraEndPx = extraEndPx,
            backdrop = LocalGlassBackdrop.current.takeIf { recordsGlassBackdrop },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = scrollToBottomPadding ?: scrollToBottomButtonPadding(bottomContentPadding)),
        )
    }
}

/** How long phase 2 waits for the focused occurrence to report before settling for the message top. */
private const val SEARCH_FOCUS_REPORT_TIMEOUT_MS = 500L

/** How many times a focused match is re-placed while the message around it settles, and how far apart. */
private const val SEARCH_FOCUS_SETTLE_PASSES = 4
private const val SEARCH_FOCUS_SETTLE_MS = 250L

/** A settled match this close to the anchor line (px) is left where it is. */
private const val SEARCH_FOCUS_SETTLE_TOLERANCE_PX = 48f

private val SearchFocusScrollSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

/**
 * Latest reported position of the focused search occurrence: the reporting text node's
 * [coordinates] (a live handle — queries return current geometry) and the match's
 * bounding [matchRect] within that node. Tagged with the [requestId] that was live when it was
 * emitted, so the scroll effect only ever acts on a report for the exact request it is serving
 * (and never mistakes a stale report from a prior wrap-around jump for the current one).
 */
private class FocusedSearchMatchReport(
    val requestId: Long,
    val coordinates: LayoutCoordinates,
    val matchRect: Rect,
) {
    fun matches(request: SearchFocusRequest): Boolean = requestId == request.requestId
}
