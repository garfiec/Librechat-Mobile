package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.content.AgentToolCall
import com.garfiec.librechat.core.model.content.MessageContentPart
import com.garfiec.librechat.core.model.content.SubagentAttachmentCall
import com.garfiec.librechat.feature.chat.viewmodel.delegate.locate
import com.garfiec.librechat.feature.chat.viewmodel.delegate.seedsFor
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The pure halves of tool-call preview hydration: keys, the display overlay, seeding and file hints. */
class ToolPartHydrationTest {

    private val fullOutput = "x".repeat(2000)
    private val full = AgentToolCall(id = "call|1", stepId = "step-1", name = "read_file", output = fullOutput)
    private val preview = full.copy(
        output = "xxx\n…\nxxx",
        outputTruncated = true,
        outputLength = fullOutput.length,
        previewRevision = "rev-1",
    )

    private fun toolPart(call: AgentToolCall, agentId: String? = null) =
        MessageContentPart(type = ContentType.TOOL_CALL, toolCall = call, agentId = agentId)

    private fun message(id: String, vararg parts: MessageContentPart) =
        Message(messageId = id, conversationId = "c", content = parts.toList())

    private fun node(message: Message) = MessageNode(message = message, children = emptyList(), siblingIndex = 0, siblingCount = 1)

    @Test
    fun only_a_previewed_call_with_an_id_has_a_key() {
        assertNull(ToolPartKey.of(full, null))
        assertNull(ToolPartKey.of(preview.copy(id = ""), null))
        val key = ToolPartKey.of(preview, "agent_a")
        assertEquals(ToolPartIdentity("call|1", "step-1", "agent_a"), key?.identity)
        assertEquals(preview.previewKey(), key?.previewKey)
    }

    @Test
    fun empty_scope_counts_as_absent() {
        assertEquals(
            ToolPartIdentity("a", null, null),
            ToolPartIdentity.of(AgentToolCall(id = "a", stepId = ""), ""),
        )
    }

    @Test
    fun the_overlay_swaps_only_loaded_parts_and_keeps_everything_else() {
        val other = AgentToolCall(id = "other", output = "short", argsTruncated = true, args = JsonPrimitive("{"))
        val text = MessageContentPart(type = ContentType.TEXT, text = "Here's the file:")
        val nodes = listOf(node(message("m1", text, toolPart(preview), toolPart(other))))
        val key = requireNotNull(ToolPartKey.of(preview, null))

        val hydrated = nodes.withHydratedToolParts(mapOf(key to full))

        val parts = hydrated.single().message.content!!
        assertSame(text, parts[0])
        assertEquals(fullOutput, parts[1].toolCall?.output)
        assertFalse(parts[1].toolCall!!.isPreview)
        assertTrue(parts[2].toolCall!!.isPreview)
    }

    @Test
    fun the_overlay_returns_the_same_list_when_nothing_matches() {
        val nodes = listOf(node(message("m1", toolPart(preview))))
        val stale = ToolPartKey(ToolPartIdentity("call|1", "step-1", null), "older-revision")
        assertSame(nodes, nodes.withHydratedToolParts(mapOf(stale to full)))
        assertSame(nodes, nodes.withHydratedToolParts(emptyMap()))
    }

    @Test
    fun the_overlay_matches_the_agent_scope() {
        val nodes = listOf(node(message("m1", toolPart(preview, agentId = "agent_b"))))
        val keyForA = requireNotNull(ToolPartKey.of(preview, "agent_a"))
        assertSame(nodes, nodes.withHydratedToolParts(mapOf(keyForA to full)))
    }

    @Test
    fun locate_finds_the_message_and_raw_index() {
        val messages = listOf(
            message("m1", toolPart(full)),
            message("m2", MessageContentPart(type = ContentType.TEXT, text = "t"), toolPart(preview)),
        )
        assertEquals("m2" to 1, locate(messages, requireNotNull(ToolPartKey.of(preview, null)))?.let { it.message.messageId to it.index })
        val changed = requireNotNull(ToolPartKey.of(preview.copy(previewRevision = "rev-2"), null))
        assertNull(locate(messages, changed))
    }

    @Test
    fun a_reload_that_previews_a_call_held_in_full_seeds_it() {
        val before = listOf(message("m1", toolPart(full)))
        val after = listOf(message("m1", toolPart(preview)))
        val seeds = seedsFor(before, after, known = emptySet())
        assertEquals(mapOf(requireNotNull(ToolPartKey.of(preview, null)) to full), seeds)
    }

    @Test
    fun a_length_mismatch_is_a_real_change_and_is_not_seeded() {
        val before = listOf(message("m1", toolPart(full.copy(output = "different"))))
        val after = listOf(message("m1", toolPart(preview)))
        assertTrue(seedsFor(before, after, known = emptySet()).isEmpty())
    }

    @Test
    fun a_known_key_or_a_first_load_seeds_nothing() {
        val after = listOf(message("m1", toolPart(preview)))
        assertTrue(seedsFor(emptyList(), after, known = emptySet()).isEmpty())
        val known = setOf(requireNotNull(ToolPartKey.of(preview, null)))
        assertTrue(seedsFor(listOf(message("m1", toolPart(full))), after, known).isEmpty())
    }

    @Test
    fun full_copy_checks_every_shortened_field() {
        val transcript = listOf(MessageContentPart(type = ContentType.TEXT, text = "a"))
        val subagentPreview = AgentToolCall(id = "s", subagentContentOmitted = true, subagentContentParts = 1)
        assertTrue(isFullCopyOf(AgentToolCall(id = "s", subagentContent = transcript), subagentPreview))
        assertFalse(isFullCopyOf(AgentToolCall(id = "s", subagentContent = transcript + transcript), subagentPreview))
        assertFalse(isFullCopyOf(AgentToolCall(id = "s"), subagentPreview))
        val argsPreview = AgentToolCall(id = "a", args = JsonPrimitive("{\"k"), argsTruncated = true, argsLength = 9)
        assertTrue(isFullCopyOf(AgentToolCall(id = "a", args = JsonPrimitive("{\"k\":\"v\"}")), argsPreview))
        assertFalse(isFullCopyOf(AgentToolCall(id = "a", args = JsonPrimitive("{}")), argsPreview))
    }

    @Test
    fun a_changed_call_of_the_same_length_is_not_a_full_copy() {
        val output = "head-" + "x".repeat(2000) + "-tail"
        val stored = AgentToolCall(id = "c", output = output)
        val shown =
            AgentToolCall(id = "c", output = "head-xx\n…\nxx-tail", outputTruncated = true, outputLength = output.length)
        assertTrue(isFullCopyOf(stored, shown))
        assertFalse(isFullCopyOf(stored.copy(output = output.replace("head-", "HEAD-")), shown))
        assertFalse(isFullCopyOf(stored.copy(output = output.replace("-tail", "-TAIL")), shown))

        val args = "{\"path\":\"a.txt\",\"body\":\"" + "y".repeat(500) + "\"}"
        val argsShown = AgentToolCall(
            id = "c",
            args = JsonPrimitive("{\"path\":\"a.txt\",\"body\":\"yyy…\"}"),
            argsTruncated = true,
            argsLength = args.length,
        )
        assertTrue(isFullCopyOf(AgentToolCall(id = "c", args = JsonPrimitive(args)), argsShown))
        assertFalse(isFullCopyOf(AgentToolCall(id = "c", args = JsonPrimitive(args.replace("a.txt", "b.txt"))), argsShown))
    }

    @Test
    fun a_pretty_printed_full_matches_its_compact_preview() {
        val output = "{\n  \"result\": \"" + "z".repeat(800) + "\"\n}"
        val shown =
            AgentToolCall(id = "c", output = "{\"result\":\"zzz…\"}", outputTruncated = true, outputLength = output.length)
        assertTrue(isFullCopyOf(AgentToolCall(id = "c", output = output), shown))
    }

    @Test
    fun hydrated_chars_count_output_args_and_transcript() {
        assertEquals(fullOutput.length.toLong(), hydratedChars(full))
        assertTrue(hydratedChars(full.copy(args = JsonPrimitive("abc"))) == fullOutput.length + 3L)
    }

    @Test
    fun an_omitted_transcript_routes_its_childrens_files_by_the_hints() {
        val call = AgentToolCall(
            id = "sub",
            name = "subagent",
            subagentContentOmitted = true,
            subagentContentParts = 4,
            subagentAttachmentCalls = listOf(SubagentAttachmentCall("child", agentId = "agent_a", stepId = "s2")),
        )
        assertEquals(listOf("sub", "child"), outputToolCallIds(listOf(toolPart(call))))
        // Once the transcript is in place the walk yields the same id.
        val hydrated = call.copy(
            subagentContentOmitted = null,
            subagentContent = listOf(toolPart(AgentToolCall(id = "child"))),
        )
        assertEquals(listOf("sub", "child"), outputToolCallIds(listOf(toolPart(hydrated))))
        assertTrue(omittedTranscriptCalls(hydrated).isEmpty())
    }

    @Test
    fun a_hint_does_not_claim_a_file_whose_scope_disagrees() {
        val hints = listOf(SubagentAttachmentCall("child", agentId = "agent_a", stepId = "s2"))
        val mine = Attachment(toolCallId = "child", agentId = "agent_a", stepId = "s2", filename = "mine.md")
        val unscoped = Attachment(toolCallId = "child", filename = "unscoped.md")
        val otherRun = Attachment(toolCallId = "child", stepId = "s9", filename = "other.md")
        val unrelated = Attachment(toolCallId = "elsewhere", filename = "x.md")

        val kept = attachmentsOwnedByHints(listOf(mine, unscoped, otherRun, unrelated), hints, otherIds = listOf("sub"))

        assertEquals(listOf(mine, unscoped, unrelated), kept)
        // An id another walk claims is never dropped.
        assertEquals(listOf(otherRun), attachmentsOwnedByHints(listOf(otherRun), hints, otherIds = listOf("child")))
    }

    @Test
    fun an_attached_command_preview_cut_mid_line_matches_its_stored_output() {
        // The shape the server sends for a long attached-workspace command: the cut falls inside one
        // long stdout line and the marker sits on its own line, so the preview has more lines than
        // the output it stands for.
        val line = "LONGWORDXYZ-".repeat(80)
        val output = "[starting directory: \"workspace/\"]\nstdout:\n$line\nstderr:\nyes: stdout: Broken pipe\n\n[exit code: 0]"
        val shown = AgentToolCall(
            id = "toolu_1",
            stepId = "step_1",
            name = "bash_tool",
            output = output.take(254) + "\n\u2026\n" + output.takeLast(255),
            outputTruncated = true,
            outputLength = output.length,
            previewRevision = "1793840539025",
        )
        val stored = AgentToolCall(id = "toolu_1", stepId = "step_1", name = "bash_tool", output = output)

        assertTrue(isFullCopyOf(stored, shown))
        assertFalse(isFullCopyOf(stored.copy(output = output.replace("[exit code: 0]", "[exit code: 1]")), shown))
    }
}
