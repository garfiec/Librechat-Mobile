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
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.content.AgentToolCall
import com.garfiec.librechat.core.model.content.MessageContentPart
import com.garfiec.librechat.core.ui.components.clearFocusOnTap
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.chat.util.buildActiveMessagePath
import com.google.common.truth.Truth.assertThat
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

    @Test
    fun aReplySavedWithAKeyErrorReadsAsTheKeyErrorWithAnAddKeyAction() {
        var opened: String? = null
        showThread(KEY_ERROR_THREAD, UserKeyErrors(OPENAI_USER_PROVIDED, emptyList()) { opened = it })

        composeRule.onNodeWithText("No key found for OpenAI. Please provide a key and try again.").assertExists()
        composeRule.onAllNodesWithText(KEY_ERROR_JSON).assertCountEquals(0)
        composeRule.onNodeWithText("Add key").performClick()
        assertThat(opened).isEqualTo("openAI")
    }

    /** The shared-chat viewer: someone else's key, nothing to add. */
    @Test
    fun aKeyErrorWithNoKeyHostReadsGenericallyWithNoAction() {
        showThread(KEY_ERROR_THREAD, userKeyErrors = null)

        composeRule.onNodeWithText("No key found. Please provide a key and try again.").assertExists()
        composeRule.onAllNodesWithText("Add key").assertCountEquals(0)
    }

    @Test
    fun aHandoffToAnUnlistedAgentReadsAgentNeverTheId() {
        showThread(HANDOFF_THREAD, userKeyErrors = null)

        composeRule.onAllNodesWithText(HANDOFF_TARGET, substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription(HANDOFF_TARGET, substring = true).assertCountEquals(0)
        composeRule.onNodeWithText("Agent").assertExists()
    }

    @Test
    fun aHandoffCardNamesTheTargetAgent() {
        showThread(HANDOFF_THREAD, userKeyErrors = null, agentNames = mapOf(HANDOFF_TARGET to "Booking Agent"))

        composeRule.onNodeWithText("Booking Agent").assertExists()
        composeRule.onNodeWithContentDescription("Agent handoff from unknown to Booking Agent").assertExists()
        composeRule.onAllNodesWithText(HANDOFF_TARGET, substring = true).assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription(HANDOFF_TARGET, substring = true).assertCountEquals(0)
    }

    private fun showThread(
        thread: List<Message>,
        userKeyErrors: UserKeyErrors?,
        agentNames: Map<String, String> = emptyMap(),
    ) {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalParsedMarkdownCache provides remember { ParsedMarkdownCache() },
                LocalUserKeyErrors provides userKeyErrors,
                LocalAgentNames provides agentNames,
            ) {
                LibreChatTheme {
                    MessageList(
                        displayMessages = buildActiveMessagePath(thread),
                        isStreaming = false,
                        streamingContent = { "" },
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
            }
        }
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
                                streamingContent = { "" },
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

        const val KEY_ERROR_JSON = """{"type":"no_user_key"}"""
        val OPENAI_USER_PROVIDED = mapOf("openAI" to EndpointConfig(userProvide = true))

        /** As the server saves a send its key check refused: `error`, no content, the payload as text. */
        val KEY_ERROR_THREAD = THREAD.take(1) + THREAD[1].copy(
            text = KEY_ERROR_JSON,
            error = true,
            endpoint = "openAI",
            model = "gpt-4o-mini",
        )

        const val HANDOFF_TARGET = "agent_B1"

        val HANDOFF_THREAD = THREAD.take(1) + THREAD[1].copy(
            content = listOf(
                MessageContentPart(
                    type = ContentType.TOOL_CALL,
                    toolCall = AgentToolCall(id = "call_1", name = "lc_transfer_to_$HANDOFF_TARGET"),
                ),
                MessageContentPart(type = ContentType.TEXT, text = LAST_TEXT),
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
