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
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.chat.util.draftItemsOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The attachment list takes focus at its header when a screen reader opened it, so the next swipe
 * reads the list rather than the field; a touch open leaves focus (and the keyboard) where it was.
 * Hosted the way `ChatScreen` wires the stack and the panel.
 */
@RunWith(AndroidJUnit4::class)
class ComposerDraftListFocusInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val items = draftItemsOf(files = emptyList(), skills = listOf("summarize", "translate"), quotes = emptyList(), serverUrl = "")

    private fun host() {
        composeRule.setContent {
            LibreChatTheme {
                var open by remember { mutableStateOf(false) }
                var focusHeader by remember { mutableStateOf(false) }
                Column {
                    if (open) {
                        ComposerDraftListPanel(items = items, onRemove = {}, onRetry = {}, focusHeader = focusHeader)
                    }
                    ComposerAttachmentStack(
                        items = items,
                        expanded = open,
                        compact = false,
                        onClick = {
                            focusHeader = false
                            open = !open
                        },
                        onScreenReaderClick = {
                            focusHeader = !open
                            open = !open
                        },
                    )
                }
            }
        }
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
}
