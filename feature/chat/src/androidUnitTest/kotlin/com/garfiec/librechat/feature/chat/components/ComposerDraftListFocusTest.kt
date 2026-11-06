package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.remember
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
 * the way `ChatScreen` gates it. On Robolectric, so it runs with the unit tests in CI, which only
 * compiles the instrumented ones.
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

    private fun host(items: DraftItems = skills, blocked: Boolean = false): DraftListState {
        val state = DraftListState()
        composeRule.setContent {
            LibreChatTheme {
                val draftList = remember { state }
                val chrome = ComposerChrome(draftItems = items).withDraftList(draftList, blocked = blocked)
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
    fun theStackDoesNotOpenTheListWhileAPausePanelIsUp() {
        val state = host(blocked = true)
        composeRule.onNodeWithContentDescription("2 items with this message").performClick()
        composeRule.onNodeWithContentDescription("2 items with this message")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()

        assertThat(state.isOpen).isFalse()
        composeRule.onNodeWithText("With this message · 2").assertDoesNotExist()
    }
}
