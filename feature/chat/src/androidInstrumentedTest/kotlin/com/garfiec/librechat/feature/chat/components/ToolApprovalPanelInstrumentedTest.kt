package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
    private val submitted = mutableListOf<List<ToolApprovalResolution>>()

    @Test
    fun compactApprovingEachCallWalksTheBatchThenSubmitsIt() {
        setPanel(batch("search_web", "read_file"), width = COMPACT)

        composeRule.onNodeWithText("1 of 2").assertExists()
        composeRule.onNodeWithText("Approve").performClick()
        afterTheBeat()
        composeRule.onNodeWithText("2 of 2").assertExists()
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

        composeRule.onNodeWithText("1 of 2").assertExists()
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

    // ── harness ───────────────────────────────────────────────────────────────

    /** Past the pause a pick waits before moving on, which idling alone does not advance. */
    private fun afterTheBeat() {
        composeRule.mainClock.advanceTimeBy(PAUSE_PANEL_AUTO_ADVANCE_DELAY_MS + BEAT_SLACK_MS)
        composeRule.waitForIdle()
    }

    private fun setPanel(action: PendingAction, width: Dp) {
        composeRule.setContent {
            // A phone-sized test device is ~420dp wide, which would clamp the wide layout's width
            // back under the 600dp split; a lower density fits it on screen at its real dp size.
            CompositionLocalProvider(LocalDensity provides Density(HARNESS_DENSITY)) {
                LibreChatTheme {
                    ToolApprovalPanel(
                        pendingAction = action,
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
