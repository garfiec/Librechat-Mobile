package com.garfiec.librechat.core.model.wakeup

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** What settled a wake-up turn: a detached subagent, or one or more background tool tasks. */
enum class WakeupKind { SUBAGENT, BACKGROUND_TOOL }

enum class WakeupTaskStatus(val wire: String) {
    COMPLETED("completed"),
    ERROR("error"),
    CANCELLED("cancelled"),
    ;

    companion object {
        fun fromWire(value: String?): WakeupTaskStatus? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * One settled task a wake-up turn reports. [threadId]/[subagentType] are set for a subagent,
 * [toolCallId]/[toolName] for a background tool.
 */
data class WakeupTask(
    val taskId: String,
    val status: WakeupTaskStatus,
    val result: String,
    val threadId: String? = null,
    val subagentType: String? = null,
    val toolCallId: String? = null,
    val toolName: String? = null,
)

data class WakeupDisplay(
    val kind: WakeupKind,
    val tasks: List<WakeupTask>,
)

/**
 * Recognizes a host-authored wake-up turn: the server resumes a parent run when a detached
 * subagent or background tool task settles, and it does so by saving a USER message whose text is
 * a fixed header line followed by a JSON payload line. Rendered as-is, that is a user bubble full
 * of model-facing prompt JSON the user never typed.
 *
 * MIRRORED from upstream `parseWakeupText` (`client/src/components/Chat/Messages/Content/Parts/wakeup.ts`),
 * whose two headers in turn mirror the server's `renderWakeupInput`
 * (`packages/api/src/agents/subagentCompletionWakeup.ts`) and `buildWakeupInput`
 * (`packages/api/src/agents/backgroundCompletionWakeup.ts`). Registered in `scripts/mirrors.json`:
 * a reworded header makes every wake-up render as a user bubble again, with nothing failing.
 *
 * The strictness is the point, as upstream's is: the header must start the text, the payload must
 * be the very next line, and every field must be present with the right type. A user message that
 * merely quotes one of these prompts must never collapse into a system row.
 */
object WakeupMessage {

    @Suppress("ReturnCount") // debt: 10 returns
    fun parse(text: String?): WakeupDisplay? {
        if (text.isNullOrEmpty() || text.length > MAX_WAKEUP_TEXT_CHARS) return null
        // Both headers are one fixed line ending in a newline, so the first line alone decides —
        // the same answer upstream's anchored `exec` over the whole text gives. Matching only that
        // line keeps this cheap where it runs: the lists' `contentType` and every search keystroke
        // call it for every user message, most of which are not wake-ups.
        val headerEnd = firstNewline(text) ?: return null
        val header = text.substring(0, headerEnd + 1)

        SUBAGENT_HEADER.matchEntire(header)?.let { match ->
            val task = subagentTask(payloadLine(text, headerEnd + 1)) ?: return null
            if (task.status.wire != match.groupValues[1]) return null
            return WakeupDisplay(WakeupKind.SUBAGENT, listOf(task))
        }

        if (!BACKGROUND_HEADER.matches(header)) return null
        val payload = payloadLine(text, headerEnd + 1) as? JsonArray ?: return null
        if (payload.isEmpty()) return null
        val tasks = payload.map { backgroundTask(it) ?: return null }
        return WakeupDisplay(WakeupKind.BACKGROUND_TOOL, tasks)
    }

    /** Index of the first newline, or null when the first line is too long to be either header. */
    private fun firstNewline(text: String): Int? {
        val limit = minOf(text.length, MAX_HEADER_CHARS + 1)
        for (i in 0 until limit) {
            if (text[i] == '\n') return i
        }
        return null
    }

    /**
     * The line starting at [start], parsed as `JSON.parse` would, or null. Not quite as strict:
     * the tree parse reads an unquoted token (`oops`, `True`, `01`) as a literal where JavaScript
     * throws. Every required field is checked as a string, so only an extra field can carry one.
     */
    private fun payloadLine(text: String, start: Int): JsonElement? {
        val end = text.indexOf('\n', start).let { if (it < 0) text.length else it }
        return runCatching { Json.parseToJsonElement(text.substring(start, end)) }.getOrNull()
    }

    private fun subagentTask(payload: JsonElement?): WakeupTask? {
        val obj = payload as? JsonObject ?: return null
        val status = WakeupTaskStatus.fromWire(obj.string("status")) ?: return null
        return WakeupTask(
            taskId = obj.string("background_task_id") ?: return null,
            status = status,
            result = obj.string("result") ?: return null,
            threadId = obj.string("subagent_thread_id") ?: return null,
            subagentType = obj.string("subagent_type") ?: return null,
        )
    }

    private fun backgroundTask(payload: JsonElement?): WakeupTask? {
        val obj = payload as? JsonObject ?: return null
        val status = WakeupTaskStatus.fromWire(obj.string("status")) ?: return null
        return WakeupTask(
            taskId = obj.string("background_task_id") ?: return null,
            status = status,
            result = obj.string("result") ?: return null,
            toolCallId = obj.string("tool_call_id") ?: return null,
            toolName = obj.string("tool") ?: return null,
        )
    }

    /** `typeof x === 'string'`: a number or boolean under the key does not count. */
    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private const val MAX_WAKEUP_TEXT_CHARS = 512 * 1024

    /** Longer than either header line with room for any task count; a longer first line is neither. */
    private const val MAX_HEADER_CHARS = 256

    // Matched against the first line alone, whole. Literal dots stay escaped; no `\b`, which ICU on
    // Android treats differently from the JVM; `[0-9]` rather than `\d`, which ICU widens to every
    // Unicode digit where JavaScript's is ASCII.
    private val SUBAGENT_HEADER = Regex(
        """A detached subagent task has (completed|error|cancelled)\. """ +
            """Continue the parent task using its durable result below\.\n""",
    )
    private val BACKGROUND_HEADER = Regex(
        """(?:A background tool task has finished\. Continue using its durable result below\.|""" +
            """[0-9]+ background tool tasks have finished\. Continue using their durable results below\.)\n""",
    )
}
