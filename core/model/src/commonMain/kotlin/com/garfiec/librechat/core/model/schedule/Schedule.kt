package com.garfiec.librechat.core.model.schedule

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

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
    /** A finer diagnosis under [status] (v0.8.8). See [ScheduleMcpDetail]. */
    val detail: String? = null,
)

object ScheduleMcpDetail {
    /**
     * The server's scheduled on-behalf-of credential is missing: an administrator has to configure
     * a renewable provider, and reconnecting in chat does not help.
     */
    const val UNATTENDED_AUTH_REQUIRED = "unattended_auth_required"
}

/**
 * Upstream `scheduleDisabledMCPLabel` over `scheduleMCPRecoveryOutcomes`: a
 * `mcp_configuration_missing` pause caused by missing unattended auth names the administrator as
 * the repair instead of generic configuration.
 */
val Schedule.pausedForUnattendedMcpAuth: Boolean
    get() = disabledReason == ScheduleDisabledReason.MCP_CONFIGURATION_MISSING &&
        mcpRecoveryOutcomes().any { it.detail == ScheduleMcpDetail.UNATTENDED_AUTH_REQUIRED }

private val MCP_RECOVERY_REASONS = setOf(
    ScheduleDisabledReason.MCP_REAUTH_REQUIRED,
    ScheduleDisabledReason.MCP_CONFIGURATION_MISSING,
    ScheduleDisabledReason.MCP_PERMISSION_DENIED,
    ScheduleDisabledReason.TOO_MANY_FAILURES,
)

/**
 * MIRRORED from upstream `scheduleMCPRecoveryOutcomes`
 * (`client/src/components/SidePanel/Schedules/errors.ts`): the per-server outcomes a paused
 * schedule's recovery reads. The structured `lastRun.mcp` wins; without it they are recovered from
 * `lastRun.error`, the string a run that failed its MCP preflight records them in.
 *
 * Upstream also falls back when `mcp` is present but fails its schema. An element with an unknown
 * status or detail is treated the same way here, but an `mcp` that is not an array at all fails
 * the whole [Schedule] decode instead.
 */
fun Schedule.mcpRecoveryOutcomes(): List<ScheduleMcpOutcome> {
    if (enabled || disabledReason !in MCP_RECOVERY_REASONS) return emptyList()
    val persisted = lastRun?.mcp
    if (persisted != null && persisted.all { it.isKnownShape() }) return persisted
    return readScheduleMcpOutcomes(lastRun?.error)
}

/**
 * MIRRORED from upstream `readScheduleMCPOutcomes` (`packages/data-provider/src/types/schedules.ts`):
 * parses `"<status>: [<outcomes JSON>]"`, the form a preflight failure is recorded in. Anything
 * else — another prefix, JSON that does not decode, an outcome of an unknown status — yields nothing.
 */
fun readScheduleMcpOutcomes(error: String?): List<ScheduleMcpOutcome> {
    if (error == null || !MCP_OUTCOMES_ERROR.containsMatchIn(error)) return emptyList()
    val outcomes = try {
        outcomesJson.decodeFromString<List<ScheduleMcpOutcome>>(error.substring(error.indexOf(": ") + 2))
    } catch (_: SerializationException) {
        return emptyList()
    } catch (_: IllegalArgumentException) {
        return emptyList()
    }
    return if (outcomes.all { it.isKnownShape() }) outcomes else emptyList()
}

private val MCP_OUTCOMES_ERROR =
    Regex("""^mcp_(reauth_required|configuration_missing|permission_denied|unavailable): \[""")

private val KNOWN_MCP_STATUSES = setOf(
    ScheduleMcpStatus.READY,
    ScheduleMcpStatus.REAUTH_REQUIRED,
    ScheduleMcpStatus.CONFIGURATION_MISSING,
    ScheduleMcpStatus.PERMISSION_DENIED,
    ScheduleMcpStatus.UNAVAILABLE,
)

// Upstream's schema requires `server` and `status`; the data class defaults both to "", so a blank
// status is what a missing one decodes to and fails the known-status check like any other.
private fun ScheduleMcpOutcome.isKnownShape(): Boolean =
    status in KNOWN_MCP_STATUSES && (detail == null || detail == ScheduleMcpDetail.UNATTENDED_AUTH_REQUIRED)

private val outcomesJson = Json { ignoreUnknownKeys = true }

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
