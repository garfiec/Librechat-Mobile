package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.ToolCallType
import com.garfiec.librechat.core.model.content.AgentToolCall
import com.garfiec.librechat.core.model.content.ImageUrlContent
import com.garfiec.librechat.core.model.content.MessageContentPart
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/** Mirrors web `getMessageParts` / `getMessageClipboardSource` (useCopyToClipboard.ts, v0.8.9). */
class MessageClipboardTest {

    private val backgroundToolWakeup =
        "2 background tool tasks have finished. Continue using their durable results below.\n" +
            """[{"background_task_id":"bg-1","tool_call_id":"call-1","tool":"web_search","status":"completed","result":"ok"},""" +
            """{"background_task_id":"bg-2","tool_call_id":"call-2","tool":"execute_code","status":"error","result":"boom"}]"""

    private fun message(parts: List<MessageContentPart>?, text: String = "") = Message(
        messageId = "m1",
        conversationId = "c1",
        text = text,
        content = parts,
    )

    private fun text(value: String) = MessageContentPart(type = ContentType.TEXT, text = value)

    @Test
    fun aMessageWithoutPartsCopiesItsText() {
        assertEquals("plain body", serializeMessageForClipboard(message(null, text = "plain body")))
        assertEquals("plain body", serializeMessageForClipboard(message(emptyList(), text = "plain body")))
    }

    @Test
    fun textPartsCopyUnlabeled() {
        val out = serializeMessageForClipboard(message(listOf(text("one"), text("two"))))
        assertEquals("one\ntwo", out)
    }

    @Test
    fun reasoningIsNotCopied() {
        val out = serializeMessageForClipboard(
            message(
                listOf(
                    MessageContentPart(type = ContentType.THINK, think = "<think>chain of thought</think>"),
                    text("answer"),
                ),
            ),
        )
        assertEquals("answer", out)
    }

    @Test
    fun toolCallsAreNotCopied() {
        val out = serializeMessageForClipboard(
            message(
                listOf(
                    MessageContentPart(
                        type = ContentType.TOOL_CALL,
                        toolCall = AgentToolCall(id = "t1", name = "search", output = "done"),
                    ),
                    text("answer"),
                ),
            ),
        )
        assertEquals("answer", out)
    }

    @Test
    fun nonTextPartsAreNotCopied() {
        val out = serializeMessageForClipboard(
            message(
                listOf(
                    MessageContentPart(
                        type = ContentType.TOOL_CALL,
                        toolCall = AgentToolCall(type = ToolCallType.CODE_INTERPRETER, id = "t1"),
                    ),
                    MessageContentPart(
                        type = ContentType.IMAGE_URL,
                        imageUrl = ImageUrlContent(url = "https://x/img.png"),
                    ),
                    MessageContentPart(type = ContentType.STEER, steer = JsonPrimitive("go left")),
                    MessageContentPart(type = ContentType.ACTIVITY_LABEL, activityLabel = "Searched the docs"),
                    MessageContentPart(type = ContentType.SUMMARY, text = "earlier turns"),
                    MessageContentPart(type = ContentType.AGENT_UPDATE),
                    text("answer"),
                ),
            ),
        )
        assertEquals("answer", out)
    }

    @Test
    fun aWakeUpCopiesItsTaskResultsNotThePrompt() {
        val wakeup = message(null, text = backgroundToolWakeup).copy(isCreatedByUser = true)
        assertEquals("ok\n\nboom", serializeMessageForClipboard(wakeup))
    }

    @Test
    fun aUserSubmittedWakeUpLookalikeCopiesItsText() {
        val typed = message(null, text = backgroundToolWakeup).copy(isCreatedByUser = true, isUserSubmitted = true)
        assertEquals(backgroundToolWakeup, serializeMessageForClipboard(typed))
    }

    @Test
    fun blankAndReservationPartsAreSkipped() {
        val out = serializeMessageForClipboard(
            message(
                listOf(
                    text("  "),
                    MessageContentPart(type = ContentType.ACTIVITY_LABEL, activityLabel = "", pending = true),
                    MessageContentPart(type = ContentType.THINK, think = "  "),
                    text("kept"),
                ),
            ),
        )
        assertEquals("kept", out)
    }

    @Test
    fun errorPartsCopyTheirErrorText() {
        val out = serializeMessageForClipboard(
            message(listOf(MessageContentPart(type = ContentType.ERROR, error = "boom"))),
        )
        assertEquals("boom", out)
    }
}
