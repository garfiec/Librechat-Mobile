package com.garfiec.librechat.core.model.approval

import com.garfiec.librechat.core.model.ToolApprovalRequest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * What the tool-approval review shows for one paused call: upstream `buildApprovalPreview`
 * (`client/src/components/Chat/approval/preview.ts`), ported line for line.
 *
 * The server's own code tools ([ToolApprovalRequest.source] `librechat_code`) read as the action
 * they take: a shell command, a new file's contents, an edit's replacements. Everything else is
 * [Kind.GENERIC]. Every text is bounded and has its hidden controls spelled out as `\uXXXX`, so a
 * proposal cannot hide a line break in a file name or reorder what the user reads with a bidi
 * override.
 */
data class ApprovalPreview(
    val kind: Kind,
    val toolName: String,
    val description: String?,
    val target: String?,
    val body: String,
    val truncated: Boolean,
) {
    enum class Kind { COMMAND, CREATE, EDIT, GENERIC }
}

/** Builds the preview for [request] from the exact arguments the interrupt carries. */
fun buildApprovalPreview(request: ToolApprovalRequest, maxChars: Int = MAX_PREVIEW_CHARS): ApprovalPreview {
    val parsed = parseArguments(request.arguments)
    var kind = ApprovalPreview.Kind.GENERIC
    var target: String? = null
    var rawBody = stringifyArguments(request.arguments)
    val isCode = request.source == ToolApprovalRequest.SOURCE_LIBRECHAT_CODE

    if (isCode && request.name == BASH_TOOL && parsed != null && !parsed.containsKey(ENVIRONMENT_ACTION)) {
        kind = ApprovalPreview.Kind.COMMAND
        rawBody = stringField(parsed, "command")
    } else if (isCode && request.name == CREATE_FILE && parsed != null) {
        kind = ApprovalPreview.Kind.CREATE
        target = stringField(parsed, "path", "file_path").ifEmpty { null }
        rawBody = stringField(parsed, "content").ifEmpty { stringifyArguments(request.arguments) }
    } else if (isCode && request.name == EDIT_FILE && parsed != null) {
        kind = ApprovalPreview.Kind.EDIT
        target = stringField(parsed, "path", "file_path").ifEmpty { null }
        // `parsed.edits ?? parsed`: a JSON null falls back to the whole object, as `??` does.
        rawBody = stringifyArguments(parsed["edits"]?.takeIf { it !is JsonNull } ?: parsed)
    }

    val totalBudget = maxOf(0, maxChars)
    val toolName = boundApprovalLabel(request.name, minOf(MAX_TOOL_NAME_CHARS, totalBudget))
    var remaining = maxOf(0, totalBudget - toolName.label.length)
    val description = request.description?.let { boundApprovalLabel(it, minOf(MAX_DESCRIPTION_CHARS, remaining)) }
    remaining = maxOf(0, remaining - (description?.label?.length ?: 0))
    val boundedTarget = target?.let { boundApprovalLabel(it, minOf(MAX_TARGET_CHARS, remaining)) }
    remaining = maxOf(0, remaining - (boundedTarget?.label?.length ?: 0))
    val body = boundPreview(rawBody, remaining)
    return ApprovalPreview(
        kind = kind,
        toolName = toolName.label,
        description = description?.label,
        target = boundedTarget?.label,
        body = body.label,
        truncated = body.truncated || boundedTarget?.truncated == true ||
            description?.truncated == true || toolName.truncated,
    )
}

/**
 * Previews for a whole batch under one shared budget ([MAX_BATCH_PREVIEW_CHARS]), so a pause with
 * many large proposals cannot multiply the work: upstream `buildApprovalPreviews`.
 */
fun buildApprovalPreviews(requests: List<ToolApprovalRequest>): List<ApprovalPreview> {
    var remaining = MAX_BATCH_PREVIEW_CHARS
    return requests.map { request ->
        val preview = buildApprovalPreview(request, minOf(MAX_PREVIEW_CHARS, remaining))
        remaining = maxOf(
            0,
            remaining - preview.body.length - preview.toolName.length -
                (preview.description?.length ?: 0) - (preview.target?.length ?: 0),
        )
        preview
    }
}

/** A label bounded to [maxChars] after its controls (line breaks included) are spelled out. */
data class BoundedText(val label: String, val truncated: Boolean)

fun boundApprovalLabel(value: String, maxChars: Int): BoundedText {
    val revealed = reveal(value, ::isHiddenInLabel)
    return BoundedText(label = revealed.take(maxOf(0, maxChars)), truncated = revealed.length > maxChars)
}

/**
 * The body: first at most [MAX_PREVIEW_LINES] lines, then controls revealed, then the character
 * cap. Upstream's order, kept so the same proposal truncates at the same place: revealing grows the
 * text, so the cap can land inside a `\uXXXX`.
 */
private fun boundPreview(value: String, maxChars: Int): BoundedText {
    val lines = value.split('\n')
    val kept = if (lines.size > MAX_PREVIEW_LINES) lines.subList(0, MAX_PREVIEW_LINES) else lines
    var body = reveal(kept.joinToString("\n"), ::isHiddenInBody)
    val byChars = body.length > maxChars
    if (byChars) body = body.take(maxOf(0, maxChars))
    return BoundedText(label = body, truncated = kept.size < lines.size || byChars)
}

private inline fun reveal(value: String, hidden: (Char) -> Boolean): String {
    if (value.none(hidden)) return value
    val out = StringBuilder(value.length + REVEAL_SLACK)
    for (char in value) {
        if (hidden(char)) out.append("\\u").append(char.code.toString(HEX).padStart(4, '0')) else out.append(char)
    }
    return out.toString()
}

/**
 * Upstream `CONTROL_CHARACTERS`: C0 controls except tab, line feed and carriage return (a body
 * keeps its layout), DEL and C1, the Arabic letter mark, the LRM/RLM marks, the embeddings and
 * overrides U+202A–U+202E and the isolates and invisible operators U+2066–U+206F. Zero-width
 * spaces and joiners (U+200B–U+200D), the BOM (U+FEFF) and the soft hyphen are NOT in the set
 * upstream, so they are not here either.
 *
 * A [Char] range check rather than a regex: every range is in the BMP, a JavaScript `/g` class
 * without `u` matches UTF-16 units just as this does, and there is no ICU/JVM class to disagree.
 */
private fun isHiddenInBody(char: Char): Boolean = when (char.code) {
    in 0x00..0x08, 0x0b, 0x0c, in 0x0e..0x1f -> true
    else -> isHiddenEverywhere(char.code)
}

/** Upstream `TARGET_CONTROL_CHARACTERS`: the body's set plus tab, line feed and carriage return. */
private fun isHiddenInLabel(char: Char): Boolean = char.code in 0x00..0x1f || isHiddenEverywhere(char.code)

private fun isHiddenEverywhere(code: Int): Boolean =
    code in 0x7f..0x9f || code == 0x061c || code == 0x200e || code == 0x200f ||
        code in 0x202a..0x202e || code in 0x2066..0x206f

private fun parseArguments(args: JsonElement?): JsonObject? = when (args) {
    is JsonObject -> args
    is JsonPrimitive -> if (args.isString) parseObject(args.content) else null
    else -> null
}

@Suppress("SwallowedException", "TooGenericExceptionCaught") // untrusted args: anything unparsable is generic
private fun parseObject(text: String): JsonObject? = try {
    Json.parseToJsonElement(text) as? JsonObject
} catch (e: Exception) {
    null
}

/** The first of [keys] whose value is a JSON string, even an empty one (upstream `stringField`). */
private fun stringField(args: JsonObject, vararg keys: String): String {
    for (key in keys) {
        val value = args[key] as? JsonPrimitive ?: continue
        if (value.isString) return value.content
    }
    return ""
}

/** `JSON.stringify(args, null, 2)`, with a string passed through as it is. */
private fun stringifyArguments(args: JsonElement?): String = when (args) {
    null -> ""
    is JsonPrimitive -> if (args.isString) args.content else args.toString()
    is JsonObject, is JsonArray -> prettyJson.encodeToString(JsonElement.serializer(), args)
}

@OptIn(ExperimentalSerializationApi::class)
private val prettyJson = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
}

const val MAX_PREVIEW_CHARS = 16 * 1024
const val MAX_PREVIEW_LINES = 200
const val MAX_BATCH_PREVIEW_CHARS = 64 * 1024
private const val MAX_TARGET_CHARS = 1024
private const val MAX_DESCRIPTION_CHARS = 1024
private const val MAX_TOOL_NAME_CHARS = 256
private const val BASH_TOOL = "bash_tool"
private const val CREATE_FILE = "create_file"
private const val EDIT_FILE = "edit_file"
private const val ENVIRONMENT_ACTION = "environmentAction"
private const val HEX = 16
private const val REVEAL_SLACK = 16
