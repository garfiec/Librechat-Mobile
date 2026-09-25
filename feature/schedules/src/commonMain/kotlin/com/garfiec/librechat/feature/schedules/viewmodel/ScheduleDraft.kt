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
    val hour: Int = DEFAULT_HOUR,
    val minute: Int = 0,
    val daysOfWeek: Set<Int> = setOf(DEFAULT_WEEKDAY),
    val cronExpression: String = "",
    val timezone: String = "",
    val chatProjectId: String? = null,
    val enabled: Boolean = true,
) {
    val isCron: Boolean get() = frequency == ScheduleFrequency.CRON

    val cadence: ScheduleCadence
        get() = if (isCron) {
            ScheduleCadence.cron(cronExpression)
        } else {
            ScheduleCadence.structured(
                frequency = frequency,
                hour = hour,
                minute = minute,
                daysOfWeek = daysOfWeek.toList().takeIf { frequency == ScheduleFrequency.WEEKLY },
            )
        }

    companion object {
        const val DEFAULT_HOUR = 9
        const val DEFAULT_WEEKDAY = 1

        /** Loads an existing schedule into the form, cron expression and all. */
        fun from(schedule: Schedule): ScheduleDraft {
            val cadence = schedule.cadence
            return ScheduleDraft(
                name = schedule.name,
                prompt = schedule.prompt,
                agentId = schedule.agentId,
                frequency = cadence.frequency,
                hour = cadence.hour ?: DEFAULT_HOUR,
                minute = cadence.minute ?: 0,
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
    WEEKDAY_REQUIRED,
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
fun ScheduleDraft.firstProblem(limits: ScheduleLimits): ScheduleDraftProblem? = when {
    name.isBlank() -> ScheduleDraftProblem.NAME_REQUIRED
    prompt.isBlank() -> ScheduleDraftProblem.PROMPT_REQUIRED
    agentId.isBlank() -> ScheduleDraftProblem.AGENT_REQUIRED
    isCron && !isPlausibleCronShape(cronExpression) -> ScheduleDraftProblem.CRON_SHAPE
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
        // A pinned deployment ignores any choice sent, so nothing is sent — the server fills it.
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
