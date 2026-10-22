package com.garfiec.librechat.feature.chat.components

import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.MemoryArtifactData
import com.garfiec.librechat.core.model.content.AgentToolCall
import com.garfiec.librechat.core.model.content.MessageContentPart
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MemoryArtifactsTest {

    private fun memoryAttachment(
        toolCallId: String?,
        key: String? = "favourite_colour",
        value: String? = "blue",
        type: String? = "update",
    ) = Attachment(
        type = "memory",
        toolCallId = toolCallId,
        memory = MemoryArtifactData(key = key, value = value, type = type),
    )

    // Explicit return type: the function is recursive, so it cannot be inferred.
    private fun toolCallPart(id: String, nested: List<String> = emptyList()): MessageContentPart =
        MessageContentPart(
            type = ContentType.TOOL_CALL,
            toolCall = AgentToolCall(
                id = id,
                subagentContent = nested.map { toolCallPart(it) }.ifEmpty { null },
            ),
        )

    @Test
    fun `takes the key as the title and the value as the body`() {
        val artifacts = collectMemoryArtifacts(listOf(memoryAttachment("c1")), "c1")

        assertThat(artifacts).hasSize(1)
        assertThat(artifacts.first().title).isEqualTo("favourite_colour")
        assertThat(artifacts.first().content).isEqualTo("blue")
        assertThat(artifacts.first().kind).isEqualTo(MemoryChangeKind.UPDATE)
    }

    @Test
    fun `a delete carries no body of its own`() {
        val artifacts = collectMemoryArtifacts(
            listOf(memoryAttachment("c1", value = null, type = "delete")),
            "c1",
        )

        assertThat(artifacts.first().kind).isEqualTo(MemoryChangeKind.DELETE)
        assertThat(artifacts.first().content).isNull()
    }

    @Test
    fun `parses a storage-limit failure into its type and token count`() {
        val blob = """{"errorType":"would_exceed","tokenCount":42,"totalTokens":9000}"""
        val artifacts = collectMemoryArtifacts(
            listOf(memoryAttachment("c1", key = "system", value = blob, type = "error")),
            "c1",
        )

        assertThat(artifacts.first().kind).isEqualTo(MemoryChangeKind.ERROR)
        assertThat(artifacts.first().error?.errorType).isEqualTo(MEMORY_ERROR_WOULD_EXCEED)
        assertThat(artifacts.first().error?.tokenCount).isEqualTo(42)
        // The blob itself must never reach the card as body text.
        assertThat(artifacts.first().content).isNull()
    }

    @Test
    fun `an unparseable failure blob degrades to the generic error, not raw JSON`() {
        val artifacts = collectMemoryArtifacts(
            listOf(memoryAttachment("c1", value = "storage is full", type = "error")),
            "c1",
        )

        assertThat(artifacts.first().kind).isEqualTo(MemoryChangeKind.ERROR)
        assertThat(artifacts.first().error).isNull()
    }

    @Test
    fun `excludes writes belonging to a different tool call`() {
        val attachments = listOf(memoryAttachment("other"), memoryAttachment("c1", key = "mine"))

        assertThat(collectMemoryArtifacts(attachments, "c1").map { it.title }).containsExactly("mine")
    }

    @Test
    fun `a write with no tool call id is not drawn on a call's card`() {
        val attachments = listOf(memoryAttachment(null, key = "background"), memoryAttachment("c1", key = "mine"))

        assertThat(collectMemoryArtifacts(attachments, "c1").map { it.title }).containsExactly("mine")
    }

    @Test
    fun `a call with no id owns no writes`() {
        val attachments = listOf(memoryAttachment(null), memoryAttachment("c1"))

        assertThat(collectMemoryArtifacts(attachments, null)).isEmpty()
        assertThat(collectMemoryArtifacts(attachments, "")).isEmpty()
    }

    @Test
    fun `a write with no tool call id is drawn once, on the saved-memory line`() {
        val attachments = listOf(memoryAttachment(null, key = "background"))
        val calls = listOf("c1", "c2")

        assertThat(calls.flatMap { collectMemoryArtifacts(attachments, it) }).isEmpty()
        assertThat(collectUnrenderedMemoryArtifacts(attachments, calls).map { it.title }).containsExactly("background")
    }

    @Test
    fun `orphan collection keeps only the writes no tool call rendered`() {
        val attachments = listOf(
            memoryAttachment("inline-call", key = "inline"),
            memoryAttachment("background-subrun", key = "background"),
        )

        val orphans = collectUnrenderedMemoryArtifacts(attachments, setOf("inline-call"))

        assertThat(orphans.map { it.title }).containsExactly("background")
    }

    @Test
    fun `orphan collection keeps a write with no tool call id at all`() {
        val orphans = collectUnrenderedMemoryArtifacts(listOf(memoryAttachment(null)), setOf("c1"))

        assertThat(orphans).hasSize(1)
    }

    @Test
    fun `rendered ids include tool calls nested inside a subagent trace`() {
        val parts = listOf(
            toolCallPart("outer", nested = listOf("inner")),
            MessageContentPart(type = ContentType.TEXT, text = "hello"),
        )

        assertThat(renderedToolCallIds(parts)).containsExactly("outer", "inner")
    }

    @Test
    fun `a memory write inside a subagent trace is not reported twice`() {
        val parts = listOf(toolCallPart("subagent", nested = listOf("set-memory")))
        val attachments = listOf(memoryAttachment("set-memory"))

        assertThat(collectUnrenderedMemoryArtifacts(attachments, renderedToolCallIds(parts))).isEmpty()
    }

    @Test
    fun `output parsing still yields a card when no payload was sent`() {
        val artifact = parseMemoryArtifact("Memory set for key \"tone\" (12 tokens)")

        assertThat(artifact).isNotNull()
        assertThat(artifact?.title).isNull()
        assertThat(artifact?.content).isEqualTo("Memory set for key \"tone\" (12 tokens)")
        assertThat(artifact?.kind).isEqualTo(MemoryChangeKind.UPDATE)
    }

    @Test
    fun `the live line and the saved message name the same open state, however many writes arrived`() {
        val first = memoryAttachment(toolCallId = null).copy(messageId = "reply-1")
        val second = memoryAttachment(toolCallId = null, key = "tone").copy(messageId = "reply-1")

        val liveEarly = memoryArtifactsStateKey(listOf(first), fallback = "")
        val liveLater = memoryArtifactsStateKey(listOf(first, second), fallback = "")
        val saved = memoryArtifactsStateKey(listOf(first, second), fallback = "reply-1")

        assertThat(liveEarly).isEqualTo("reply-1")
        assertThat(liveLater).isEqualTo(liveEarly)
        assertThat(saved).isEqualTo(liveEarly)
    }

    @Test
    fun `the line's state key ignores other attachments and falls back without a stamped id`() {
        val image = Attachment(type = "image", messageId = "other")
        val unstamped = memoryAttachment(toolCallId = null)

        assertThat(memoryArtifactsStateKey(listOf(image, unstamped), fallback = "msg-9")).isEqualTo("msg-9")
        assertThat(memoryArtifactsStateKey(listOf(image, unstamped), fallback = "")).isEmpty()
    }

    @Test
    fun `each card a call draws has its own state key, and a call without an id carries nothing`() {
        assertThat(memoryCardStateKey("call_1", 0)).isEqualTo(memoryCardStateKey("call_1", 0))
        assertThat(memoryCardStateKey("call_1", 0)).isNotEqualTo(memoryCardStateKey("call_1", 1))
        assertThat(memoryCardStateKey("call_1", 0)).isNotEqualTo(memoryCardStateKey("call_2", 0))
        assertThat(memoryCardStateKey(null, 0)).isEmpty()
        assertThat(memoryCardStateKey("", 0)).isEmpty()
    }
}
