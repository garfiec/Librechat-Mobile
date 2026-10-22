package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.content.AgentToolCall
import com.garfiec.librechat.core.model.content.MessageContentPart
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.chat.util.MessageNode
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Skills in the message list, through the real render path: a `skill` tool call routes to the
 * skill card (and only an exact `skill` does), the user turn shows its pills, and a streaming turn
 * that picked skills shows one "Running X" row per pick until its final message arrives.
 */
@RunWith(AndroidJUnit4::class)
class SkillCallInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val userTurn = Message(
        messageId = "u1",
        conversationId = CONVO,
        text = "summarize",
        isCreatedByUser = true,
        manualSkills = listOf("pdf-tools"),
        alwaysAppliedSkills = listOf("house-style"),
    )

    private fun toolCall(id: String, name: String, skillName: String) = MessageContentPart(
        type = ContentType.TOOL_CALL,
        toolCall = AgentToolCall(
            id = id,
            name = name,
            args = JsonPrimitive("""{"skillName":"$skillName"}"""),
            output = "Skill \"$skillName\" loaded.",
        ),
    )

    private fun show(nodes: List<Message>, isStreaming: Boolean) {
        composeRule.setContent {
            val markdownCache = remember { ParsedMarkdownCache() }
            CompositionLocalProvider(LocalParsedMarkdownCache provides markdownCache) {
                LibreChatTheme {
                    MessageList(
                        displayMessages = nodes.map { MessageNode(it, emptyList(), 0, 1) },
                        isStreaming = isStreaming,
                        streamingContent = "",
                        onSiblingNavigation = { _, _ -> },
                        onEditMessage = {},
                        onRegenerateMessage = {},
                        onCopyMessage = {},
                        userName = "Ada",
                    )
                }
            }
        }
    }

    private fun isShowing(text: String): Boolean =
        composeRule.onAllNodes(hasText(text, substring = true), useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()

    private fun awaitText(text: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) { isShowing(text) }
    }

    @Test
    fun aFinalReplysLeadingSkillPartsRenderAsSkillCards() {
        val reply = Message(
            messageId = "a1",
            conversationId = CONVO,
            parentMessageId = "u1",
            text = "Here is the summary.",
            content = listOf(
                toolCall("call_manual_skill_a1_0", "skill", "pdf-tools"),
                MessageContentPart(type = ContentType.TEXT, text = "Here is the summary."),
            ),
        )
        show(listOf(userTurn, reply), isStreaming = false)
        awaitText("Here is the summary.")
        assertTrue(isShowing("Ran pdf-tools"))
        // The user turn's pills: the always-applied one and the picked one.
        assertTrue(isShowing("house-style"))
        assertFalse(isShowing("Running pdf-tools"))
    }

    @Test
    fun onlyTheExactToolNameIsASkillCall() {
        val reply = Message(
            messageId = "a1",
            conversationId = CONVO,
            parentMessageId = "u1",
            text = "done",
            content = listOf(toolCall("t1", "find_skill", "pdf-tools")),
        )
        show(listOf(userTurn, reply), isStreaming = false)
        awaitText("find_skill")
        assertFalse(isShowing("Ran pdf-tools"))
    }

    @Test
    fun aStreamingTurnShowsARunningRowPerPick() {
        show(listOf(userTurn), isStreaming = true)
        awaitText("Running pdf-tools")
        // Always-applied skills make no card, so no stand-in either.
        assertFalse(isShowing("Running house-style"))
    }

    private companion object {
        const val CONVO = "c1"
    }
}
