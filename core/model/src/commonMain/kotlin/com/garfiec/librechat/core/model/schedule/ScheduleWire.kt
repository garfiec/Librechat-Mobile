package com.garfiec.librechat.core.model.schedule

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The deployment's schedule policy, served WITH the list so a form can refuse a choice the
 * server would reject rather than surfacing it as a 400 after submit.
 */
@Serializable
data class ScheduleLimits(
    val maxPerUser: Int = 0,
    val minIntervalMinutes: Int = 1,
    /** Every schedule must be filed under a chat project. Implied by [projectId]. */
    val requireProject: Boolean = false,
    /**
     * Operator-pinned destination. When set it is the ONLY destination and **the client must not
     * offer a picker** — the server ignores any choice sent anyway.
     */
    val projectId: String? = null,
)

/** `GET /api/schedules`. The single-schedule routes answer a bare [Schedule] instead. */
@Serializable
data class ScheduleListResponse(
    val schedules: List<Schedule> = emptyList(),
    val limits: ScheduleLimits = ScheduleLimits(),
)

/**
 * Body of `POST /api/schedules`.
 *
 * [clientRequestId] is REQUIRED and is the idempotency key: creation commits the row and arms it
 * in two writes, so a failure between them leaves the client unable to tell whether anything
 * persisted. A blind retry then produces two recurring schedules; a retry carrying the same key
 * resolves to the original row. It must therefore be STABLE across retries of one creation.
 */
@Serializable
data class CreateScheduleRequest(
    val name: String,
    val prompt: String,
    @SerialName("agent_id") val agentId: String,
    val cadence: ScheduleCadence,
    val timezone: String,
    val clientRequestId: String,
    val target: String = ScheduleTarget.NEW,
    @SerialName("file_ids") val fileIds: List<String>? = null,
    val chatProjectId: String? = null,
    val enabled: Boolean = true,
)

/**
 * Body of `PATCH /api/schedules/:id`. Every field is optional, and that is load-bearing rather
 * than convenient.
 *
 * **Omitting a field leaves it alone.** [cadence] is sent whole, so an editor that always sends
 * it would rewrite a cron expression it cannot represent into whatever its structured controls
 * happen to hold. [fileIds] is the same: sending an empty list would detach attachments this
 * client never offered to manage. Send only what the user changed.
 *
 * [expectedConfigRevision] is the revision the edit was computed from. The server fences on it
 * and answers 409 rather than letting a concurrent edit elsewhere be silently overwritten.
 */
@Serializable
data class UpdateScheduleRequest(
    val name: String? = null,
    val prompt: String? = null,
    @SerialName("agent_id") val agentId: String? = null,
    val cadence: ScheduleCadence? = null,
    val timezone: String? = null,
    val target: String? = null,
    @SerialName("file_ids") val fileIds: List<String>? = null,
    val chatProjectId: String? = null,
    val enabled: Boolean? = null,
    val expectedConfigRevision: Int? = null,
) {
    /** The server refuses an update that changes nothing (`400`), so ask before sending. */
    val isEmpty: Boolean
        get() = name == null && prompt == null && agentId == null && cadence == null &&
            timezone == null && target == null && fileIds == null && chatProjectId == null &&
            enabled == null
}

/** `POST /api/schedules/:id/run`. */
@Serializable
data class ScheduleRunNowResponse(
    val scheduleId: String = "",
    val conversationId: String = "",
    val status: String = ScheduleRunStatus.STARTED,
)

/** `DELETE /api/schedules/:id` — `200` erased, `202` still draining a live run. */
@Serializable
data class ScheduleDeleteResponse(val id: String = "")

/** `code` values the schedule routes answer with. Most refusals carry only `error` prose. */
object ScheduleErrorCode {
    /**
     * `503` — the engine has not armed YET. Carries `Retry-After`; retrying is correct.
     */
    const val NOT_READY = "SCHEDULES_NOT_READY"

    /**
     * `503` — arming is attempted exactly once at boot and failed, so this is terminal for the
     * life of the server process. **Never retry it**: nothing re-attempts arming, so a client
     * obeying a backoff would poll a condition that cannot change without operator action.
     */
    const val UNAVAILABLE = "SCHEDULES_UNAVAILABLE"

    /** Run-now preflight: an MCP server the agent needs could not be reached. */
    const val MCP_UNAVAILABLE = "mcp_unavailable"
}
