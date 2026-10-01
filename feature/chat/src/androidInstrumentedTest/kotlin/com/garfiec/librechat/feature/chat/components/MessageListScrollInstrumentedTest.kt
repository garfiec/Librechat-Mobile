package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.PendingAction
import com.garfiec.librechat.core.model.PendingActionPayload
import com.garfiec.librechat.core.model.PendingActionTypes
import com.garfiec.librechat.core.model.ToolApprovalRequest
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.chat.util.buildActiveMessagePath
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * Two things [MessageList] does to the scroll position on its own, and when it must not.
 *
 * **The streaming follower must stand down while a run is paused for human review.** A pause
 * does not end the run: `isStreaming` stays true across it, so the per-frame follower keeps
 * pinning the list's tail to the bottom. The pause's controls are docked by the composer, and the
 * thread keeps only a one-line marker — the user reading the paused reply above it must be able to
 * scroll without the follower dragging them back. The pair below applies the same stimulus (the
 * reply grows) and differs only in whether a pause is outstanding, so the control proves the
 * measurement can actually detect chasing.
 *
 * **The keyboard must not cost the user the field they just tapped.** The viewport-shrink handler
 * jumps to the tail of the last item, which would scroll an inline message edit out of sight the
 * moment the keyboard came up — so it stands down while the focus is inside the list, and leaves
 * bringing the field into view to foundation.
 *
 * Position is read from the anchor's own bounds rather than from `LazyListState`, which
 * [MessageList] owns internally and does not expose; the anchor stays composed either way because a
 * LazyColumn item composes whole while any part of it is on screen.
 */
@RunWith(AndroidJUnit4::class)
class MessageListScrollInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** Grown after the first frame; recomposition feeds it back into the list. */
    private var streamingContent by mutableStateOf(SHORT_STREAM)
    private var isStreaming by mutableStateOf(true)
    private var paused by mutableStateOf(false)
    private var editingMessageId by mutableStateOf<String?>(null)

    /** Stands in for the Scaffold's imePadding: raising it shrinks the list's viewport. */
    private var keyboardInset by mutableStateOf(0.dp)

    /**
     * The follower runs off `withFrameNanos`, which leaves Compose permanently non-idle for the
     * whole run — every finder call syncs on idle first, so on the automatic clock they all time
     * out. Driving the clock by hand is the only way to observe a frame loop at all.
     */
    @Before
    fun driveTheClockByHand() {
        composeRule.mainClock.autoAdvance = false
    }

    @Test
    fun theFollowerChasesAGrowingTailWhileOutputStreams() {
        setChat()
        val anchorTop = settledTop(SHORT_STREAM)

        composeRule.runOnUiThread { streamingContent = LONG_STREAM }
        advanceFrames(CHASE_FRAMES)

        // The control: with no pause the follower is expected to chase, which is what proves the
        // measurement below can tell chasing from stillness.
        val moved = anchorTop - topOf(SHORT_STREAM)
        assertTrue("the follower did not chase a growing reply; moved ${moved}px", moved > CHASE_SLACK_PX)
    }

    @Test
    fun aPauseStopsTheFollowerChasingAGrowingTail() {
        // A pause arrives mid-run, onto a list the follower has already brought to its tail.
        setChat()
        advanceFrames(SETTLE_FRAMES)
        composeRule.runOnUiThread { paused = true }
        // Past the one-shot scroll that brings the pause marker into view — that one is wanted.
        val anchorTop = settledTop(SHORT_STREAM)

        composeRule.runOnUiThread { streamingContent = LONG_STREAM }
        advanceFrames(CHASE_FRAMES)

        // Same growth, same number of frames the control needed. Nothing may move.
        val moved = anchorTop - topOf(SHORT_STREAM)
        assertTrue("the follower chased the paused reply by ${moved}px", abs(moved) < STILL_TOLERANCE_PX)
    }

    /**
     * Editing a message above the tail: the handler's jump to the last item would carry the field
     * off screen. The inset is raised directly rather than by summoning a real IME, because the
     * handler keys on the viewport shrinking and nothing else.
     */
    @Test
    fun theKeyboardLeavesTheFocusedEditFieldOnScreen() {
        startEditing(EARLIER_MESSAGE)

        val before = fieldVsViewport()
        assertTrue("the field was already off screen before the keyboard: $before", before.isVisible)

        composeRule.runOnUiThread { keyboardInset = KEYBOARD_HEIGHT }
        advanceFrames(CHASE_FRAMES)

        val after = fieldVsViewport()
        assertTrue("the keyboard pushed the focused field off screen: $after", after.isVisible)
    }

    /**
     * The other half of leaving this to foundation: a field low enough that the keyboard really
     * does cover it still has to be lifted clear, and nothing in [MessageList] does that.
     */
    @Test
    fun theKeyboardLiftsTheLastEditFieldClear() {
        startEditing(LAST_MESSAGE)

        composeRule.runOnUiThread { keyboardInset = KEYBOARD_HEIGHT }
        advanceFrames(CHASE_FRAMES)

        val after = fieldVsViewport()
        assertTrue("the keyboard covered the focused field: $after", after.isVisible)
    }

    // ── harness ───────────────────────────────────────────────────────────────

    private fun setChat() {
        composeRule.setContent {
            // ParsedMarkdownCache is normally provided by ChatRoot; the harness renders
            // MessageList directly, so it supplies its own.
            val markdownCache = remember { ParsedMarkdownCache() }
            CompositionLocalProvider(LocalParsedMarkdownCache provides markdownCache) {
                LibreChatTheme {
                    Box(Modifier.fillMaxSize().padding(bottom = keyboardInset)) {
                        MessageList(
                            displayMessages = buildActiveMessagePath(THREAD),
                            isStreaming = isStreaming,
                            streamingContent = if (isStreaming) streamingContent else "",
                            onSiblingNavigation = { _, _ -> },
                            onEditMessage = {},
                            onRegenerateMessage = {},
                            onCopyMessage = {},
                            pendingAction = if (paused) TOOL_PAUSE else null,
                            editingMessageId = editingMessageId,
                            editingText = editingMessageId?.let { id -> "Edited text for $id" }.orEmpty(),
                            onEditTextChange = {},
                            onEditSaveAndSubmit = {},
                            onEditSaveOnly = {},
                            onEditCancel = {},
                        )
                    }
                }
            }
        }
    }

    /** Opens [messageId] for inline edit — a settled thread, since editing is gated on no run — and focuses its field. */
    private fun startEditing(messageId: String) {
        isStreaming = false
        editingMessageId = messageId
        setChat()
        advanceFrames(SETTLE_FRAMES)
        scrollIntoView(editAnchor(messageId))
        composeRule.onNode(hasSetTextAction()).performClick()
        advanceFrames(FOCUS_FRAMES)
    }

    private fun editAnchor(messageId: String) = "Edited text for $messageId"

    /**
     * `performScrollToNode` can't be used here: it loops scroll-then-wait until the node shows
     * up, and on the hand-driven clock no frame ever applies the scroll, so it spins forever.
     * This issues one semantics scroll per step and advances the frames that apply it.
     */
    private fun scrollIntoView(anchor: String) {
        repeat(MAX_SCROLL_STEPS) {
            val list = composeRule.onNode(hasScrollAction()).fetchSemanticsNode().boundsInRoot
            val target = composeRule.onAllNodesWithText(anchor, substring = true)
                .fetchSemanticsNodes()
                .map { it.boundsInRoot }
                .firstOrNull { it.height > 0f }
            // Not composed yet means it is further down, toward the list's tail.
            val delta = when {
                target == null -> list.height / 2
                target.top < list.top -> target.top - list.top
                target.bottom > list.bottom -> target.bottom - list.bottom
                else -> return
            }
            composeRule.onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) {
                it(0f, delta)
            }
            advanceFrames(FOCUS_FRAMES)
        }
        error("\"$anchor\" never scrolled into view")
    }

    /** Where the focused edit field sits relative to the list's own (clipping) bounds. */
    private fun fieldVsViewport(): Placement {
        val field = composeRule.onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot
        val list = composeRule.onNode(hasScrollAction()).fetchSemanticsNode().boundsInRoot
        return Placement(field.top, field.bottom, list.top, list.bottom)
    }

    data class Placement(
        val fieldTop: Float,
        val fieldBottom: Float,
        val listTop: Float,
        val listBottom: Float,
    ) {
        /**
         * A node clipped entirely out of the list reports `Rect.Zero`, which satisfies any naive
         * bounds comparison — so an empty rect has to be rejected outright or this asserts nothing.
         */
        val isVisible: Boolean
            get() = fieldBottom > fieldTop && fieldTop >= listTop && fieldBottom <= listBottom
    }

    private fun topOf(text: String): Float =
        composeRule.onNodeWithText(text, substring = true).fetchSemanticsNode().boundsInRoot.top

    /**
     * The anchor's position once the opening scroll is done.
     *
     * Both cases open with a scroll of their own — the jump that arms a run, the animation that
     * brings a new pause marker into view — and a baseline read mid-flight would score that
     * opening scroll as the chase under test.
     */
    private fun settledTop(text: String): Float {
        advanceFrames(SETTLE_FRAMES)
        return topOf(text)
    }

    private fun advanceFrames(count: Int) {
        repeat(count) { composeRule.mainClock.advanceTimeByFrame() }
        composeRule.waitForIdle()
    }

    private companion object {
        const val CONVO = "convo-1"

        /**
         * Long enough that the thread fills the viewport and the list can actually scroll.
         *
         * Parented into a chain rather than left flat: the list keys its items by the tree parent,
         * so a flat thread hands LazyColumn the same NO_PARENT key twelve times and measurement
         * throws before any of this can be observed.
         */
        val THREAD = (1..12).map { index ->
            Message(
                messageId = "m$index",
                conversationId = CONVO,
                parentMessageId = if (index == 1) null else "m${index - 1}",
                text = "Turn $index of the conversation, long enough to occupy a line or two.",
                isCreatedByUser = index % 2 == 1,
                sender = "TestBot",
            )
        }

        /** Two turns above the tail: the shape the handler's jump-to-last would scroll away. */
        const val EARLIER_MESSAGE = "m10"
        const val LAST_MESSAGE = "m12"

        val TOOL_PAUSE = PendingAction(
            actionId = "action-1",
            conversationId = CONVO,
            payload = PendingActionPayload(
                type = PendingActionTypes.TOOL_APPROVAL,
                actionRequests = listOf(ToolApprovalRequest(name = "provision_cluster", toolCallId = "call-0")),
            ),
        )

        const val SHORT_STREAM = "Working on it"
        val LONG_STREAM = SHORT_STREAM + (1..60).joinToString("") { "\nreply line $it" }

        /** Well past a single eased step, so a follower that runs at all clears it. */
        const val CHASE_SLACK_PX = 40f

        /** A list that never scrolled leaves the anchor exactly where it was; this is rounding. */
        const val STILL_TOLERANCE_PX = 2f

        /** Enough frames for the opening scroll to land and for a live follower to show itself. */
        const val SETTLE_FRAMES = 150
        const val CHASE_FRAMES = 150
        const val FOCUS_FRAMES = 60
        const val MAX_SCROLL_STEPS = 8

        val KEYBOARD_HEIGHT: Dp = 340.dp
    }
}
