package com.garfiec.librechat.core.model.approval

import com.garfiec.librechat.core.model.ToolApprovalRequest
import com.garfiec.librechat.core.model.approval.ApprovalPreview.Kind
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Upstream `buildApprovalPreview` (`client/src/components/Chat/approval/preview.ts`), case by case. */
class ApprovalPreviewTest {

    private fun request(
        name: String,
        arguments: JsonElement?,
        source: String? = ToolApprovalRequest.SOURCE_LIBRECHAT_CODE,
        description: String? = null,
    ) = ToolApprovalRequest(name = name, arguments = arguments, toolCallId = "call_1", description = description, source = source)

    // --- kinds ---

    @Test
    fun bashToolFromTheServerIsACommand() {
        val preview = buildApprovalPreview(request("bash_tool", buildJsonObject { put("command", "ls -la") }))
        assertEquals(Kind.COMMAND, preview.kind)
        assertEquals("ls -la", preview.body)
        assertNull(preview.target)
        assertFalse(preview.truncated)
    }

    @Test
    fun argumentsAsAJsonStringAreParsed() {
        val preview = buildApprovalPreview(request("bash_tool", JsonPrimitive("""{"command":"echo hi"}""")))
        assertEquals(Kind.COMMAND, preview.kind)
        assertEquals("echo hi", preview.body)
    }

    @Test
    fun sameNameWithoutTheServerSourceIsGeneric() {
        // An MCP tool can be called bash_tool; only the server-authored source makes it a command.
        val args = buildJsonObject { put("command", "rm -rf /") }
        val preview = buildApprovalPreview(request("bash_tool", args, source = null))
        assertEquals(Kind.GENERIC, preview.kind)
        assertEquals("{\n  \"command\": \"rm -rf /\"\n}", preview.body)
        assertEquals("bash_tool", preview.toolName)
    }

    @Test
    fun environmentActionKeepsBashGenericEvenWhenNull() {
        val withAction = buildJsonObject { put("command", "x"); put("environmentAction", "restart") }
        assertEquals(Kind.GENERIC, buildApprovalPreview(request("bash_tool", withAction)).kind)
        // `parsed.environmentAction === undefined` is false for a JSON null as well.
        val withNull = buildJsonObject { put("command", "x"); put("environmentAction", JsonNull) }
        assertEquals(Kind.GENERIC, buildApprovalPreview(request("bash_tool", withNull)).kind)
    }

    @Test
    fun commandWithoutACommandStringHasAnEmptyBody() {
        val preview = buildApprovalPreview(request("bash_tool", buildJsonObject { put("command", 5) }))
        assertEquals(Kind.COMMAND, preview.kind)
        assertEquals("", preview.body)
    }

    @Test
    fun unparsableStringArgumentsAreGenericAndShownRaw() {
        val preview = buildApprovalPreview(request("bash_tool", JsonPrimitive("{not json")))
        assertEquals(Kind.GENERIC, preview.kind)
        assertEquals("{not json", preview.body)
    }

    @Test
    fun arrayArgumentsAreGeneric() {
        val preview = buildApprovalPreview(request("create_file", buildJsonArray { add(JsonPrimitive(1)) }))
        assertEquals(Kind.GENERIC, preview.kind)
        assertEquals("[\n  1\n]", preview.body)
    }

    @Test
    fun createFileShowsTargetAndContents() {
        val args = buildJsonObject { put("path", "src/main.py"); put("content", "print('hi')\n") }
        val preview = buildApprovalPreview(request("create_file", args))
        assertEquals(Kind.CREATE, preview.kind)
        assertEquals("src/main.py", preview.target)
        assertEquals("print('hi')\n", preview.body)
    }

    @Test
    fun createFileFallsBackToFilePathAndToTheWholeArguments() {
        val args = buildJsonObject { put("file_path", "a.txt") }
        val preview = buildApprovalPreview(request("create_file", args))
        assertEquals("a.txt", preview.target)
        assertEquals("{\n  \"file_path\": \"a.txt\"\n}", preview.body)
    }

    @Test
    fun anEmptyPathStringDoesNotFallThroughToFilePath() {
        // `stringField` returns the first STRING value, empty or not; `|| undefined` then drops it.
        val args = buildJsonObject { put("path", ""); put("file_path", "b.txt"); put("content", "x") }
        assertNull(buildApprovalPreview(request("create_file", args)).target)
    }

    @Test
    fun editFileShowsItsEdits() {
        val args = buildJsonObject {
            put("path", "a.kt")
            put("edits", buildJsonArray { add(buildJsonObject { put("old_text", "a"); put("new_text", "b") }) })
        }
        val preview = buildApprovalPreview(request("edit_file", args))
        assertEquals(Kind.EDIT, preview.kind)
        assertEquals("a.kt", preview.target)
        assertEquals("[\n  {\n    \"old_text\": \"a\",\n    \"new_text\": \"b\"\n  }\n]", preview.body)
    }

    @Test
    fun editFileWithoutEditsShowsTheWholeObject() {
        val args = buildJsonObject { put("path", "a.kt"); put("old_text", "a"); put("new_text", "b") }
        val preview = buildApprovalPreview(request("edit_file", args))
        assertEquals("{\n  \"path\": \"a.kt\",\n  \"old_text\": \"a\",\n  \"new_text\": \"b\"\n}", preview.body)
        val withNull = buildJsonObject { put("path", "a.kt"); put("edits", JsonNull) }
        assertTrue(buildApprovalPreview(request("edit_file", withNull)).body.startsWith("{"))
    }

    @Test
    fun stringEditsPassThroughRaw() {
        val args = buildJsonObject { put("path", "a.kt"); put("edits", "raw edits") }
        assertEquals("raw edits", buildApprovalPreview(request("edit_file", args)).body)
    }

    @Test
    fun nullArgumentsGiveAnEmptyGenericBody() {
        val preview = buildApprovalPreview(request("tool", null, source = null))
        assertEquals(Kind.GENERIC, preview.kind)
        assertEquals("", preview.body)
    }

    // --- hidden characters ---

    @Test
    fun bodyRevealsControlsButKeepsTabsAndLineBreaks() {
        val command = "echo a\tb\r\nnext\u0000\u0007\u000b\u000c\u001b\u007f\u0085\u009f"
        val preview = buildApprovalPreview(request("bash_tool", buildJsonObject { put("command", command) }))
        assertEquals("echo a\tb\r\nnext\\u0000\\u0007\\u000b\\u000c\\u001b\\u007f\\u0085\\u009f", preview.body)
    }

    @Test
    fun bodyRevealsBidiControlsAndInvisibleOperators() {
        val hidden = "؜‎‏‪‫‬‭‮⁦⁧⁨⁩⁪⁯"
        val preview = buildApprovalPreview(request("bash_tool", buildJsonObject { put("command", "x$hidden") }))
        assertEquals(
            "x\\u061c\\u200e\\u200f\\u202a\\u202b\\u202c\\u202d\\u202e\\u2066\\u2067\\u2068\\u2069\\u206a\\u206f",
            preview.body,
        )
    }

    @Test
    fun charactersUpstreamLeavesAloneAreNotRevealed() {
        // Zero-width space/joiners, the BOM, the soft hyphen, NBSP and the neighbours of each
        // revealed range are outside upstream's set.
        val kept = "​‌‍﻿­ `؛؝‍‐ ⁥⁰é中😀"
        val preview = buildApprovalPreview(request("bash_tool", buildJsonObject { put("command", kept) }))
        assertEquals(kept, preview.body)
    }

    @Test
    fun labelsRevealLineBreaksAndTabsToo() {
        val args = buildJsonObject { put("path", "evil\nname\t\r‮.txt"); put("content", "x") }
        val preview = buildApprovalPreview(request("create_file", args, description = "line\nbreak‏"))
        assertEquals("evil\\u000aname\\u0009\\u000d\\u202e.txt", preview.target)
        assertEquals("line\\u000abreak\\u200f", preview.description)
    }

    @Test
    fun toolNameIsRevealed() {
        val preview = buildApprovalPreview(request("tool‮name", JsonPrimitive(""), source = null))
        assertEquals("tool\\u202ename", preview.toolName)
    }

    @Test
    fun genericBodiesAreRevealedAfterStringifying() {
        // JSON already escapes C0 controls in an object; the bidi override survives stringify raw.
        val args = buildJsonObject { put("q", "a\u0001b‮c") }
        assertEquals("{\n  \"q\": \"a\\u0001b\\u202ec\"\n}", buildApprovalPreview(request("search", args, source = null)).body)
    }

    // --- bounds ---

    @Test
    fun bodyKeepsAtMostTwoHundredLines() {
        val command = (1..250).joinToString("\n") { "line $it" }
        val preview = buildApprovalPreview(request("bash_tool", buildJsonObject { put("command", command) }))
        assertEquals(MAX_PREVIEW_LINES, preview.body.split('\n').size)
        assertTrue(preview.body.endsWith("line 200"))
        assertTrue(preview.truncated)
    }

    @Test
    fun exactlyTwoHundredLinesIsNotTruncated() {
        val command = (1..200).joinToString("\n") { "l" }
        assertFalse(buildApprovalPreview(request("bash_tool", buildJsonObject { put("command", command) })).truncated)
    }

    @Test
    fun theCharacterCapCountsTheNameDescriptionAndTargetFirst() {
        val args = buildJsonObject { put("path", "p.txt"); put("content", "x".repeat(100)) }
        // 11 (create_file) + 4 (desc) + 5 (p.txt) = 20, leaving 30 of 50 for the body.
        val preview = buildApprovalPreview(request("create_file", args, description = "desc"), maxChars = 50)
        assertEquals(30, preview.body.length)
        assertTrue(preview.truncated)
    }

    @Test
    fun theCapAppliesAfterRevealingSoItCanSplitAnEscape() {
        val command = "ab‮"
        val args = buildJsonObject { put("command", command) }
        val preview = buildApprovalPreview(request("bash_tool", args), maxChars = 9 + 5)
        // 9 chars for the name leave 5: "ab" and the first three of the revealed escape.
        assertEquals("ab\\u2", preview.body)
        assertTrue(preview.truncated)
    }

    @Test
    fun aLongLabelIsCutAndMarksTheWholePreviewTruncated() {
        val longPath = "d/".repeat(600) + "f.txt"
        val args = buildJsonObject { put("path", longPath); put("content", "x") }
        val preview = buildApprovalPreview(request("create_file", args))
        assertEquals(1024, preview.target?.length)
        assertTrue(preview.truncated)
        assertEquals("x", preview.body)
    }

    @Test
    fun aRevealedLineBreakCountsTowardTheLabelCap() {
        val label = boundApprovalLabel("a\nb", 7)
        assertEquals("a\\u000a", label.label)
        assertTrue(label.truncated)
        assertFalse(boundApprovalLabel("a\nb", 8).truncated)
    }

    @Test
    fun aZeroBudgetLeavesEverythingEmptyAndTruncated() {
        val preview = buildApprovalPreview(request("bash_tool", buildJsonObject { put("command", "ls") }), maxChars = 0)
        assertEquals("", preview.toolName)
        assertEquals("", preview.body)
        assertTrue(preview.truncated)
    }

    @Test
    fun aBatchSharesOneBudget() {
        val big = "y".repeat(MAX_PREVIEW_CHARS)
        val requests = (1..6).map { request("bash_tool", buildJsonObject { put("command", big) }) }
        val previews = buildApprovalPreviews(requests)
        // Each call takes the 16K cap (9 of it for the name), so the 64K batch runs out on the fifth.
        assertEquals(MAX_PREVIEW_CHARS - 9, previews[0].body.length)
        assertEquals(MAX_PREVIEW_CHARS - 9, previews[3].body.length)
        assertEquals(0, previews[4].body.length)
        assertEquals("", previews[5].toolName)
        assertTrue(previews.all { it.truncated })
        assertEquals(MAX_BATCH_PREVIEW_CHARS, previews.sumOf { it.body.length + it.toolName.length })
    }
}
