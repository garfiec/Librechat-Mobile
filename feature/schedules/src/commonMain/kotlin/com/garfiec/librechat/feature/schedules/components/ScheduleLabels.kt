package com.garfiec.librechat.feature.schedules.components

import androidx.compose.runtime.Composable
import com.garfiec.librechat.core.model.schedule.ScheduleDisabledReason
import com.garfiec.librechat.core.model.schedule.ScheduleFrequency
import com.garfiec.librechat.feature.schedules.resources.Res
import com.garfiec.librechat.feature.schedules.resources.schedule_disabled_agent
import com.garfiec.librechat.feature.schedules.resources.schedule_disabled_balance
import com.garfiec.librechat.feature.schedules.resources.schedule_disabled_failures
import com.garfiec.librechat.feature.schedules.resources.schedule_disabled_invalid
import com.garfiec.librechat.feature.schedules.resources.schedule_disabled_mcp_config
import com.garfiec.librechat.feature.schedules.resources.schedule_disabled_mcp_permission
import com.garfiec.librechat.feature.schedules.resources.schedule_disabled_mcp_reauth
import com.garfiec.librechat.feature.schedules.resources.schedule_disabled_permission
import com.garfiec.librechat.feature.schedules.resources.schedule_disabled_project
import com.garfiec.librechat.feature.schedules.resources.schedule_disabled_project_required
import com.garfiec.librechat.feature.schedules.resources.schedule_disabled_unknown
import com.garfiec.librechat.feature.schedules.resources.schedule_frequency_cron
import com.garfiec.librechat.feature.schedules.resources.schedule_frequency_daily
import com.garfiec.librechat.feature.schedules.resources.schedule_frequency_hourly
import com.garfiec.librechat.feature.schedules.resources.schedule_frequency_weekdays
import com.garfiec.librechat.feature.schedules.resources.schedule_frequency_weekly
import com.garfiec.librechat.feature.schedules.resources.schedule_problem_agent
import com.garfiec.librechat.feature.schedules.resources.schedule_problem_cron
import com.garfiec.librechat.feature.schedules.resources.schedule_problem_name
import com.garfiec.librechat.feature.schedules.resources.schedule_problem_no_agents
import com.garfiec.librechat.feature.schedules.resources.schedule_problem_project
import com.garfiec.librechat.feature.schedules.resources.schedule_problem_prompt
import com.garfiec.librechat.feature.schedules.resources.schedule_problem_time
import com.garfiec.librechat.feature.schedules.resources.schedule_problem_too_frequent
import com.garfiec.librechat.feature.schedules.resources.schedule_problem_weekday
import com.garfiec.librechat.feature.schedules.viewmodel.ScheduleDraftProblem
import org.jetbrains.compose.resources.stringResource

/**
 * Every [ScheduleDisabledReason] has a sentence, including the ones this client cannot repair.
 *
 * A schedule that stopped and says only "paused" leaves the user with nothing to act on, and three
 * of these name a specific, different repair. An unrecognized reason — one a newer server added —
 * still says the server paused it rather than rendering blank.
 */
@Composable
fun disabledReasonLabel(reason: String?): String = when (reason) {
    ScheduleDisabledReason.MCP_REAUTH_REQUIRED -> stringResource(Res.string.schedule_disabled_mcp_reauth)
    ScheduleDisabledReason.MCP_CONFIGURATION_MISSING -> stringResource(Res.string.schedule_disabled_mcp_config)
    ScheduleDisabledReason.MCP_PERMISSION_DENIED -> stringResource(Res.string.schedule_disabled_mcp_permission)
    ScheduleDisabledReason.TOO_MANY_FAILURES -> stringResource(Res.string.schedule_disabled_failures)
    ScheduleDisabledReason.AGENT_DELETED -> stringResource(Res.string.schedule_disabled_agent)
    ScheduleDisabledReason.INVALID_SCHEDULE -> stringResource(Res.string.schedule_disabled_invalid)
    ScheduleDisabledReason.PERMISSION_REVOKED -> stringResource(Res.string.schedule_disabled_permission)
    ScheduleDisabledReason.INSUFFICIENT_BALANCE -> stringResource(Res.string.schedule_disabled_balance)
    ScheduleDisabledReason.PROJECT_DELETED -> stringResource(Res.string.schedule_disabled_project)
    ScheduleDisabledReason.PROJECT_REQUIRED -> stringResource(Res.string.schedule_disabled_project_required)
    else -> stringResource(Res.string.schedule_disabled_unknown)
}

@Composable
fun frequencyLabel(frequency: String): String = when (frequency) {
    ScheduleFrequency.HOURLY -> stringResource(Res.string.schedule_frequency_hourly)
    ScheduleFrequency.DAILY -> stringResource(Res.string.schedule_frequency_daily)
    ScheduleFrequency.WEEKDAYS -> stringResource(Res.string.schedule_frequency_weekdays)
    ScheduleFrequency.WEEKLY -> stringResource(Res.string.schedule_frequency_weekly)
    ScheduleFrequency.CRON -> stringResource(Res.string.schedule_frequency_cron)
    // A cadence a newer server added. Shown as its own wire value rather than mislabelled.
    else -> frequency
}

@Composable
fun problemLabel(problem: ScheduleDraftProblem, minIntervalMinutes: Int): String = when (problem) {
    ScheduleDraftProblem.NAME_REQUIRED -> stringResource(Res.string.schedule_problem_name)
    ScheduleDraftProblem.PROMPT_REQUIRED -> stringResource(Res.string.schedule_problem_prompt)
    ScheduleDraftProblem.AGENT_REQUIRED -> stringResource(Res.string.schedule_problem_agent)
    ScheduleDraftProblem.CRON_SHAPE -> stringResource(Res.string.schedule_problem_cron)
    ScheduleDraftProblem.TIME_REQUIRED -> stringResource(Res.string.schedule_problem_time)
    ScheduleDraftProblem.WEEKDAY_REQUIRED -> stringResource(Res.string.schedule_problem_weekday)
    ScheduleDraftProblem.NO_AGENTS -> stringResource(Res.string.schedule_problem_no_agents)
    ScheduleDraftProblem.PROJECT_REQUIRED -> stringResource(Res.string.schedule_problem_project)
    ScheduleDraftProblem.TOO_FREQUENT ->
        stringResource(Res.string.schedule_problem_too_frequent, minIntervalMinutes)
}
