package com.garfiec.librechat.core.model.code

import com.garfiec.librechat.core.model.Attachment
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * `POST /api/agents/tools/execute_code/call`: runs one fenced code block of a saved reply (web
 * `RunCode.tsx`, `useToolCallMutation`). [partIndex] is null for a reply with no content parts, as
 * web sends none there. The server reads the message by [messageId] (404 when it is not the
 * caller's or has expired) and takes the conversation from it; `lang`, `code` and `isTemporary`
 * are the tool's arguments.
 */
@Serializable
data class CodeRunRequest(
    val messageId: String,
    val conversationId: String,
    val blockIndex: Int,
    val lang: String,
    val code: String,
    val partIndex: Int? = null,
    val isTemporary: Boolean = false,
)

/** The run's answer: the tool's text output, and the files it produced (null entries failed). */
@Serializable
data class CodeRunResponse(
    val result: JsonElement? = null,
    val attachments: List<Attachment?>? = null,
)

/** One stored run, as `GET /api/agents/tools/calls?conversationId=` lists them (oldest first). */
@Serializable
data class ToolCallRecord(
    val toolId: String? = null,
    val messageId: String? = null,
    val partIndex: Int? = null,
    val blockIndex: Int? = null,
    val conversationId: String? = null,
    val result: JsonElement? = null,
    val attachments: List<Attachment?>? = null,
)

/** A run's output as a code block shows it. */
data class CodeRunResult(val output: String, val attachments: List<Attachment>)

/** The text of a tool result: a string as is, anything else (a content array) as its JSON. */
fun JsonElement?.toolResultText(): String = when (this) {
    null -> ""
    is JsonPrimitive -> if (isString) content else toString()
    else -> toString()
}

fun CodeRunResponse.toResult(): CodeRunResult =
    CodeRunResult(result.toolResultText(), attachments.orEmpty().filterNotNull())

fun ToolCallRecord.toResult(): CodeRunResult =
    CodeRunResult(result.toolResultText(), attachments.orEmpty().filterNotNull())

/**
 * Where a run belongs: web's `mapToolCalls` key, `${messageId}_${partIndex ?? 0}_${blockIndex ?? 0}_${toolId}`.
 * MIRRORED (`scripts/mirrors.json` `run-code-block`).
 */
fun codeRunKey(messageId: String, partIndex: Int?, blockIndex: Int?, toolId: String = EXECUTE_CODE_TOOL): String =
    "${messageId}_${partIndex ?: 0}_${blockIndex ?: 0}_$toolId"

/** The stored runs of execute_code, grouped by [codeRunKey] in the order the server listed them. */
fun List<ToolCallRecord>.codeRunsByKey(): Map<String, List<CodeRunResult>> =
    mapNotNull { record ->
        val messageId = record.messageId?.takeIf { record.toolId == EXECUTE_CODE_TOOL } ?: return@mapNotNull null
        codeRunKey(messageId, record.partIndex, record.blockIndex) to record.toResult()
    }.groupBy({ it.first }, { it.second })

/**
 * The language a fenced block runs as, or null when the code tool takes none of its spellings (no
 * Run button). Port of web's `normalizeLanguage` (`client/src/utils/languages.ts`): lower-cased and
 * trimmed, then looked up; the value is what the tool's `lang` takes. MIRRORED (`run-code-languages`).
 */
object CodeRunLanguages {
    private val ALIASES: Map<String, String> = mapOf(
        "c" to "c",
        "cpp" to "cpp",
        "c++" to "cpp",
        "cplusplus" to "cpp",
        "d" to "d",
        "f90" to "f90",
        "fortran" to "f90",
        "fortran90" to "f90",
        "go" to "go",
        "golang" to "go",
        "java" to "java",
        "js" to "js",
        "javascript" to "js",
        "node" to "js",
        "nodejs" to "js",
        "php" to "php",
        "py" to "py",
        "python" to "py",
        "python3" to "py",
        "rs" to "rs",
        "rust" to "rs",
        "ts" to "ts",
        "typescript" to "ts",
    )

    fun normalize(lang: String?): String? = lang?.trim()?.lowercase()?.let(ALIASES::get)
}

private const val EXECUTE_CODE_TOOL = "execute_code"
