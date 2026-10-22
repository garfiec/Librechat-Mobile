package com.garfiec.librechat.feature.chat.components

import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.MemoryArtifactData
import com.garfiec.librechat.feature.chat.viewmodel.ActiveToolCall
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * How a `set_memory` / `delete_memory` call is recognised and when it reads as a failure. Ports of
 * upstream `Part.tsx`'s exact-name routing and `MemoryCall.tsx`'s `isMemoryFailureOutput`.
 *
 * The regexes are top-level vals, so this also fails if either pattern stops compiling on the JVM.
 * It does not exercise Android's ICU engine; the patterns are kept to plain alternation and `\b`,
 * which ICU accepts.
 */
class MemoryToolCallTest {

    private fun memoryAttachment(
        toolCallId: String? = "c1",
        key: String? = "tone",
        value: String? = "dry",
        type: String? = "update",
    ) = Attachment(
        type = "memory",
        toolCallId = toolCallId,
        memory = MemoryArtifactData(key = key, value = value, type = type),
    )

    // --- routing ---

    @Test
    fun `the two memory tools route by their exact names`() {
        assertThat(isMemoryToolCall("set_memory")).isTrue()
        assertThat(isMemoryToolCall("delete_memory")).isTrue()
    }

    @Test
    fun `an MCP tool with memory in its name is not a memory write`() {
        assertThat(isMemoryToolCall("memory_search_mcp_notes")).isFalse()
        assertThat(isMemoryToolCall("create_memory_mcp_server")).isFalse()
        assertThat(isMemoryToolCall("memory")).isFalse()
    }

    @Test
    fun `the match is case-sensitive on the tool's own name`() {
        assertThat(isMemoryToolCall("Set_Memory")).isFalse()
        assertThat(isMemoryToolCall("DELETE_MEMORY")).isFalse()
        assertThat(isMemoryToolCall(null)).isFalse()
    }

    // --- failure rule ---

    @Test
    fun `empty or blank output is not a failure`() {
        assertThat(isMemoryFailureOutput("set_memory", null)).isFalse()
        assertThat(isMemoryFailureOutput("set_memory", "")).isFalse()
        assertThat(isMemoryFailureOutput("delete_memory", "   \n")).isFalse()
    }

    @Test
    fun `the server's set success sentences are not failures`() {
        assertThat(isMemoryFailureOutput("set_memory", "Memory set for key \"tone\" (12 tokens)")).isFalse()
        assertThat(isMemoryFailureOutput("set_memory", "Memory saved")).isFalse()
        assertThat(isMemoryFailureOutput("set_memory", "  memory SAVED.  ")).isFalse()
    }

    @Test
    fun `set refusals are failures`() {
        assertThat(isMemoryFailureOutput("set_memory", "Failed to set memory for key \"tone\"")).isTrue()
        assertThat(isMemoryFailureOutput("set_memory", "Invalid key \"x\". Must be one of: a, b")).isTrue()
        assertThat(isMemoryFailureOutput("set_memory", "Memory storage exceeded. Cannot save new memories.")).isTrue()
        // `\b`: a word that merely starts with "saved" is not the success sentence.
        assertThat(isMemoryFailureOutput("set_memory", "Memory savedx")).isTrue()
    }

    @Test
    fun `the server's delete success sentences are not failures`() {
        assertThat(isMemoryFailureOutput("delete_memory", "Memory deleted for key \"tone\"")).isFalse()
        assertThat(isMemoryFailureOutput("delete_memory", "Memory deleted.")).isFalse()
    }

    @Test
    fun `delete refusals are failures`() {
        assertThat(isMemoryFailureOutput("delete_memory", "Failed to delete memory for key \"tone\"")).isTrue()
        // Web's pattern wants " for key" or "." straight after "deleted"; a bare one is a refusal.
        assertThat(isMemoryFailureOutput("delete_memory", "Memory deleted")).isTrue()
        assertThat(isMemoryFailureOutput("delete_memory", "Memory deletedx")).isTrue()
    }

    @Test
    fun `a delete's success sentence does not pass for a set`() {
        assertThat(isMemoryFailureOutput("set_memory", "Memory deleted for key \"tone\"")).isTrue()
        assertThat(isMemoryFailureOutput("delete_memory", "Memory set for key \"tone\" (3 tokens)")).isTrue()
    }

    // --- the cards a call draws ---

    @Test
    fun `a successful write draws its payload`() {
        val cards = memoryToolArtifacts(
            "set_memory",
            "Memory set for key \"tone\" (3 tokens)",
            listOf(memoryAttachment()),
            "c1",
        )

        assertThat(cards).hasSize(1)
        assertThat(cards.single().kind).isEqualTo(MemoryChangeKind.UPDATE)
        assertThat(cards.single().content).isEqualTo("dry")
    }

    @Test
    fun `a prose refusal with no payload draws an error card with the server's sentence`() {
        val cards = memoryToolArtifacts("set_memory", "  Failed to set memory for key \"tone\"  ", emptyList(), "c1")

        assertThat(cards).hasSize(1)
        assertThat(cards.single().kind).isEqualTo(MemoryChangeKind.ERROR)
        assertThat(cards.single().content).isEqualTo("Failed to set memory for key \"tone\"")
    }

    @Test
    fun `a still-streaming call with no output draws nothing yet`() {
        assertThat(memoryToolArtifacts("set_memory", "", emptyList(), "c1")).isEmpty()
        assertThat(memoryToolArtifacts("delete_memory", null, emptyList(), "c1")).isEmpty()
    }

    @Test
    fun `an error artifact fails the call even beside success prose`() {
        val blob = """{"errorType":"would_exceed","tokenCount":42}"""
        val cards = memoryToolArtifacts(
            "set_memory",
            "Memory set for key \"tone\" (3 tokens)",
            listOf(memoryAttachment(value = blob, type = "error")),
            "c1",
        )

        assertThat(cards.single().kind).isEqualTo(MemoryChangeKind.ERROR)
        assertThat(cards.single().error?.tokenCount).isEqualTo(42)
    }

    @Test
    fun `a refusal with a known error artifact draws the artifact, not the sentence twice`() {
        val blob = """{"errorType":"already_exceeded","tokenCount":10}"""
        val cards = memoryToolArtifacts(
            "set_memory",
            "Memory storage exceeded. Cannot save new memories.",
            listOf(memoryAttachment(value = blob, type = "error")),
            "c1",
        )

        assertThat(cards).hasSize(1)
        assertThat(cards.single().error?.errorType).isEqualTo("already_exceeded")
        assertThat(cards.single().content).isNull()
    }

    @Test
    fun `an error artifact with an unreadable blob borrows the server's sentence`() {
        val cards = memoryToolArtifacts(
            "set_memory",
            "Memory storage would exceed limit. Cannot save this memory.",
            listOf(memoryAttachment(value = "not json", type = "error")),
            "c1",
        )

        assertThat(cards.single().kind).isEqualTo(MemoryChangeKind.ERROR)
        assertThat(cards.single().content).isEqualTo("Memory storage would exceed limit. Cannot save this memory.")
    }

    // --- live stream ---

    /** The tool answers a bad key with prose and the step still closes as completed. */
    @Test
    fun `a live refusal draws an error card before the message finalizes`() {
        val call = ActiveToolCall(id = "c1", name = "set_memory", isComplete = true, output = "Invalid key \"Tone!\"")

        val artifacts = streamingMemoryArtifacts(call, emptyList())

        assertThat(artifacts.map { it.kind }).containsExactly(MemoryChangeKind.ERROR)
        assertThat(artifacts.single().content).isEqualTo("Invalid key \"Tone!\"")
    }

    @Test
    fun `a live write with nothing to judge yet keeps the running card`() {
        val running = ActiveToolCall(id = "c1", name = "set_memory")

        assertThat(streamingMemoryArtifacts(running, emptyList())).isEmpty()
    }

    @Test
    fun `a live write draws its memory attachment as soon as it arrives`() {
        val running = ActiveToolCall(id = "c1", name = "set_memory")

        val artifacts = streamingMemoryArtifacts(running, listOf(memoryAttachment()))

        assertThat(artifacts.map { it.kind }).containsExactly(MemoryChangeKind.UPDATE)
    }

    @Test
    fun `a live call of another tool is not drawn as a memory card`() {
        val mcp = ActiveToolCall(id = "c1", name = "memory_search_mcp_notes", isComplete = true, output = "nope")

        assertThat(streamingMemoryArtifacts(mcp, listOf(memoryAttachment()))).isEmpty()
    }
}
