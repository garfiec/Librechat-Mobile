package com.garfiec.librechat.core.model.background

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/** The tool whose output [BackgroundTaskOutput.parse] reads (upstream `Constants.CHECK_BACKGROUND_TASK`). */
const val CHECK_BACKGROUND_TASK_TOOL = "check_background_task"

/** Status of one task as the poll tool reports it. Upstream `BackgroundTaskStatus` in `Parts/background.ts`. */
enum class PolledTaskStatus(val wire: String) {
    RUNNING("running"),
    STOPPING("stopping"),
    ACCEPTED("accepted"),
    CLAIMED("claimed"),
    NOT_RUNNING("not_running"),
    CONTROL_NOT_FOUND("control_not_found"),
    COMPLETED("completed"),
    ERROR("error"),
    CANCELLED("cancelled"),
    DISPATCHED("dispatched"),
    FAILED("failed"),
    INTERRUPTED("interrupted"),
    ;

    val isFailure: Boolean get() = this == ERROR || this == FAILED || this == INTERRUPTED

    companion object {
        private val byWire = entries.associateBy { it.wire }

        /** `cancellation_requested` reads as [STOPPING]; anything unknown is null (the card is refused). */
        fun fromWire(value: String?): PolledTaskStatus? =
            if (value == "cancellation_requested") STOPPING else byWire[value]
    }
}

data class PolledTask(
    val taskId: String,
    val toolName: String,
    val status: PolledTaskStatus,
    val result: String? = null,
    val error: String? = null,
    val note: String? = null,
    val message: String? = null,
    val subagentType: String? = null,
    val resultAvailable: Boolean = false,
    val resultClaimed: Boolean = false,
    /** `pending` | `failed` | `delivered`, or null. */
    val delivery: String? = null,
)

sealed interface BackgroundTaskDisplay {
    data class Task(val task: PolledTask) : BackgroundTaskDisplay

    data class TaskList(
        val tasks: List<PolledTask>,
        val partial: Boolean,
        val warning: String? = null,
        val message: String? = null,
    ) : BackgroundTaskDisplay

    data class Notice(val status: String, val message: String) : BackgroundTaskDisplay
}

enum class BackgroundTaskOutcome { FAILED, CANCELLED }

/**
 * MIRRORED from upstream `parseBackgroundTaskOutput` / `backgroundTaskOutcome`
 * (`client/src/components/Chat/Messages/Content/Parts/background.ts`, v0.8.8).
 *
 * **Only host-shaped output becomes a card.** A single task, a `{tasks[], partial}` list, or a
 * `{status, message}` notice; anything else — including a list with one malformed row — returns
 * null so the raw tool output stays visible. The checks are `typeof`-strict on purpose: a
 * mistyped field means the shape is not the host's.
 */
object BackgroundTaskOutput {

    private const val MAX_OUTPUT_CHARS = 2_000_000
    private val PENDING_NOTICES = setOf("delivery_scheduled", "result_persisting")
    private val DELIVERY_VALUES = setOf("pending", "failed", "delivered")
    private val json = Json { isLenient = false }

    fun parse(output: String?): BackgroundTaskDisplay? {
        if (output.isNullOrEmpty() || output.length > MAX_OUTPUT_CHARS) return null
        val text = output.trim()
        if (!text.startsWith('{') || !text.endsWith('}')) return null
        val payload = runCatching { json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return null

        if ("tasks" in payload) {
            val list = payload["tasks"] as? JsonArray ?: return null
            val tasks = list.map { parseTask(it) ?: return null }
            return BackgroundTaskDisplay.TaskList(
                tasks = tasks,
                partial = payload.bool("partial") == true,
                warning = payload.string("warning"),
                message = payload.string("message"),
            )
        }
        parseTask(payload)?.let { return BackgroundTaskDisplay.Task(it) }
        val status = payload.string("status")
        val message = payload.string("message")
        return if (status != null && message != null) BackgroundTaskDisplay.Notice(status, message) else null
    }

    /** The poll step succeeding is not proof that the task or control behind it did. */
    fun outcome(display: BackgroundTaskDisplay?): BackgroundTaskOutcome? = when (display) {
        null -> null
        is BackgroundTaskDisplay.Notice -> when {
            display.status == "cancelled" -> BackgroundTaskOutcome.CANCELLED
            display.status in PENDING_NOTICES -> null
            else -> BackgroundTaskOutcome.FAILED
        }
        is BackgroundTaskDisplay.TaskList ->
            if (display.partial || display.warning != null) BackgroundTaskOutcome.FAILED else null
        is BackgroundTaskDisplay.Task -> when (display.task.status) {
            PolledTaskStatus.CANCELLED -> BackgroundTaskOutcome.CANCELLED
            PolledTaskStatus.ERROR,
            PolledTaskStatus.FAILED,
            PolledTaskStatus.INTERRUPTED,
            PolledTaskStatus.NOT_RUNNING,
            PolledTaskStatus.CONTROL_NOT_FOUND,
            -> BackgroundTaskOutcome.FAILED
            else -> null
        }
    }

    private fun parseTask(value: JsonElement): PolledTask? {
        val obj = value as? JsonObject ?: return null
        val status = PolledTaskStatus.fromWire(obj.string("status")) ?: return null
        val taskId = obj.string("background_task_id")?.takeIf { it.isNotEmpty() } ?: return null
        val tool = obj.string("tool")?.takeIf { it.isNotEmpty() } ?: return null
        if (listOf("result", "error", "note", "message", "subagent_type").any { !obj.optionalString(it) }) return null
        if (!obj.optionalBool("result_available") || !obj.optionalBool("result_claimed")) return null
        val delivery = obj["delivery"]
        if (delivery != null && (delivery !is JsonPrimitive || !delivery.isString || delivery.content !in DELIVERY_VALUES)) {
            return null
        }
        val cancelling = status == PolledTaskStatus.RUNNING && obj.bool("cancellation_requested") == true
        return PolledTask(
            taskId = taskId,
            toolName = tool,
            status = if (cancelling) PolledTaskStatus.STOPPING else status,
            result = obj.string("result"),
            error = obj.string("error"),
            note = obj.string("note"),
            message = obj.string("message"),
            subagentType = obj.string("subagent_type"),
            resultAvailable = obj.bool("result_available") == true,
            resultClaimed = obj.bool("result_claimed") == true,
            delivery = (delivery as? JsonPrimitive)?.content,
        )
    }

    /** `typeof x === 'string'`. */
    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.bool(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull

    /** Absent, or a string. `undefined` in upstream; an explicit JSON null is not a string. */
    private fun JsonObject.optionalString(key: String): Boolean {
        val v = this[key] ?: return true
        return v is JsonPrimitive && v.isString
    }

    /** Upstream `value != null && typeof value !== 'boolean'` rejects: null/absent pass, a boolean passes. */
    private fun JsonObject.optionalBool(key: String): Boolean {
        val v = this[key] ?: return true
        if (v is JsonNull) return true
        return v is JsonPrimitive && !v.isString && v.booleanOrNull != null
    }
}
