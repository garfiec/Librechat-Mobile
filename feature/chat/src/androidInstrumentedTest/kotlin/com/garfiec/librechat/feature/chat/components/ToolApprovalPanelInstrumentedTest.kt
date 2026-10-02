package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.garfiec.librechat.core.model.PendingAction
import com.garfiec.librechat.core.model.PendingActionPayload
import com.garfiec.librechat.core.model.PendingActionTypes
import com.garfiec.librechat.core.model.ToolApprovalDecisions
import com.garfiec.librechat.core.model.ToolApprovalRequest
import com.garfiec.librechat.core.model.ToolReviewConfig
import com.garfiec.librechat.core.model.request.ToolApprovalResolution
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.chat.util.ToolDecisionDraft
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The docked tool-approval panel's own behaviour, driven through a host that hoists its state the
 * way `PendingActionDelegate` does — the panel is stateless, so a test that fed it fixed values
 * could not see a pick move it on.
 */
@RunWith(AndroidJUnit4::class)
class ToolApprovalPanelInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var drafts by mutableStateOf(emptyMap<String, ToolDecisionDraft>())
    private var activeCallId by mutableStateOf<String?>(null)
    private var collapsed by mutableStateOf(false)
    private var pending by mutableStateOf<PendingAction?>(null)
    private val submitted = mutableListOf<List<ToolApprovalResolution>>()

    @Test
    fun compactApprovingEachCallWalksTheBatchThenSubmitsIt() {
        setPanel(batch("search_web", "read_file"), width = COMPACT)

        assertPage("1 of 2")
        composeRule.onNodeWithText("Approve").performClick()
        afterTheBeat()
        assertPage("2 of 2")
        assertTrue("submitted before the batch was complete", submitted.isEmpty())

        composeRule.onNodeWithText("Reject").performClick()
        afterTheBeat()

        assertEquals(1, submitted.size)
        assertEquals(
            listOf(ToolApprovalDecisions.APPROVE, ToolApprovalDecisions.REJECT),
            submitted.single().map { it.decision },
        )
    }

    @Test
    fun anEditDoesNotAdvanceAndStartsFromTheModelsArguments() {
        setPanel(batch("search_web", "read_file", allowEdit = true), width = COMPACT)

        composeRule.onNodeWithText("Edit").performClick()
        afterTheBeat()

        assertPage("1 of 2")
        assertEquals("""{"q":"call-1"}""", drafts["call-1"]?.editedArguments)
        assertTrue(submitted.isEmpty())
    }

    @Test
    fun approveAllSubmitsTheWholeBatchAtOnce() {
        setPanel(batch("search_web", "read_file", "write_file"), width = COMPACT)

        composeRule.onNodeWithText("Approve all").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("call-1", "call-2", "call-3"), submitted.single().map { it.toolCallId })
    }

    @Test
    fun bulkShortcutsHideWhenOneCallsPolicyForbidsThem() {
        val payload = batch("search_web", "read_file")
        val restricted = payload.copy(
            payload = payload.payload!!.copy(
                reviewConfigs = listOf(
                    config("call-1", ToolApprovalDecisions.APPROVE, ToolApprovalDecisions.REJECT),
                    config("call-2", ToolApprovalDecisions.REJECT),
                ),
            ),
        )
        setPanel(restricted, width = COMPACT)

        composeRule.onNodeWithText("Approve all").assertDoesNotExist()
        composeRule.onNodeWithText("Reject all").assertExists()
    }

    @Test
    fun wideLayoutAdvancesTabsButOnlyContinueSubmits() {
        setPanel(batch("search_web", "read_file"), width = WIDE)

        composeRule.onNodeWithText("Approve").performClick()
        afterTheBeat()
        assertEquals("call-2", activeCallId)

        composeRule.onNodeWithText("Approve").performClick()
        afterTheBeat()
        assertTrue("the wide layout submitted on a pick", submitted.isEmpty())

        composeRule.onNodeWithText("Continue (2/2)").performClick()
        composeRule.waitForIdle()
        assertEquals(1, submitted.size)
    }

    @Test
    fun collapsedPanelIsInertAndATapExpandsIt() {
        collapsed = true
        setPanel(batch("search_web", "read_file"), width = COMPACT)

        composeRule.onNodeWithText("Approve").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Next tool call").assertIsNotEnabled()

        composeRule.onNodeWithText("search_web").performClick()
        composeRule.waitForIdle()
        assertEquals(false, collapsed)
    }

    @Test
    fun compactScrubbingTheDotsWalksTheBatchWithoutSubmitting() {
        setPanel(batch("search_web", "read_file", "write_file"), width = COMPACT)

        composeRule.onNodeWithTag(PAUSE_PANEL_PAGE_DOTS_TAG).performTouchInput {
            swipe(center, center + Offset(120.dp.toPx(), 0f))
        }
        composeRule.waitForIdle()

        assertEquals("call-3", activeCallId)
        assertPage("3 of 3")
        assertTrue(submitted.isEmpty())
    }

    /** A scrub moves one item per 48dp of travel, not one per dot: 60dp from the middle reaches only the next. */
    @Test
    fun compactScrubMovesOneItemPerStep() {
        setPanel(batch("search_web", "read_file", "write_file"), width = COMPACT)

        composeRule.onNodeWithTag(PAUSE_PANEL_PAGE_DOTS_TAG).performTouchInput {
            swipe(center, center + Offset(60.dp.toPx(), 0f))
        }
        composeRule.waitForIdle()

        assertEquals("call-2", activeCallId)
    }

    @Test
    fun compactTappingADotJumpsToIt() {
        setPanel(batch("search_web", "read_file", "write_file"), width = COMPACT)

        composeRule.onNodeWithTag(PAUSE_PANEL_PAGE_DOTS_TAG).performTouchInput {
            click(Offset(width * 5f / 6f, centerY))
        }
        composeRule.waitForIdle()

        assertEquals("call-3", activeCallId)
    }

    @Test
    fun collapsedDotsAreInert() {
        collapsed = true
        setPanel(batch("search_web", "read_file", "write_file"), width = COMPACT)

        composeRule.onNodeWithTag(PAUSE_PANEL_PAGE_DOTS_TAG).performTouchInput { swipe(centerLeft, centerRight) }
        composeRule.waitForIdle()

        assertEquals(null, activeCallId)
    }

    @Test
    fun wideSwipeOnTheDecisionsPagesBetweenCalls() {
        setPanel(batch("search_web", "read_file"), width = WIDE)

        swipeFromApprove(fraction = -0.5f)
        assertEquals("call-2", activeCallId)

        swipeFromApprove(fraction = 0.5f)
        assertEquals("call-1", activeCallId)
    }

    /** The question's text and arguments are for reading: only the answer controls page. */
    @Test
    fun wideSwipeOutsideTheDecisionsDoesNotPage() {
        setPanel(batch("search_web", "read_file"), width = WIDE)

        val areaWidth = composeRule.onNodeWithTag(PAUSE_PANEL_SWIPE_AREA_TAG).fetchSemanticsNode().size.width
        composeRule.onNodeWithText("Runs search_web for the assistant.").performTouchInput {
            swipe(center, center - Offset(areaWidth / 2f, 0f))
        }
        composeRule.onNodeWithText("""{"q":"call-1"}""").performTouchInput {
            swipe(center, center - Offset(areaWidth / 2f, 0f))
        }
        composeRule.waitForIdle()

        assertEquals(null, activeCallId)
    }

    @Test
    fun wideSwipePastTheLastCallStaysAndDoesNotSubmit() {
        activeCallId = "call-2"
        setPanel(batch("search_web", "read_file"), width = WIDE)

        swipeFromApprove(fraction = -0.5f)

        assertEquals("call-2", activeCallId)
        assertTrue(submitted.isEmpty())
    }

    @Test
    fun wideShortSlowDragSpringsBack() {
        setPanel(batch("search_web", "read_file"), width = WIDE)

        swipeFromApprove(fraction = -0.1f, durationMillis = 600)

        assertEquals(null, activeCallId)
    }

    /** Short of a quarter of the width, so only its speed can carry it over. */
    @Test
    fun wideQuickFlickPages() {
        setPanel(batch("search_web", "read_file"), width = WIDE)

        swipeFromApprove(fraction = -0.15f, durationMillis = 60)

        assertEquals("call-2", activeCallId)
    }

    @Test
    fun wideSwipeInsideTheEditFieldDoesNotPage() {
        setPanel(batch("search_web", "read_file", allowEdit = true), width = WIDE)
        composeRule.onNodeWithText("Edit").performClick()
        composeRule.waitForIdle()

        composeRule.onNode(hasSetTextAction()).performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        assertEquals(null, activeCallId)
    }

    @Test
    fun wideDraggingAlongTheTabsSelectsTheOneUnderTheFinger() {
        setPanel(batch("search_web", "read_file", "write_file"), width = WIDE)

        val tabs = composeRule.onNodeWithTag(PAUSE_PANEL_TABS_TAG)
        val readFile = composeRule.onNodeWithText("read_file").fetchSemanticsNode().boundsInRoot
        val tabsLeft = tabs.fetchSemanticsNode().boundsInRoot.left
        tabs.performTouchInput {
            swipe(Offset(4f, centerY), Offset(readFile.center.x - tabsLeft, centerY))
        }
        composeRule.waitForIdle()

        assertEquals("call-2", activeCallId)
        assertTrue(submitted.isEmpty())
    }

    /** One pause's resume can race the run's next pause, which then replaces it on screen. */
    @Test
    fun wideReplacingThePauseWithAShorterOneOpensItsOwnPage() {
        activeCallId = "call-3"
        setPanel(batch("search_web", "read_file", "write_file"), width = WIDE)

        activeCallId = null
        pending = batch("delete_file")
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Runs delete_file for the assistant.").assertExists()
    }

    @Test
    fun wideTappingATabStillSelectsIt() {
        setPanel(batch("search_web", "read_file", "write_file"), width = WIDE)

        composeRule.onNodeWithText("write_file").performClick()
        composeRule.waitForIdle()

        assertEquals("call-3", activeCallId)
    }

    // ── harness ───────────────────────────────────────────────────────────────

    /**
     * Drags from the shown call's Approve button by [fraction] of the swipe area's width (negative
     * is leftward) — a finger travels well past the button it lands on.
     */
    private fun swipeFromApprove(fraction: Float, durationMillis: Long = 200L) {
        val areaWidth = composeRule.onNodeWithTag(PAUSE_PANEL_SWIPE_AREA_TAG).fetchSemanticsNode().size.width
        composeRule.onNodeWithText("Approve").performTouchInput {
            swipe(center, center + Offset(areaWidth * fraction, 0f), durationMillis)
        }
        composeRule.waitForIdle()
    }

    private fun assertPage(page: String) {
        composeRule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, page)).assertExists()
    }

    /** Past the pause a pick waits before moving on, which idling alone does not advance. */
    private fun afterTheBeat() {
        composeRule.mainClock.advanceTimeBy(PAUSE_PANEL_AUTO_ADVANCE_DELAY_MS + BEAT_SLACK_MS)
        composeRule.waitForIdle()
    }

    private fun setPanel(action: PendingAction, width: Dp) {
        pending = action
        composeRule.setContent {
            // A phone-sized test device is ~420dp wide, which would clamp the wide layout's width
            // back under the 600dp split; a lower density fits it on screen at its real dp size.
            CompositionLocalProvider(LocalDensity provides Density(HARNESS_DENSITY)) {
                LibreChatTheme {
                    ToolApprovalPanel(
                        pendingAction = pending ?: action,
                        isResolving = false,
                        drafts = drafts,
                        activeCallId = activeCallId,
                        collapsed = collapsed,
                        onDraftChange = { id, draft -> drafts = drafts + (id to draft) },
                        onSelectCall = { activeCallId = it },
                        onCollapsedChange = { collapsed = it },
                        onSubmit = { submitted += it },
                        modifier = Modifier.width(width),
                    )
                }
            }
        }
    }

    private fun batch(vararg names: String, allowEdit: Boolean = false): PendingAction {
        val requests = names.mapIndexed { index, name ->
            ToolApprovalRequest(
                name = name,
                toolCallId = "call-${index + 1}",
                arguments = buildJsonObject { put("q", JsonPrimitive("call-${index + 1}")) },
                description = "Runs $name for the assistant.",
            )
        }
        val decisions = listOfNotNull(
            ToolApprovalDecisions.APPROVE,
            ToolApprovalDecisions.REJECT,
            ToolApprovalDecisions.EDIT.takeIf { allowEdit },
        )
        return PendingAction(
            actionId = "action-1",
            conversationId = "convo-1",
            payload = PendingActionPayload(
                type = PendingActionTypes.TOOL_APPROVAL,
                actionRequests = requests,
                reviewConfigs = requests.map { config(it.toolCallId, *decisions.toTypedArray()) },
            ),
        )
    }

    private fun config(toolCallId: String, vararg decisions: String) = ToolReviewConfig(
        actionName = "tool",
        toolCallId = toolCallId,
        allowedDecisions = decisions.toList(),
    )

    private companion object {
        val COMPACT = 380.dp
        val WIDE = 720.dp
        const val BEAT_SLACK_MS = 100L
        const val HARNESS_DENSITY = 1.5f
    }
}
