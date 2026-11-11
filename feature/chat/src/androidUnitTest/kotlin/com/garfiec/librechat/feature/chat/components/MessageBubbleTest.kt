package com.garfiec.librechat.feature.chat.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.TextField
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.content.MessageContentPart
import com.garfiec.librechat.core.ui.components.clearFocusOnTap
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.chat.util.buildActiveMessagePath
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MessageBubbleTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aTapOnAMessageDismissesTheKeyboard() {
        focusComposerOver(THREAD)

        composeRule.onNodeWithText(LAST_TEXT).performClick()

        composeRule.onNodeWithTag(COMPOSER).assertIsNotFocused()
    }

    /** The thinking card's header toggles it, consuming the tap before the bubble row sees it. */
    @Test
    fun aTapOnAThinkingHeaderDismissesTheKeyboard() {
        focusComposerOver(THREAD_WITH_THINKING)

        composeRule.onNodeWithText("Thinking").performClick()

        composeRule.onNodeWithTag(COMPOSER).assertIsNotFocused()
    }

    /** Under the app root's `clearFocusOnTap`, as in the app, which never sees a tap the thread consumes. */
    private fun focusComposerOver(thread: List<Message>) {
        composeRule.setContent {
            CompositionLocalProvider(LocalParsedMarkdownCache provides remember { ParsedMarkdownCache() }) {
                LibreChatTheme {
                    Column(Modifier.fillMaxSize().clearFocusOnTap()) {
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            MessageList(
                                displayMessages = buildActiveMessagePath(thread),
                                isStreaming = false,
                                streamingContent = "",
                                onSiblingNavigation = { _, _ -> },
                                onEditMessage = {},
                                onRegenerateMessage = {},
                                onCopyMessage = {},
                                bottomContentPadding = 0.dp,
                                onEditTextChange = {},
                                onEditSave = {},
                                onEditRerun = {},
                                onEditCancel = {},
                            )
                        }
                        TextField(value = "", onValueChange = {}, modifier = Modifier.testTag(COMPOSER))
                    }
                }
            }
        }
        composeRule.onNodeWithTag(COMPOSER).performClick()
        composeRule.onNodeWithTag(COMPOSER).assertIsFocused()
    }

    private companion object {
        const val COMPOSER = "composer"
        const val LAST_TEXT = "The second turn of the conversation."

        /** Chained by parent: the list keys items by tree parent, so a flat thread repeats a key. */
        val THREAD = listOf(
            Message(
                messageId = "m1",
                conversationId = "c",
                text = "The first turn of the conversation.",
                isCreatedByUser = true,
                sender = "User",
            ),
            Message(
                messageId = "m2",
                conversationId = "c",
                parentMessageId = "m1",
                text = LAST_TEXT,
                isCreatedByUser = false,
                sender = "TestBot",
            ),
        )

        val THREAD_WITH_THINKING = THREAD.take(1) + THREAD[1].copy(
            content = listOf(
                MessageContentPart(type = ContentType.THINK, think = "Weighing the options."),
                MessageContentPart(type = ContentType.TEXT, text = LAST_TEXT),
            ),
        )
    }
}
