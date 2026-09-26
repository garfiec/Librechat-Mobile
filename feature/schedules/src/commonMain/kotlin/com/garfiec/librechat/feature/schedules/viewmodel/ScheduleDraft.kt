package com.garfiec.librechat.feature.schedules.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.model.schedule.CreateScheduleRequest
import com.garfiec.librechat.core.model.schedule.Schedule
import com.garfiec.librechat.core.model.schedule.ScheduleCadence
import com.garfiec.librechat.core.model.schedule.ScheduleFrequency
import com.garfiec.librechat.core.model.schedule.ScheduleLimits
import com.garfiec.librechat.core.model.schedule.UpdateScheduleRequest
import com.garfiec.librechat.core.model.schedule.cadenceIntervalMinutesOrUnknown
import com.garfiec.librechat.core.model.schedule.isPlausibleCronShape

/**
 * The editable form behind the schedule editor, kept apart from the ViewModel so the rules that
 * decide what gets SENT are pure and testable without a Compose harness.
 */
@Immutable
data class ScheduleDraft(
    val name: String = "",
    val prompt: String = "",
    val agentId: String = "",
    val frequency: String = ScheduleFrequency.DAILY,
    /**
     * Held as text, not as Int.
     *
     * Parsing on every keystroke and discarding what does not parse means the field cannot be
     * cleared to retype it and swallows a leading zero — it snaps back under the user's cursor.
     * Blank is a legitimate intermediate state here, and becomes [ScheduleDraftProblem.TIME_REQUIRED]
     * rather than a silently substituted default.
     */
    val hourText: String = DEFAULT_HOUR.toString(),
    val minuteText: String = "0",
    val daysOfWeek: Set<Int> = setOf(DEFAULT_WEEKDAY),
    val cronExpression: String = "",
    val timezone: String = "",
    val chatProjectId: String? = null,
    val enabled: Boolean = true,
) {
    val isCron: Boolean get() = frequency == ScheduleFrequency.CRON

    val hour: Int? get() = hourText.trim().toIntOrNull()?.takeIf { it in 0..MAX_HOUR }
    val minute: Int? get() = minuteText.trim().toIntOrNull()?.takeIf { it in 0..MAX_MINUTE }

    val cadence: ScheduleCadence
        get() = if (isCron) {
            ScheduleCadence.cron(cronExpression)
        } else {
            ScheduleCadence.structured(
                frequency = frequency,
                hour = hour ?: 0,
                minute = minute ?: 0,
                daysOfWeek = daysOfWeek.toList().takeIf { frequency == ScheduleFrequency.WEEKLY },
            )
        }

    companion object {
        const val DEFAULT_HOUR = 9
        const val DEFAULT_WEEKDAY = 1
        const val MAX_HOUR = 23
        const val MAX_MINUTE = 59

        /** Loads an existing schedule into the form, cron expression and all. */
        fun from(schedule: Schedule): ScheduleDraft {
            val cadence = schedule.cadence
            return ScheduleDraft(
                name = schedule.name,
                prompt = schedule.prompt,
                agentId = schedule.agentId,
                frequency = cadence.frequency,
                // Blank for a cron row: the cadence carries no hour or minute, so a default here
                // would show a time the schedule does not run at — and would be sent back as one
                // if the user then switched the form to a structured frequency.
                hourText = cadence.hour?.toString().orEmpty(),
                minuteText = cadence.minute?.toString().orEmpty(),
                daysOfWeek = cadence.daysOfWeek?.toSet()?.takeIf { it.isNotEmpty() }
                    ?: setOf(DEFAULT_WEEKDAY),
                cronExpression = cadence.expression.orEmpty(),
                timezone = schedule.timezone,
                chatProjectId = schedule.chatProjectId,
                enabled = schedule.enabled,
            )
        }
    }
}

/** Why a draft cannot be saved yet. Null means it can. */
enum class ScheduleDraftProblem {
    NAME_REQUIRED,
    PROMPT_REQUIRED,
    AGENT_REQUIRED,
    CRON_SHAPE,
    TIME_REQUIRED,
    WEEKDAY_REQUIRED,
    NO_AGENTS,
    PROJECT_REQUIRED,
    TOO_FREQUENT,
}

/**
 * The first reason this draft would be refused, checked against the deployment's own policy.
 *
 * [ScheduleLimits.minIntervalMinutes] ships with the list for exactly this: refusing a cadence
 * before submit beats a 400 after it. A cron cadence's interval is deliberately NOT checked here —
 * measuring it needs a real cron engine, so it is submitted and the server answers.
 */
fun ScheduleDraft.firstProblem(
    limits: ScheduleLimits,
    hasNoAgents: Boolean = false,
): ScheduleDraftProblem? = when {
    name.isBlank() -> ScheduleDraftProblem.NAME_REQUIRED
    prompt.isBlank() -> ScheduleDraftProblem.PROMPT_REQUIRED
    hasNoAgents -> ScheduleDraftProblem.NO_AGENTS
    agentId.isBlank() -> ScheduleDraftProblem.AGENT_REQUIRED
    isCron && !isPlausibleCronShape(cronExpression) -> ScheduleDraftProblem.CRON_SHAPE
    // An hourly cadence compiles to `<minute> * * * *` — the hour is never read, which is why the
    // editor disables that field for it. Requiring one anyway made the only field that could
    // satisfy the rule the one field the user could not reach.
    !isCron && minute == null -> ScheduleDraftProblem.TIME_REQUIRED
    !isCron && frequency != ScheduleFrequency.HOURLY && hour == null ->
        ScheduleDraftProblem.TIME_REQUIRED
    frequency == ScheduleFrequency.WEEKLY && daysOfWeek.isEmpty() ->
        ScheduleDraftProblem.WEEKDAY_REQUIRED
    // A pinned project is supplied by the server, so only an unpinned requirement can be unmet.
    limits.requireProject && limits.projectId == null && chatProjectId == null ->
        ScheduleDraftProblem.PROJECT_REQUIRED
    isTooFrequent(limits) -> ScheduleDraftProblem.TOO_FREQUENT
    else -> null
}

private fun ScheduleDraft.isTooFrequent(limits: ScheduleLimits): Boolean {
    val interval = cadenceIntervalMinutesOrUnknown(cadence) ?: return false
    return interval < limits.minIntervalMinutes
}

fun ScheduleDraft.toCreateRequest(clientRequestId: String): CreateScheduleRequest =
    CreateScheduleRequest(
        name = name.trim(),
        prompt = prompt.trim(),
        agentId = agentId,
        cadence = cadence,
        timezone = timezone,
        clientRequestId = clientRequestId,
        // Sent as chosen. A pinned deployment ignores it and fills its own destination in, which
        // is why the editor offers no picker there rather than suppressing the field here.
        chatProjectId = chatProjectId,
        enabled = enabled,
    )

/**
 * The PATCH body for the difference between [original] and this draft.
 *
 * **Everything unchanged is omitted, and that is the safety property of the editor rather than an
 * optimisation.** `cadence` travels whole: a body that always carried it would rewrite a cron
 * expression this form cannot represent into whatever the structured controls happen to hold, so
 * renaming a cron schedule would silently change when it runs. `file_ids` is never sent at all —
 * this client does not manage schedule attachments, and an empty list would detach them.
 *
 * [Schedule.configRevision] rides along so a concurrent edit elsewhere answers `409` instead of
 * being overwritten by a payload computed from a stale snapshot.
 */
fun ScheduleDraft.diffAgainst(original: Schedule): UpdateScheduleRequest {
    val trimmedName = name.trim()
    val trimmedPrompt = prompt.trim()
    return UpdateScheduleRequest(
        name = trimmedName.takeIf { it != original.name },
        prompt = trimmedPrompt.takeIf { it != original.prompt },
        agentId = agentId.takeIf { it != original.agentId },
        cadence = cadence.takeIf { it != original.cadence },
        timezone = timezone.takeIf { it != original.timezone },
        // Only ever set, never cleared: `chatProjectId: null` CLEARS the scope server-side, and
        // `explicitNulls = false` cannot express that anyway — so the editor does not offer it.
        chatProjectId = chatProjectId?.takeIf { it != original.chatProjectId },
        enabled = enabled.takeIf { it != original.enabled },
        expectedConfigRevision = original.configRevision,
    )
}
