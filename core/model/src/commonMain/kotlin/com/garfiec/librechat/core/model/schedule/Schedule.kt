package com.garfiec.librechat.core.model.schedule

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A scheduled chat (v0.8.8-rc2): a prompt the SERVER sends to an agent on a recurring cadence,
 * filing each run's conversation under a chat project.
 *
 * Experimental and **default-OFF** upstream — see `isSchedulesEnabled`. Every string-typed
 * enumeration here stays a raw `String` for the same reason as elsewhere in this codebase: an
 * unrecognized enum value throws at DECODE time, which would fail the whole list rather than
 * degrading one row.
 */
@Serializable
data class Schedule(
    val id: String,
    val user: String = "",
    val name: String = "",
    val prompt: String = "",
    @SerialName("agent_id") val agentId: String = "",
    val cadence: ScheduleCadence,
    val timezone: String = "",
    /** Only `"new"` exists today: each run opens a fresh conversation. */
    val target: String = ScheduleTarget.NEW,
    @SerialName("file_ids") val fileIds: List<String>? = null,
    /**
     * Already resolved against the deployment's pinned project, so this is the destination the
     * next run will actually use — not the client's stored choice.
     */
    val chatProjectId: String? = null,
    val enabled: Boolean = true,
    /** Why the SERVER turned this off. See [ScheduleDisabledReason]. */
    val disabledReason: String? = null,
    val nextRunAt: String? = null,
    val lastRun: ScheduleLastRun? = null,
    /**
     * The occurrences generating right now, with the conversation each is producing.
     *
     * Read from the run rows rather than from [lastRun], which is projected only once a run
     * settles. A run parked on an approval is deliberately absent — its chat was listed long ago,
     * and those rows accumulate for as long as approvals wait.
     */
    val inFlight: List<ScheduleInFlightRun>? = null,
    val runCount: Int = 0,
    val failureCount: Int = 0,
    /**
     * Bumped by every config change. An edit must send the revision it was computed FROM
     * (`expectedConfigRevision`) so a concurrent edit elsewhere answers 409 instead of being
     * silently overwritten — cadence is sent whole, so a fresh-read fence alone cannot see it.
     */
    val configRevision: Int? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class ScheduleInFlightRun(val conversationId: String)

@Serializable
data class ScheduleLastRun(
    val conversationId: String? = null,
    val status: String? = null,
    val error: String? = null,
    val mcp: List<ScheduleMcpOutcome>? = null,
    val firedAt: String? = null,
)

/** Per-server readiness of the unattended MCP preflight. */
@Serializable
data class ScheduleMcpOutcome(
    val server: String = "",
    /** The agent whose selected tool needs this server — a handoff or subagent, when set. */
    val agentId: String? = null,
    val status: String = "",
)

object ScheduleTarget {
    const val NEW = "new"
}

object ScheduleRunStatus {
    const val STARTED = "started"
    const val REQUIRES_ACTION = "requires_action"
    const val SUCCESS = "success"
    const val ERROR = "error"
    const val INTERRUPTED = "interrupted"
    const val SKIPPED_OVERLAP = "skipped_overlap"
    const val SKIPPED_BALANCE = "skipped_balance"
}

/**
 * Why a schedule is off. Every value has to render: a schedule that says "disabled" with no
 * reason leaves the user with nothing to act on, and several of these need a specific repair.
 */
object ScheduleDisabledReason {
    const val MCP_REAUTH_REQUIRED = "mcp_reauth_required"
    const val MCP_CONFIGURATION_MISSING = "mcp_configuration_missing"
    const val MCP_PERMISSION_DENIED = "mcp_permission_denied"
    const val TOO_MANY_FAILURES = "too_many_failures"
    const val AGENT_DELETED = "agent_deleted"
    const val INVALID_SCHEDULE = "invalid_schedule"
    const val PERMISSION_REVOKED = "permission_revoked"
    const val INSUFFICIENT_BALANCE = "insufficient_balance"
    const val PROJECT_DELETED = "project_deleted"
    const val PROJECT_REQUIRED = "project_required"
}

object ScheduleMcpStatus {
    const val READY = "ready"
    const val REAUTH_REQUIRED = "mcp_reauth_required"
    const val CONFIGURATION_MISSING = "mcp_configuration_missing"
    const val PERMISSION_DENIED = "mcp_permission_denied"
    const val UNAVAILABLE = "mcp_unavailable"
}

object ScheduleFrequency {
    const val HOURLY = "hourly"
    const val DAILY = "daily"
    const val WEEKDAYS = "weekdays"
    const val WEEKLY = "weekly"
    const val CRON = "cron"

    /** The cadences a structured picker can build. [CRON] is the escape hatch beside them. */
    val STRUCTURED = listOf(HOURLY, DAILY, WEEKDAYS, WEEKLY)
}

/**
 * A cadence, flattened from upstream's discriminated union.
 *
 * Flat rather than sealed because the union's arms share a decoder here: a sealed hierarchy needs
 * a discriminator serializer that throws on an unrecognized `frequency`, which would fail the
 * whole list for one row a newer server added. **Encoding is what has to stay disciplined** —
 * the server's zod schema rejects a structured cadence carrying `expression`, or a cron one
 * carrying `hour`, so build these through [structured] / [cron] rather than by hand.
 */
@Serializable
data class ScheduleCadence(
    /**
     * Required, with no default, and that is not an oversight. `encodeDefaults = false` drops a
     * field whose value equals its default, so a default of `"daily"` would silently omit the
     * discriminator from every daily payload and the server's union would reject it. A default of
     * anything else would let a row that somehow arrived without one read as a cadence it is not,
     * which an edit could then write back. It is the discriminator of a zod `discriminatedUnion`,
     * so no row the server wrote can lack it.
     */
    val frequency: String,
    val hour: Int? = null,
    val minute: Int? = null,
    val daysOfWeek: List<Int>? = null,
    val expression: String? = null,
) {
    val isCron: Boolean get() = frequency == ScheduleFrequency.CRON

    companion object {
        fun structured(
            frequency: String,
            hour: Int,
            minute: Int,
            daysOfWeek: List<Int>? = null,
        ): ScheduleCadence = ScheduleCadence(
            frequency = frequency,
            // Required by the server schema for every structured arm, including `hourly`,
            // whose hour is unused but must still be present and in range.
            hour = hour.coerceIn(0, 23),
            minute = minute.coerceIn(0, 59),
            daysOfWeek = daysOfWeek?.distinct()?.sorted()?.takeIf { it.isNotEmpty() },
        )

        fun cron(expression: String): ScheduleCadence =
            ScheduleCadence(frequency = ScheduleFrequency.CRON, expression = expression.trim())
    }
}

/** Bound on a stored cron expression; five fields each holding a list run long. */
const val SCHEDULE_CRON_MAX_LENGTH = 256

/** The server caps attachments per schedule. */
const val SCHEDULE_MAX_FILES = 10
