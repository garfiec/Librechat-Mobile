package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.content.MessageContentPart
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StoppedReplyTest {

    private val turn = Message(messageId = "u1", conversationId = "c1", isCreatedByUser = true)
    private val stopped = Message(messageId = "a1", conversationId = "c1", parentMessageId = "u1", unfinished = true)

    private fun path(vararg messages: Message) = messages.map { MessageNode(it, emptyList(), 0, 1) }

    private fun id(
        reply: Message,
        before: Message = turn,
        streaming: Boolean = false,
        pending: Boolean = false,
        comparing: Boolean = false,
    ) = stoppedReplyId(path(before, reply), streaming, pending, comparing)

    private val parallel = listOf(
        MessageContentPart(type = ContentType.TEXT, text = "a", agentId = "openAI__gpt"),
        MessageContentPart(type = ContentType.TEXT, text = "b", agentId = "openAI__gpt____1"),
    )

    @Test
    fun aStoppedReplyCanBeContinued() {
        assertEquals("a1", id(stopped))
    }

    @Test
    fun aFinishReasonOtherThanStopCounts() {
        val cut = stopped.copy(unfinished = false, finishReason = "length")
        assertEquals("a1", id(cut))
        assertNull(id(cut.copy(finishReason = "stop")))
        // The tool-call-limit notice offers its own ways on.
        assertNull(id(cut.copy(finishReason = "tool_call_limit")))
        assertNull(id(stopped.copy(unfinished = false)))
    }

    @Test
    fun nothingWhileARunIsLiveOrWaitsOnTheUser() {
        assertNull(id(stopped, streaming = true))
        // A paused run (a tool approval, a question) is also saved unfinished.
        assertNull(id(stopped, pending = true))
    }

    @Test
    fun onlyAReplyToTheUserTurnAboveIt() {
        assertNull(id(stopped.copy(error = true)))
        assertNull(id(stopped.copy(isCreatedByUser = true)))
        // Continue replays the turn above: a chained reply has none.
        assertNull(id(stopped, before = Message(messageId = "u1", conversationId = "c1")))
        assertNull(id(stopped.copy(parentMessageId = "other")))
        assertNull(stoppedReplyId(path(stopped), isStreaming = false, hasPendingAction = false, comparing = false))
        val summary = stopped.copy(content = listOf(MessageContentPart(type = ContentType.SUMMARY)))
        assertNull(id(summary))
    }

    @Test
    fun aComparisonReplyOnlyWhileComparing() {
        val compared = stopped.copy(content = parallel)
        // A single-model Continue on it would save the primary's answer alone.
        assertNull(id(compared))
        assertEquals("a1", id(compared, comparing = true))
        assertNull(id(stopped, comparing = true))
    }
}
