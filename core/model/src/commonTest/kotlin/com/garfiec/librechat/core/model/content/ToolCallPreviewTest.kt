package com.garfiec.librechat.core.model.content

import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.response.ToolCallPartResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Tool-call previews (v0.8.9-rc1): the markers must survive decoding before any load asks for them. */
class ToolCallPreviewTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val previewJson = """
        {"id":"call|1","stepId":"step_9","name":"read_file","args":{"path":"a.kts"},
         "output":"head\n…\ntail","outputTruncated":true,"outputLength":48210,
         "argsTruncated":true,"argsLength":900,"subagentContentOmitted":true,"subagentContentParts":3,
         "subagentAttachmentCalls":[{"id":"child-1","agentId":"agent_a","stepId":"s2"},{"id":"child-2"}],
         "previewRevision":"2026-10-05T10:00:00.000Z"}
    """.trimIndent()

    @Test
    fun markers_and_step_id_decode() {
        val call = json.decodeFromString<AgentToolCall>(previewJson)
        assertEquals("step_9", call.stepId)
        assertEquals(true, call.outputTruncated)
        assertEquals(48210, call.outputLength)
        assertEquals(true, call.argsTruncated)
        assertEquals(900, call.argsLength)
        assertEquals(true, call.subagentContentOmitted)
        assertEquals(3, call.subagentContentParts)
        assertEquals(
            listOf(SubagentAttachmentCall("child-1", "agent_a", "s2"), SubagentAttachmentCall("child-2")),
            call.subagentAttachmentCalls,
        )
        assertEquals("2026-10-05T10:00:00.000Z", call.previewRevision)
        assertTrue(call.isPreview)
    }

    @Test
    fun a_full_call_carries_no_markers() {
        val call = json.decodeFromString<AgentToolCall>("""{"id":"c1","name":"read_file","output":"x"}""")
        assertNull(call.stepId)
        assertNull(call.outputTruncated)
        assertNull(call.previewRevision)
        assertFalse(call.isPreview)
    }

    @Test
    fun each_flag_alone_makes_a_preview() {
        assertTrue(AgentToolCall(outputTruncated = true).isPreview)
        assertTrue(AgentToolCall(argsTruncated = true).isPreview)
        assertTrue(AgentToolCall(subagentContentOmitted = true).isPreview)
        assertFalse(AgentToolCall(outputLength = 10, previewRevision = "r").isPreview)
    }

    @Test
    fun with_full_takes_only_the_shortened_fields_and_clears_the_markers() {
        val preview = AgentToolCall(
            id = "c1",
            name = "read_file",
            args = JsonPrimitive("short args"),
            output = "head…tail",
            runStepDurationMs = 10,
            outputTruncated = true,
            outputLength = 99,
            previewRevision = "r1",
            subagentAttachmentCalls = listOf(SubagentAttachmentCall("child")),
        )
        val full = AgentToolCall(
            id = "c1",
            name = "stale-name",
            args = JsonPrimitive("full args"),
            output = "the whole output",
            runStepDurationMs = 1,
        )
        val merged = preview.withFull(full)
        assertEquals("the whole output", merged.output)
        // Args were not shortened, so the preview's copy stays.
        assertEquals(JsonPrimitive("short args"), merged.args)
        // Everything else stays as the cached copy has it.
        assertEquals("read_file", merged.name)
        assertEquals(10L, merged.runStepDurationMs)
        assertEquals(listOf(SubagentAttachmentCall("child")), merged.subagentAttachmentCalls)
        assertFalse(merged.isPreview)
        assertNull(merged.outputLength)
        assertNull(merged.previewRevision)
    }

    @Test
    fun with_full_restores_args_and_transcript_when_shortened() {
        val transcript = listOf(MessageContentPart(type = ContentType.TEXT, text = "child"))
        val preview = AgentToolCall(
            args = JsonPrimitive("{\"a\":"),
            argsTruncated = true,
            argsLength = 40,
            subagentContentOmitted = true,
            subagentContentParts = 1,
        )
        val fullArgs = JsonObject(mapOf("a" to JsonPrimitive("long")))
        val merged = preview.withFull(AgentToolCall(args = fullArgs, subagentContent = transcript))
        assertEquals(fullArgs, merged.args)
        assertEquals(transcript, merged.subagentContent)
        assertNull(merged.argsLength)
        assertNull(merged.subagentContentParts)
        assertFalse(merged.isPreview)
    }

    @Test
    fun with_full_keeps_the_preview_value_when_the_full_copy_lacks_it() {
        val preview = AgentToolCall(output = "head", outputTruncated = true)
        assertEquals("head", preview.withFull(AgentToolCall()).output)
    }

    @Test
    fun fingerprint_matches_upstream_fnv1a() {
        assertEquals("ztntfp", fingerprint(""))
        assertEquals("1r9wi7g", fingerprint("a"))
        assertEquals("m3bicr", fingerprint("hello"))
    }

    @Test
    fun preview_key_is_stable_and_moves_with_the_content() {
        val call = json.decodeFromString<AgentToolCall>(previewJson)
        assertEquals(call.previewKey(), call.copy().previewKey())
        assertTrue(call.previewKey().startsWith("2026-10-05T10:00:00.000Z:"))
        assertNotEquals(call.previewKey(), call.copy(output = "other").previewKey())
        assertNotEquals(call.previewKey(), call.copy(outputLength = 1).previewKey())
        assertNotEquals(call.previewKey(), call.copy(args = JsonPrimitive("x")).previewKey())
        // Same content under another message revision is another stored version.
        assertNotEquals(call.previewKey(), call.copy(previewRevision = "later").previewKey())
    }

    @Test
    fun full_lengths_prefer_the_preview_lengths() {
        val call = AgentToolCall(args = JsonPrimitive("abc"), output = "xy", argsLength = 500, outputLength = 900)
        assertEquals(500, call.fullArgsLength())
        assertEquals(900, call.fullOutputLength())
        val plain = AgentToolCall(args = JsonObject(mapOf("k" to JsonPrimitive("v"))), output = "xy")
        assertEquals("""{"k":"v"}""".length, plain.fullArgsLength())
        assertEquals(2, plain.fullOutputLength())
        assertEquals(0, AgentToolCall().fullArgsLength())
    }

    @Test
    fun part_response_decodes() {
        val response = json.decodeFromString<ToolCallPartResponse>(
            """{"conversationId":"c","messageId":"m","partIndex":2,
               "tool_call":{"id":"call|1","output":"full","subagent_content":[{"type":"text","text":"t"}]}}""",
        )
        assertEquals(2, response.partIndex)
        assertEquals("full", response.toolCall.output)
        assertEquals(1, response.toolCall.subagentContent?.size)
    }

    @Test
    fun messages_report_a_preview_at_any_depth() {
        val nested = MessageContentPart(
            type = ContentType.TOOL_CALL,
            toolCall = AgentToolCall(
                subagentContent = listOf(
                    MessageContentPart(type = ContentType.TOOL_CALL, toolCall = AgentToolCall(outputTruncated = true)),
                ),
            ),
        )
        assertTrue(listOf(Message(messageId = "m", conversationId = "c", content = listOf(nested))).containsToolCallPreview())
        val clean = nested.copy(toolCall = AgentToolCall())
        assertFalse(listOf(Message(messageId = "m", conversationId = "c", content = listOf(clean))).containsToolCallPreview())
    }

    @Test
    fun raw_export_json_reports_preview_markers() {
        val marked = json.parseToJsonElement(
            """{"conversation":{},"messages":[{"content":[{"type":"tool_call","tool_call":{"id":"a","argsTruncated":true}}]}]}""",
        )
        val clean = json.parseToJsonElement(
            """{"messages":[{"content":[{"type":"tool_call","tool_call":{"id":"a","outputTruncated":false}}]}],
               "outputTruncated":true}""",
        )
        assertTrue(marked.containsToolCallPreviewMarkers())
        assertFalse(clean.containsToolCallPreviewMarkers())
    }

    @Test
    fun export_tree_drops_only_unset_preview_fields_at_any_depth() {
        val tree = json.parseToJsonElement(
            """{"createdAt":null,"content":[{"type":"tool_call","tool_call":{"id":"a","output":"x",
               "outputTruncated":null,"previewRevision":null,"executor":null,"subagent_content":[
               {"type":"tool_call","tool_call":{"id":"b","argsLength":null,"argsTruncated":true}}]}}]}""",
        )
        val expected = json.parseToJsonElement(
            """{"createdAt":null,"content":[{"type":"tool_call","tool_call":{"id":"a","output":"x",
               "executor":null,"subagent_content":[
               {"type":"tool_call","tool_call":{"id":"b","argsTruncated":true}}]}}]}""",
        )
        assertEquals(expected, tree.withoutUnsetToolCallPreviewFields())
    }
}
