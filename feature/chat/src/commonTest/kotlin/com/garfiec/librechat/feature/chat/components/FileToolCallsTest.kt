package com.garfiec.librechat.feature.chat.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Upstream `ReadFileCall` / `FileAuthoringCall` / `DiffView` / `parseJsonField`, reduced to the
 * data the card draws.
 */
class FileToolCallsTest {

    // --- routing ---

    @Test
    fun only_the_exact_names_route() {
        assertEquals(FileToolKind.READ, FileToolKind.of("read_file"))
        assertEquals(FileToolKind.CREATE, FileToolKind.of("create_file"))
        assertEquals(FileToolKind.EDIT, FileToolKind.of("edit_file"))
        assertNull(FileToolKind.of("read_file_mcp_drive"))
        assertNull(FileToolKind.of("Read_File"))
        assertNull(FileToolKind.of(null))
    }

    // --- language ---

    @Test
    fun language_comes_from_the_name_then_the_extension() {
        assertEquals("python", langFromPath("src/app/main.py"))
        assertEquals("typescript", langFromPath("a.TSX"))
        assertEquals("makefile", langFromPath("build/Makefile"))
        assertEquals("dockerfile", langFromPath("Dockerfile"))
        assertEquals("plaintext", langFromPath("notes"))
        assertEquals("plaintext", langFromPath("archive.tar.zst"))
        assertEquals("bash", langFromPath("run.zsh"))
    }

    // --- read_file ---

    @Test
    fun a_read_shows_the_output_as_the_file_language() {
        val view = fileToolView(FileToolKind.READ, """{"path":"/workspace/src/x.kt"}""", "fun x() = 1", intent = null)
        assertEquals("x.kt", view.fileName)
        assertEquals("kotlin", view.language)
        assertEquals("fun x() = 1", view.preview)
        assertTrue(view.previewIsCode)
        assertNull(view.diff)
        assertNull(view.separateOutput)
    }

    @Test
    fun file_path_is_the_fallback_key_and_plain_text_has_no_language() {
        val view = fileToolView(FileToolKind.READ, """{"file_path":"README"}""", "", intent = null)
        assertEquals("README", view.fileName)
        assertNull(view.language)
    }

    @Test
    fun a_call_without_a_path_names_no_file() {
        assertNull(fileToolView(FileToolKind.READ, null, null, intent = null).fileName)
        assertNull(fileToolView(FileToolKind.READ, """{"path":""}""", null, intent = null).fileName)
    }

    // --- create_file ---

    @Test
    fun a_create_shows_the_authored_contents_and_the_summary_below() {
        val args = """{"path":"a/b.py","content":"print(1)\n"}"""
        val view = fileToolView(FileToolKind.CREATE, args, "Created a/b.py", intent = null)
        assertEquals("print(1)\n", view.preview)
        assertTrue(view.previewIsCode)
        assertFalse(view.overwrote)
        assertEquals("Created a/b.py", view.separateOutput)
    }

    @Test
    fun an_overwrite_reads_as_updated_and_its_diff_wins_over_the_contents() {
        val output = "Updated a.txt\n--- a/a.txt\n+++ b/a.txt\n@@ -1,1 +1,1 @@\n-old\n+new\n"
        val view = fileToolView(FileToolKind.CREATE, """{"path":"a.txt","content":"new\n"}""", output, intent = null)
        assertTrue(view.overwrote)
        val diff = assertNotNull(view.diff)
        assertEquals(1, diff.additions)
        assertEquals(1, diff.deletions)
        // The diff is the output, so the raw output is not repeated below it.
        assertEquals(output, view.preview)
        assertNull(view.separateOutput)
        assertFalse(view.previewIsCode)
    }

    @Test
    fun a_create_without_contents_falls_back_to_the_output() {
        val view = fileToolView(FileToolKind.CREATE, """{"path":"a.txt"}""", "Created a.txt", intent = null)
        assertEquals("Created a.txt", view.preview)
        assertFalse(view.previewIsCode)
        assertNull(view.separateOutput)
    }

    // --- edit_file ---

    @Test
    fun an_edit_previews_its_replacements_from_the_arguments() {
        val args = """{"path":"a.kt","edits":[{"old_text":"a\nb\n","new_text":"c\n"},{"old_text":"x","new_text":""}]}"""
        val view = fileToolView(FileToolKind.EDIT, args, "Edited a.kt", intent = null)
        val diff = assertNotNull(view.diff)
        assertEquals(
            listOf(
                DiffLine(DiffLine.Type.DEL, "a"),
                DiffLine(DiffLine.Type.DEL, "b"),
                DiffLine(DiffLine.Type.ADD, "c"),
                DiffLine(DiffLine.Type.HUNK, ""),
                DiffLine(DiffLine.Type.DEL, "x"),
            ),
            diff.lines,
        )
        // A pure deletion (`new_text: ""`) counts no addition.
        assertEquals(1, diff.additions)
        assertEquals(3, diff.deletions)
        assertFalse(diff.hasLineNumbers)
        assertEquals("-a\n-b\n+c\n\n-x", view.preview)
        assertEquals("Edited a.kt", view.separateOutput)
    }

    @Test
    fun a_single_replacement_and_string_encoded_edits_are_read() {
        assertEquals(
            listOf(TextEditPreview("a", "b")),
            buildEditArgsPreview("""{"path":"p","old_text":"a","new_text":"b"}"""),
        )
        assertEquals(
            listOf(TextEditPreview("a", "b")),
            buildEditArgsPreview("""{"edits":"[{\"old_text\":\"a\",\"new_text\":\"b\"}]"}"""),
        )
        assertEquals(
            listOf(TextEditPreview("a", "b")),
            buildEditArgsPreview("""{"edits":["{\"old_text\":\"a\",\"new_text\":\"b\"}", 5, {"old_text":""}]}"""),
        )
    }

    @Test
    fun partial_arguments_pair_the_fields_in_order() {
        val partial = """{"path":"a.kt","edits":[{"old_text":"one","new_text":"uno"},{"old_text":"two","new_te"""
        assertEquals(
            listOf(TextEditPreview("one", "uno"), TextEditPreview("two", "")),
            buildEditArgsPreview(partial),
        )
    }

    @Test
    fun an_edit_with_nothing_to_show_has_no_diff() {
        val view = fileToolView(FileToolKind.EDIT, """{"path":"a.kt"}""", "", intent = null)
        assertNull(view.diff)
        assertEquals("", view.preview)
    }

    // --- unified diff ---

    @Test
    fun a_unified_diff_is_numbered_from_its_hunk_header() {
        val diff = parseUnifiedDiff(
            "--- a/f\n+++ b/f\n@@ -10,3 +10,3 @@ fun\n ctx\n-old\n+new\n\\ No newline at end of file\n tail\n",
        )
        assertEquals(
            listOf(
                DiffLine(DiffLine.Type.HUNK, "@@ -10,3 +10,3 @@ fun"),
                DiffLine(DiffLine.Type.CONTEXT, "ctx", oldLine = 10, newLine = 10),
                DiffLine(DiffLine.Type.DEL, "old", oldLine = 11),
                DiffLine(DiffLine.Type.ADD, "new", newLine = 11),
                DiffLine(DiffLine.Type.CONTEXT, "tail", oldLine = 12, newLine = 12),
            ),
            diff.lines,
        )
        assertTrue(diff.hasLineNumbers)
        assertEquals(11, diff.lines[2].number)
        assertEquals(11, diff.lines[3].number)
    }

    @Test
    fun file_headers_inside_a_hunk_are_changes() {
        // Removing a line `-- x` gives `--- x`: inside a hunk it is a deletion, not a header.
        val diff = parseUnifiedDiff("@@ -1 +1 @@\n--- x\n+++ y\n")
        assertEquals(listOf(DiffLine.Type.HUNK, DiffLine.Type.DEL, DiffLine.Type.ADD), diff.lines.map { it.type })
        assertEquals("-- x", diff.lines[1].text)
    }

    @Test
    fun a_bare_hunk_marker_resets_numbering() {
        val diff = parseUnifiedDiff("@@ -1 +1 @@\n-a\n@@\n+b\n")
        assertEquals(DiffLine(DiffLine.Type.HUNK, ""), diff.lines[2])
        assertNull(diff.lines[3].newLine)
    }

    @Test
    fun leading_hunk_rows_are_not_drawn() {
        val diff = buildEditPreviewDiff(listOf(TextEditPreview("", ""), TextEditPreview("a", "")))
        assertEquals(DiffLine.Type.HUNK, diff.lines.first().type)
        assertEquals(listOf(DiffLine(DiffLine.Type.DEL, "a")), diff.visibleLines)
        assertTrue(parseUnifiedDiff("@@ -1 +1 @@\n").visibleLines.isEmpty())
    }

    @Test
    fun output_needs_a_header_after_a_line_break_to_be_a_diff() {
        assertTrue(hasDiff("Edited f\n@@ -1 +1 @@\n-a\n+b"))
        assertTrue(hasDiff("x\n--- a"))
        assertFalse(hasDiff("@@ -1 +1 @@ at the start"))
        assertFalse(hasDiff("Edited f"))
    }

    // --- parseJsonField ---

    @Test
    fun a_field_is_read_from_whole_json() {
        assertEquals("p", parseJsonField("""{"path":"p"}""", "path"))
        assertEquals("3", parseJsonField("""{"path":3}""", "path"))
        assertEquals("", parseJsonField("""{"path":null}""", "path"))
        assertEquals("", parseJsonField("[1]", "path"))
        assertEquals("", parseJsonField("", "path"))
        assertEquals("", parseJsonField(null, "path"))
    }

    @Test
    fun a_field_is_read_from_partial_json_with_escapes_decoded() {
        assertEquals("a/b c", parseJsonField("""{"path":"a\/b c""", "path"))
        assertEquals("line\nnext \"q\"", parseJsonField("""{"content":"line\nnext \"q\"","pa""", "content"))
        // A dangling escape at the stream's edge is held back, not shown.
        assertEquals("ab", parseJsonField("{\"content\":\"ab\\", "content"))
        assertEquals("ab\"", parseJsonField("{\"content\":\"ab\\\"", "content"))
    }

    @Test
    fun a_very_long_field_is_read_from_partial_and_complete_json() {
        // Far past the K/N recursion limit of any thread stack the tests or the app run on.
        val plain = "x".repeat(100_000)
        assertEquals(plain, parseJsonField("{\"path\":\"a.txt\",\"content\":\"$plain", "content"))
        val escaped = "\\n".repeat(50_000)
        assertEquals("\n".repeat(50_000), parseJsonField("{\"content\":\"$escaped", "content"))
        assertEquals(listOf(plain), parseJsonFieldOccurrences("{\"content\":\"$plain\"}", "content"))
    }

    @Test
    fun unicode_escapes_decode_and_partial_ones_wait() {
        assertEquals("café", unescapeJsonString("caf\\u00e9"))
        assertEquals("caf", unescapeJsonString("caf\\u00"))
        assertEquals("😀", unescapeJsonString("\\ud83d\\ude00"))
        // A high surrogate whose low half may still be streaming is held back...
        assertEquals("a", unescapeJsonString("a\\ud83d"))
        assertEquals("a", unescapeJsonString("a\\ud83d\\ud"))
        // ...but kept when the value is known to be settled.
        assertEquals("a\ud83d", unescapeJsonString("a\\ud83d", streaming = false))
        assertEquals("\\q\\uZZZZ", unescapeJsonString("\\q\\uZZZZ"))
    }
}
