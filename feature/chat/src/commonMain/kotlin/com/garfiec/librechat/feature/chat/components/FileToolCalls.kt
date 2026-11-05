package com.garfiec.librechat.feature.chat.components

import com.garfiec.librechat.core.model.content.AgentToolCall
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The file tools the server's code environment and skills expose (`read_file`, `create_file`,
 * `edit_file`), routed by EXACT name as upstream `Part.tsx` routes them: a substring match would
 * claim an MCP tool such as `read_file_mcp_drive`.
 */
internal enum class FileToolKind(val toolName: String) {
    READ("read_file"),
    CREATE("create_file"),
    EDIT("edit_file"),
    ;

    companion object {
        fun of(toolName: String?): FileToolKind? = entries.firstOrNull { it.toolName == toolName }
    }
}

/** One row of a diff: upstream `DiffLine`. A `HUNK` with empty text is a divider between edits. */
internal data class DiffLine(
    val type: Type,
    val text: String,
    val oldLine: Int? = null,
    val newLine: Int? = null,
) {
    enum class Type { ADD, DEL, CONTEXT, HUNK }

    /** One number per row: the old line for a removal, the new one otherwise (upstream `lineNumber`). */
    val number: Int? get() = if (type == Type.DEL) oldLine else newLine ?: oldLine
}

internal data class ParsedDiff(
    val lines: List<DiffLine>,
    val additions: Int,
    val deletions: Int,
    val hasLineNumbers: Boolean,
) {
    /** Rows to draw: hunk rows before the first content row are dropped, as `DiffView` does. */
    val visibleLines: List<DiffLine>
        get() {
            val first = lines.indexOfFirst { it.type != DiffLine.Type.HUNK }
            return if (first == -1) emptyList() else lines.filterIndexed { i, line -> i >= first || line.type != DiffLine.Type.HUNK }
        }
}

/** One `edit_file` replacement from the call's arguments (upstream `TextEditPreview`). */
internal data class TextEditPreview(val oldText: String, val newText: String)

/**
 * What a file tool's card shows: upstream `ReadFileCall` / `FileAuthoringCall` reduced to data, so
 * the card only draws it.
 */
internal data class FileToolView(
    val kind: FileToolKind,
    val filePath: String,
    /** The path's last segment, or the path itself; null when the call names no file. */
    val fileName: String?,
    /** A highlighter language from the path, null for plain text. */
    val language: String?,
    /** `create_file` overwrote an existing file: the host's summary opens with `Updated `. */
    val overwrote: Boolean,
    /** The diff to draw: the output's own when it carries one, else an edit's arguments. */
    val diff: ParsedDiff?,
    /** The text pane when there is no diff, and what Copy copies either way. */
    val preview: String,
    /** Highlight [preview] as [language]: only a create's authored contents are code. */
    val previewIsCode: Boolean,
    /** The tool's own output, shown under the preview when it says something the preview does not. */
    val separateOutput: String?,
    val intent: String?,
)

internal fun fileToolView(kind: FileToolKind, args: String?, output: String?, intent: String?): FileToolView {
    val out = output.orEmpty()
    val filePath = parseJsonField(args, "path").ifEmpty { parseJsonField(args, "file_path") }
    val fileName = filePath.substringAfterLast('/').ifEmpty { filePath }.ifEmpty { null }
    val language = langFromPath(filePath).takeIf { it != PLAINTEXT }
    if (kind == FileToolKind.READ) {
        return FileToolView(
            kind = kind,
            filePath = filePath,
            fileName = fileName,
            language = language,
            overwrote = false,
            diff = null,
            preview = out,
            previewIsCode = true,
            separateOutput = null,
            intent = intent,
        )
    }
    val isCreate = kind == FileToolKind.CREATE
    val overwrote = isCreate && out.startsWith("Updated ")
    val authored = if (isCreate) parseJsonField(args, "content") else ""
    val outputIsDiff = hasDiff(out)
    val edits = if (isCreate) emptyList() else buildEditArgsPreview(args)
    // A diff in the output supersedes the args preview: it carries the input with real file
    // context. Only the output is ever parsed as diff text.
    val diff = when {
        outputIsDiff -> parseUnifiedDiff(out)
        edits.isNotEmpty() -> buildEditPreviewDiff(edits)
        else -> null
    }
    val preview = when {
        outputIsDiff -> out
        isCreate -> authored.ifEmpty { out }
        diff != null -> diffPreviewText(diff)
        else -> out
    }
    return FileToolView(
        kind = kind,
        filePath = filePath,
        fileName = fileName,
        language = language,
        overwrote = overwrote,
        diff = diff,
        preview = preview,
        previewIsCode = isCreate && authored.isNotEmpty() && preview == authored,
        separateOutput = out.takeIf { it.isNotEmpty() && preview != it },
        intent = intent,
    )
}

/** The persisted call's arguments as text: `args` (an object or a JSON string), else `function.arguments`. */
internal fun AgentToolCall.argsText(): String? {
    val fromArgs = when (val value = args) {
        null, JsonNull -> null
        is JsonPrimitive -> value.content
        else -> value.toString()
    }
    return fromArgs?.takeIf { it.isNotBlank() } ?: function?.arguments ?: fromArgs
}

/** Upstream `langFromPath` (`ReadFileCall.tsx`): by file name first, then by extension. */
internal fun langFromPath(filePath: String): String {
    val name = filePath.substringAfterLast('/').lowercase()
    FILENAME_LANGUAGES[name]?.let { return it }
    val ext = if ('.' in name) name.substringAfterLast('.') else ""
    return EXTENSION_LANGUAGES[ext] ?: PLAINTEXT
}

private const val PLAINTEXT = "plaintext"

private val EXTENSION_LANGUAGES = mapOf(
    "py" to "python",
    "js" to "javascript",
    "ts" to "typescript",
    "tsx" to "typescript",
    "jsx" to "javascript",
    "rs" to "rust",
    "go" to "go",
    "rb" to "ruby",
    "java" to "java",
    "kt" to "kotlin",
    "swift" to "swift",
    "cs" to "csharp",
    "php" to "php",
    "lua" to "lua",
    "r" to "r",
    "sh" to "bash",
    "bash" to "bash",
    "zsh" to "bash",
    "json" to "json",
    "yaml" to "yaml",
    "yml" to "yaml",
    "toml" to "toml",
    "md" to "markdown",
    "sql" to "sql",
    "css" to "css",
    "scss" to "scss",
    "less" to "less",
    "html" to "html",
    "xml" to "xml",
    "c" to "c",
    "cpp" to "cpp",
    "h" to "c",
    "hpp" to "cpp",
)

private val FILENAME_LANGUAGES = mapOf(
    "makefile" to "makefile",
    "dockerfile" to "dockerfile",
)

/** Upstream `hasDiff` (`FileAuthoringCall.tsx`): a hunk header or a file header after a line break. */
internal fun hasDiff(output: String): Boolean =
    DIFF_HUNK_AFTER_BREAK.containsMatchIn(output) || "\n--- " in output || "\n+++ " in output

private val DIFF_HUNK_AFTER_BREAK = Regex("""\n@@\s""")
private val HUNK_HEADER = Regex("""^@@ -([0-9]+)(?:,[0-9]+)? \+([0-9]+)(?:,[0-9]+)? @@""")

/**
 * Upstream `parseUnifiedDiff` (`DiffView.tsx`): REAL unified-diff text (a tool's own output) into
 * typed rows, numbered when the hunk headers carry numbers. File headers before the first hunk and
 * `\ No newline` markers are dropped. Knows nothing of the args preview, which is built from
 * structured edits by [buildEditPreviewDiff] and never round-trips through diff text.
 */
internal fun parseUnifiedDiff(diff: String): ParsedDiff {
    val lines = mutableListOf<DiffLine>()
    var additions = 0
    var deletions = 0
    var hasLineNumbers = false
    var oldLine: Int? = null
    var newLine: Int? = null
    var inHunk = false
    for (raw in diff.removeSuffix("\n").split('\n')) {
        val hunk = HUNK_HEADER.find(raw)
        when {
            // A file header names the file the card already names; inside a hunk it is a change.
            !inHunk && (raw.startsWith("--- ") || raw.startsWith("+++ ")) -> Unit
            raw.startsWith("\\") -> Unit
            hunk != null -> {
                inHunk = true
                oldLine = hunk.groupValues[1].toIntOrNull()
                newLine = hunk.groupValues[2].toIntOrNull()
                hasLineNumbers = true
                lines += DiffLine(DiffLine.Type.HUNK, raw)
            }
            raw == "@@" -> {
                inHunk = true
                oldLine = null
                newLine = null
                lines += DiffLine(DiffLine.Type.HUNK, "")
            }
            raw.startsWith("+") -> {
                additions++
                lines += DiffLine(DiffLine.Type.ADD, raw.substring(1), newLine = newLine)
                newLine = newLine?.plus(1)
            }
            raw.startsWith("-") -> {
                deletions++
                lines += DiffLine(DiffLine.Type.DEL, raw.substring(1), oldLine = oldLine)
                oldLine = oldLine?.plus(1)
            }
            else -> {
                lines += DiffLine(DiffLine.Type.CONTEXT, raw.removePrefix(" "), oldLine = oldLine, newLine = newLine)
                oldLine = oldLine?.plus(1)
                newLine = newLine?.plus(1)
            }
        }
    }
    return ParsedDiff(lines, additions, deletions, hasLineNumbers)
}

/**
 * Upstream `buildEditPreviewDiff`: an `edit_file` call's replacements as removals then additions,
 * one empty hunk row between edits, no line numbers (the arguments carry text, not positions). An
 * empty side contributes no row, and a trailing newline ends the last line rather than adding one.
 */
internal fun buildEditPreviewDiff(edits: List<TextEditPreview>): ParsedDiff {
    val lines = mutableListOf<DiffLine>()
    var additions = 0
    var deletions = 0
    edits.forEachIndexed { index, edit ->
        if (index > 0) lines += DiffLine(DiffLine.Type.HUNK, "")
        for (text in previewRows(edit.oldText)) {
            deletions++
            lines += DiffLine(DiffLine.Type.DEL, text)
        }
        for (text in previewRows(edit.newText)) {
            additions++
            lines += DiffLine(DiffLine.Type.ADD, text)
        }
    }
    return ParsedDiff(lines, additions, deletions, hasLineNumbers = false)
}

private fun previewRows(text: String): List<String> =
    if (text.isEmpty()) emptyList() else text.removeSuffix("\n").split('\n')

/** Upstream `diffPreviewText`: the copy text. One-way, never parsed back. */
internal fun diffPreviewText(parsed: ParsedDiff): String = parsed.lines.joinToString("\n") { line ->
    when (line.type) {
        DiffLine.Type.ADD -> "+${line.text}"
        DiffLine.Type.DEL -> "-${line.text}"
        DiffLine.Type.CONTEXT -> line.text
        DiffLine.Type.HUNK -> ""
    }
}

/**
 * Upstream `buildEditArgsPreview`: `edits[]` (each entry, or the array itself, possibly a JSON
 * string), else a single top-level `old_text`/`new_text`, else (partially streamed args) the two
 * fields' occurrences paired in document order.
 */
internal fun buildEditArgsPreview(args: String?): List<TextEditPreview> {
    val parsed = parseArgsObject(args)
    val edits = coerceJsonValue(parsed?.get("edits"))
    if (edits is JsonArray && edits.isNotEmpty()) {
        return edits.mapNotNull { edit ->
            val entry = coerceJsonValue(edit) as? JsonObject ?: return@mapNotNull null
            val oldText = entry.textValue("old_text")
            val newText = entry.textValue("new_text")
            if (oldText.isEmpty() && newText.isEmpty()) null else TextEditPreview(oldText, newText)
        }
    }
    if (parsed != null) {
        val oldText = parsed.textValue("old_text")
        val newText = parsed.textValue("new_text")
        return if (oldText.isEmpty() && newText.isEmpty()) emptyList() else listOf(TextEditPreview(oldText, newText))
    }
    val oldTexts = parseJsonFieldOccurrences(args, "old_text")
    val newTexts = parseJsonFieldOccurrences(args, "new_text")
    return (0 until maxOf(oldTexts.size, newTexts.size))
        .map { TextEditPreview(oldTexts.getOrElse(it) { "" }, newTexts.getOrElse(it) { "" }) }
        .filter { it.oldText.isNotEmpty() || it.newText.isNotEmpty() }
}

private fun JsonObject.textValue(key: String): String =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()

private fun coerceJsonValue(value: JsonElement?): JsonElement? {
    if (value !is JsonPrimitive || !value.isString) return value
    return parseJsonOrNull(value.content) ?: value
}

private fun parseArgsObject(args: String?): JsonObject? = args?.let(::parseJsonOrNull) as? JsonObject

@Suppress("SwallowedException", "TooGenericExceptionCaught") // untrusted tool args: unparsable is absent
private fun parseJsonOrNull(text: String): JsonElement? = try {
    toolCallJson.parseToJsonElement(text)
} catch (e: Exception) {
    null
}

/**
 * Upstream `parseJsonField` (`parseJsonField.ts`): a string field from the args, whole JSON first,
 * else (partial JSON while it streams) the field's value up to the end of the text. Non-string
 * values read as their JSON text, as `String(value)` does for numbers and booleans.
 */
internal fun parseJsonField(args: String?, field: String): String {
    val parsed = args?.ifEmpty { "{}" }?.let(::parseJsonOrNull) ?: if (args == null) JsonObject(emptyMap()) else null
    when (parsed) {
        is JsonObject -> return when (val value = parsed[field]) {
            null, JsonNull -> ""
            is JsonPrimitive -> value.content
            else -> value.toString()
        }
        // An array is an object to JavaScript, with no such field.
        is JsonArray -> return ""
        // A bare string, number or null is not an object, so upstream reads it as partial text.
        else -> Unit
    }
    val match = fieldRegex(field).find(args.orEmpty()) ?: return ""
    return unescapeJsonString(match.groupValues[1])
}

/** Upstream `parseJsonFieldOccurrences`: every occurrence of [field] in partial JSON, in order. */
internal fun parseJsonFieldOccurrences(args: String?, field: String): List<String> {
    if (args.isNullOrEmpty()) return emptyList()
    return fieldRegex(field).findAll(args).map { unescapeJsonString(it.groupValues[1]) }.toList()
}

/**
 * `"field":"value"`, tolerating a missing closing quote and a dangling escape at the end. `\z`
 * stands in for JavaScript's `$` (end of text); the JVM's `$` also matches before a final newline.
 */
private fun fieldRegex(field: String): Regex =
    // Only ever called with literal identifiers, so the field needs no escaping.
    Regex("\"" + field + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)(?:\"|\\\\?\\z)")

/**
 * Upstream `unescapeJsonString`: the JSON string escapes, with a stream-boundary incompletion (a
 * dangling `\`, a partial `\uXX`, a high surrogate whose low half may still be coming) held back
 * rather than shown, and an unknown escape kept literally.
 */
internal fun unescapeJsonString(value: String, streaming: Boolean = true): String {
    val out = StringBuilder(value.length)
    var i = 0
    while (i < value.length) {
        val step = unescapeStep(value, i, streaming, out)
        if (step < 0) break
        i = step
    }
    return out.toString()
}

/**
 * Appends what [value] holds at [i] to [out] and returns the next index, or -1 when the rest is a
 * stream-edge incompletion to hold back.
 */
private fun unescapeStep(value: String, i: Int, streaming: Boolean, out: StringBuilder): Int {
    val ch = value[i]
    if (ch != '\\') {
        out.append(ch)
        return i + 1
    }
    if (i + 1 >= value.length) return -1
    val next = value[i + 1]
    val afterEscape = i + 2
    if (next != 'u') {
        val simple = SIMPLE_ESCAPES[next]
        if (simple != null) out.append(simple) else out.append('\\').append(next)
        return afterEscape
    }
    val hex = value.substring(afterEscape, minOf(afterEscape + 4, value.length))
    val code = hex.takeIf { it.length == 4 && it.all { c -> c in HEX_DIGITS } }?.toInt(HEX_RADIX)
    return when {
        code == null && streaming && afterEscape + 4 > value.length -> -1
        code == null -> {
            out.append("\\u")
            afterEscape
        }
        streaming && code in HIGH_SURROGATES && PARTIAL_LOW_HALF.matches(value.substring(afterEscape + 4)) -> -1
        else -> {
            out.append(code.toChar())
            afterEscape + 4
        }
    }
}

private val SIMPLE_ESCAPES = mapOf(
    'n' to '\n', 't' to '\t', 'r' to '\r', 'b' to '\b', 'f' to '\u000c',
    '"' to '"', '\\' to '\\', '/' to '/',
)
private const val HEX_DIGITS = "0123456789abcdefABCDEF"
private const val HEX_RADIX = 16
private val HIGH_SURROGATES = 0xd800..0xdbff
private val PARTIAL_LOW_HALF = Regex("""(?:\\(?:u[0-9a-fA-F]{0,3})?)?""")
