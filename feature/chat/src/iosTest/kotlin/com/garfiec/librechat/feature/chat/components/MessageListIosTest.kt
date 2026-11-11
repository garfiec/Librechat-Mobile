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
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.content.MessageContentPart
import com.garfiec.librechat.core.ui.components.clearFocusOnTap
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.chat.util.buildActiveMessagePath
import kotlin.test.Test

/** The thread's taps under iOS's own text-selection and gesture handling, which Android's tests don't run. */
@OptIn(ExperimentalTestApi::class)
class MessageListIosTest {

    @Test
    fun aTapOnMessageTextDismissesTheKeyboard() = runComposeUiTest {
        focusComposerOverThread()

        onNodeWithText(REPLY_TEXT, useUnmergedTree = true).performClick()

        onNodeWithTag(COMPOSER).assertIsNotFocused()
    }

    @Test
    fun aTapOnAThinkingHeaderDismissesTheKeyboard() = runComposeUiTest {
        focusComposerOverThread()

        onNodeWithText("Thinking").performClick()

        onNodeWithTag(COMPOSER).assertIsNotFocused()
    }

    private fun ComposeUiTest.focusComposerOverThread() {
        setContent {
            CompositionLocalProvider(LocalParsedMarkdownCache provides remember { ParsedMarkdownCache() }) {
                LibreChatTheme {
                    Column(Modifier.fillMaxSize().clearFocusOnTap()) {
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            MessageList(
                                displayMessages = buildActiveMessagePath(THREAD),
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
        onNodeWithTag(COMPOSER).performClick()
        onNodeWithTag(COMPOSER).assertIsFocused()
    }

    private companion object {
        const val COMPOSER = "composer"
        const val REPLY_TEXT = "The second turn of the conversation."

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
                text = REPLY_TEXT,
                isCreatedByUser = false,
                sender = "TestBot",
                content = listOf(
                    MessageContentPart(type = ContentType.THINK, think = "Weighing the options."),
                    MessageContentPart(type = ContentType.TEXT, text = REPLY_TEXT),
                ),
            ),
        )
    }
}
