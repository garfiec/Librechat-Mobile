package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
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
 * the way `ChatScreen` gates it: shut during a tool-approval pause, and closed by a pause panel that
 * comes up ([DraftListPauseEffects]). On Robolectric, so it runs with the unit tests in CI, which
 * only compiles the instrumented ones.
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
                    FailedLine(chrome)
                }
            }
        }
        return state
    }

    /**
     * A screen as `ChatScreen` wires it: [toolPause] is a tool-approval panel docked above the
     * composer, which shuts the list ([withDraftList]) and closes it as it comes up
     * ([DraftListPauseEffects]); the list draws only while no pause panel is up.
     */
    private inner class PauseHost(items: DraftItems, initialToolPause: Boolean = false) {
        val state = DraftListState()
        var toolPause by mutableStateOf(initialToolPause)

        init {
            composeRule.setContent {
                LibreChatTheme {
                    val draftList = remember { state }
                    val paused = toolPause
                    DraftListPauseEffects(draftList, pausePanelUp = paused)
                    val chrome = ComposerChrome(draftItems = items).withDraftList(draftList, toolPause = paused)
                    Column {
                        if (chrome.isDraftListOpen && !paused) {
                            ComposerDraftListPanel(items = items, onRemove = {}, onRetry = {}, focusHeader = draftList.focusHeader)
                        }
                        composerFieldLeadingIcon(comparison = null, chrome = chrome, compact = false)!!()
                        FailedLine(chrome)
                    }
                }
            }
        }
    }

    @Test
    fun aPausePanelComingUpClosesTheListAndItStaysClosedAfter() {
        val pause = PauseHost(skills)
        composeRule.onNodeWithContentDescription("2 items with this message").performClick()
        composeRule.waitForIdle()
        assertThat(pause.state.isOpen).isTrue()

        pause.toolPause = true
        composeRule.waitForIdle()
        assertThat(pause.state.isOpen).isFalse()
        composeRule.onNodeWithText("With this message · 2").assertDoesNotExist()

        // Decided: the list doesn't pop back over the thread on its own.
        pause.toolPause = false
        composeRule.waitForIdle()
        assertThat(pause.state.isOpen).isFalse()
        composeRule.onNodeWithText("With this message · 2").assertDoesNotExist()
    }

    @Test
    fun duringAToolPauseTheStackShowsButHasNoOpenAction() {
        val pause = PauseHost(skills, initialToolPause = true)
        val stack = composeRule.onNodeWithContentDescription("2 items with this message")

        // Still there, with its count, but neither a touch nor a screen reader can open the list.
        stack.assertExists()
        stack.assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        stack.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Role))
        stack.performClick()
        composeRule.waitForIdle()

        assertThat(pause.state.isOpen).isFalse()
        composeRule.onNodeWithText("With this message · 2").assertDoesNotExist()
    }

    @Test
    fun duringAToolPauseTheFailedLineSaysAfterTheApprovalAndOpensNothing() {
        val pause = PauseHost(failedUpload, initialToolPause = true)
        val line = composeRule.onNodeWithText("1 file didn't upload. You can retry or remove it after the approval.")

        line.assertExists()
        line.assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
        line.performClick()
        composeRule.waitForIdle()

        assertThat(pause.state.isOpen).isFalse()
        composeRule.onNodeWithText("With this message · 1").assertDoesNotExist()

        // The approval decided: the line goes back to pointing at the list, which opens again.
        pause.toolPause = false
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Tap it in the list to retry", substring = true).performClick()
        composeRule.waitForIdle()
        assertThat(pause.state.isOpen).isTrue()
        composeRule.onNodeWithText("With this message · 1").assertExists()
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

    /** The line under the field, fed from the chrome as `CommonChatInputCore` feeds it. */
    @Composable
    private fun FailedLine(chrome: ComposerChrome) = FailedUploadsLine(
        failedCount = chrome.draftItems.failedCount,
        listOpen = chrome.isDraftListOpen,
        listShut = chrome.draftListShut,
        onOpen = chrome.onToggleDraftList,
        onOpenFromScreenReader = chrome.onToggleDraftListFromScreenReader,
    )
}
