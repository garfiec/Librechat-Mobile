package com.garfiec.librechat.core.model.background

import kotlinx.serialization.Serializable

/**
 * `GET /api/convos/:conversationId/background-tasks` (v0.8.8) — the caller's ordinary background
 * tool tasks in one conversation. Mirrors upstream `BackgroundTaskIndex`
 * (`packages/data-provider/src/types/background.ts`).
 */
@Serializable
data class BackgroundTaskIndex(
    val conversationId: String = "",
    val tasks: List<BackgroundTaskSummary> = emptyList(),
    /**
     * False when the durable store is unavailable or its bounded list was cut short, so finished
     * results held elsewhere may be missing. Absent on no current server; treated as complete.
     */
    val complete: Boolean? = null,
    /**
     * Whether the deployment lets users stop ordinary background tools
     * (`endpoints.agents.ordinaryToolCancellation`). The cancel route answers 403 otherwise, so Stop
     * is offered only when this is true.
     */
    val cancellable: Boolean = false,
)

/**
 * One task's public projection. Results, artifacts and errors stay server-side.
 *
 * [status] and [delivery] stay raw strings: an enum would fail the whole list on a value a newer
 * server adds, where an unknown one should degrade one row.
 */
@Serializable
data class BackgroundTaskSummary(
    val taskId: String = "",
    val toolName: String = "",
    val toolCallId: String = "",
    val messageId: String? = null,
    val stepId: String? = null,
    /** See [BackgroundTaskStatus]. */
    val status: String = "",
    val cancellationRequested: Boolean = false,
    /** ISO-8601 dispatch time. */
    val startedAt: String? = null,
    /** ISO-8601 terminal time; absent while running. */
    val settledAt: String? = null,
    /** See [BackgroundTaskDelivery]. Absent for tasks without automatic delivery. */
    val delivery: String? = null,
) {
    val isRunning: Boolean get() = status == BackgroundTaskStatus.RUNNING
}

object BackgroundTaskStatus {
    const val RUNNING = "running"
    const val COMPLETED = "completed"
    const val ERROR = "error"
    const val CANCELLED = "cancelled"
}

/** Whether a finished task's result has reached the agent: `pending` still arrives as a new turn, `failed` never will. */
object BackgroundTaskDelivery {
    const val PENDING = "pending"
    const val DELIVERED = "delivered"
    const val FAILED = "failed"
}

/**
 * `POST /api/convos/:conversationId/background-tasks/cancel`. A null [taskIds] is OMITTED from the
 * body (the client's `explicitNulls = false`), which the server reads as "every running task" —
 * a literal `null` would be a 400.
 */
@Serializable
data class BackgroundTaskCancelRequest(
    val taskIds: List<String>? = null,
)

@Serializable
data class BackgroundTaskCancelResponse(
    val results: List<BackgroundTaskCancelResult> = emptyList(),
)

@Serializable
data class BackgroundTaskCancelResult(
    val taskId: String = "",
    /** `not_found` | `unavailable` | `requested` | `already_requested` | `settled`. */
    val status: String = "",
) {
    /** The cancellation was accepted, now or earlier, or the task had already finished. */
    val isResolved: Boolean get() = status in RESOLVED

    private companion object {
        val RESOLVED = setOf("requested", "already_requested", "settled")
    }
}
