package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.chat.util.DraftItems
import com.garfiec.librechat.feature.chat.util.draftItemsOf
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The attachment list takes focus at its header when a screen reader opened it, so the next swipe
 * reads the list rather than the field; a touch open leaves focus (and the keyboard) where it was.
 * The stack renders through `composerFieldLeadingIcon`, the slot both composers draw, with the
 * toggles `ChatScreen` hands them ([withDraftList] over a [DraftListState]), and the panel opens
 * the way `ChatScreen` gates it, a tool-approval pause included ([DraftListPauseEffects]). On
 * Robolectric, so it runs with the unit tests in CI, which only compiles the instrumented ones.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ComposerDraftListFocusTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val skills =
        draftItemsOf(files = emptyList(), skills = listOf("summarize", "translate"), quotes = emptyList(), serverUrl = "")

    private val failedUpload = draftItemsOf(
        files = listOf(AttachedFile(uri = "content://report", name = "report.pdf", uploadFailed = true)),
        skills = emptyList(),
        quotes = emptyList(),
        serverUrl = "",
    )

    private fun host(items: DraftItems = skills): DraftListState {
        val state = DraftListState()
        composeRule.setContent {
            LibreChatTheme {
                val draftList = remember { state }
                val chrome = ComposerChrome(draftItems = items).withDraftList(draftList)
                Column {
                    if (chrome.isDraftListOpen) {
                        ComposerDraftListPanel(items = items, onRemove = {}, onRetry = {}, focusHeader = draftList.focusHeader)
                    }
                    composerFieldLeadingIcon(comparison = null, chrome = chrome, compact = false)!!()
                    FailedUploadsLine(chrome = chrome)
                }
            }
        }
        return state
    }

    /**
     * A tool-approval pause as `ChatScreen` wires it: the panel starts expanded, opening the list
     * collapses it (the ViewModel's `setToolPanelCollapsed(true)`), and the list shows once it is.
     */
    private inner class ToolPauseHost(items: DraftItems) {
        val state = DraftListState()
        var collapsed by mutableStateOf(false)
        var collapses = 0
        val retried = mutableListOf<AttachedFile>()
        val removed = mutableListOf<String>()

        init {
            composeRule.setContent {
                LibreChatTheme {
                    val draftList = remember { state }
                    val expanded = !collapsed
                    DraftListPauseEffects(draftList, pausePanelUp = true, toolPauseExpanded = expanded)
                    val chrome = ComposerChrome(draftItems = items).withDraftList(
                        draftList,
                        toolPauseExpanded = expanded,
                        onCollapseToolPause = {
                            collapses++
                            collapsed = true
                        },
                    )
                    Column {
                        if (chrome.isDraftListOpen && !expanded) {
                            ComposerDraftListPanel(
                                items = items,
                                onRemove = { removed += it.key },
                                onRetry = { retried += it },
                                focusHeader = draftList.focusHeader,
                            )
                        }
                        composerFieldLeadingIcon(comparison = null, chrome = chrome, compact = false)!!()
                        FailedUploadsLine(chrome = chrome)
                    }
                }
            }
        }
    }

    /**
     * The list open with no pause, then a pause panel arriving: an ask (the composer goes, so
     * `ChatScreen` stops drawing the list) or a tool pause restored collapsed.
     */
    private inner class PauseArrivalHost(items: DraftItems) {
        val state = DraftListState()
        var pausePanelUp by mutableStateOf(false)

        init {
            composeRule.setContent {
                LibreChatTheme {
                    val draftList = remember { state }
                    DraftListPauseEffects(draftList, pausePanelUp = pausePanelUp, toolPauseExpanded = false)
                    val chrome = ComposerChrome(draftItems = items).withDraftList(draftList)
                    Column {
                        if (chrome.isDraftListOpen && !pausePanelUp) {
                            ComposerDraftListPanel(items = items, onRemove = {}, onRetry = {}, focusHeader = draftList.focusHeader)
                        }
                        composerFieldLeadingIcon(comparison = null, chrome = chrome, compact = false)!!()
                    }
                }
            }
        }
    }

    @Test
    fun aPausePanelComingUpClosesTheListAndItStaysClosedAfter() {
        val pause = PauseArrivalHost(skills)
        composeRule.onNodeWithContentDescription("2 items with this message").performClick()
        composeRule.waitForIdle()
        assertThat(pause.state.isOpen).isTrue()

        pause.pausePanelUp = true
        composeRule.waitForIdle()
        assertThat(pause.state.isOpen).isFalse()

        // Answered: the list doesn't pop back over the thread on its own.
        pause.pausePanelUp = false
        composeRule.waitForIdle()
        assertThat(pause.state.isOpen).isFalse()
        composeRule.onNodeWithText("With this message · 2").assertDoesNotExist()
    }

    @Test
    fun aScreenReaderOpenFocusesTheHeader() {
        host()
        composeRule.onNodeWithContentDescription("2 items with this message")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("With this message · 2").assertIsFocused()
    }

    @Test
    fun aTouchOpenLeavesFocusAlone() {
        host()
        composeRule.onNodeWithContentDescription("2 items with this message").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("With this message · 2").assertIsNotFocused()
    }

    @Test
    fun aScreenReaderOpenFromTheFailedUploadLineFocusesTheHeader() {
        host(items = failedUpload)
        composeRule.onNodeWithText("1 file didn't upload", substring = true)
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("With this message · 1").assertIsFocused()
    }

    @Test
    fun aTouchOnTheFailedUploadLineOpensWithoutTakingFocus() {
        host(items = failedUpload)
        composeRule.onNodeWithText("1 file didn't upload", substring = true).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("With this message · 1").assertIsNotFocused()
    }

    @Test
    fun aTapDuringAToolPauseCollapsesThePanelAndOpensTheList() {
        val pause = ToolPauseHost(skills)

        composeRule.onNodeWithContentDescription("2 items with this message").performClick()
        composeRule.waitForIdle()
        assertThat(pause.state.isOpen).isTrue()
        assertThat(pause.collapses).isEqualTo(1)
        composeRule.onNodeWithText("With this message · 2").assertExists()

        // Closing it leaves the panel collapsed; it asks nothing more of the panel.
        composeRule.onNodeWithContentDescription("2 items with this message")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertThat(pause.state.isOpen).isFalse()
        assertThat(pause.collapses).isEqualTo(1)
        composeRule.onNodeWithText("With this message · 2").assertDoesNotExist()
    }

    @Test
    fun expandingTheApprovalPanelAgainClosesTheList() {
        val pause = ToolPauseHost(skills)
        composeRule.onNodeWithContentDescription("2 items with this message").performClick()
        composeRule.waitForIdle()
        assertThat(pause.state.isOpen).isTrue()

        pause.collapsed = false
        composeRule.waitForIdle()

        assertThat(pause.state.isOpen).isFalse()
        // And collapsing it again doesn't bring the list back on its own.
        pause.collapsed = true
        composeRule.waitForIdle()
        composeRule.onNodeWithText("With this message · 2").assertDoesNotExist()
    }

    @Test
    fun aFailedFileIsRetriedOrRemovedFromTheListDuringAToolPause() {
        val pause = ToolPauseHost(failedUpload)

        composeRule.onNodeWithText("1 file didn't upload", substring = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("With this message · 1").assertExists()

        composeRule.onNodeWithText("report.pdf").performClick()
        composeRule.onNodeWithContentDescription("Remove report.pdf").performClick()
        composeRule.waitForIdle()

        assertThat(pause.retried.map { it.name }).containsExactly("report.pdf")
        assertThat(pause.removed).hasSize(1)
    }
}
