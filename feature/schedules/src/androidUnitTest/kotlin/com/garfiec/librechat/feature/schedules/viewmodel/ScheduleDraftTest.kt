package com.garfiec.librechat.feature.schedules.viewmodel

import com.garfiec.librechat.core.model.schedule.Schedule
import com.garfiec.librechat.core.model.schedule.ScheduleCadence
import com.garfiec.librechat.core.model.schedule.ScheduleFrequency
import com.garfiec.librechat.core.model.schedule.ScheduleLimits
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * What the editor sends.
 *
 * The load-bearing case is the cron schedule: this form cannot represent `0 9,17 * * 1-5` with its
 * structured controls, and `cadence` travels whole, so a PATCH body that always carried a cadence
 * would silently change when an existing schedule runs — a rename would move the schedule. The
 * defence is omission, not rendering, so it is asserted on the body rather than on the form.
 */
class ScheduleDraftTest {

    private val cronSchedule = Schedule(
        id = "sched_1",
        name = "Shift report",
        prompt = "Summarise the shift",
        agentId = "agent_abc",
        cadence = ScheduleCadence.cron("0 9,17 * * 1-5"),
        timezone = "Europe/London",
        chatProjectId = "proj_1",
        enabled = true,
        configRevision = 7,
    )

    private val limits = ScheduleLimits(maxPerUser = 10, minIntervalMinutes = 1)

    @Test
    fun renaming_a_cron_schedule_leaves_its_cadence_alone() {
        val draft = ScheduleDraft.from(cronSchedule).copy(name = "Renamed")

        val body = draft.diffAgainst(cronSchedule)

        assertThat(body.name).isEqualTo("Renamed")
        assertThat(body.cadence).isNull()
        assertThat(body.expectedConfigRevision).isEqualTo(7)
    }

    @Test
    fun a_cron_expression_round_trips_through_the_form() {
        val draft = ScheduleDraft.from(cronSchedule)

        assertThat(draft.isCron).isTrue()
        assertThat(draft.cronExpression).isEqualTo("0 9,17 * * 1-5")
        assertThat(draft.diffAgainst(cronSchedule).cadence).isNull()
    }

    @Test
    fun a_deliberate_cadence_change_is_sent_whole() {
        val draft = ScheduleDraft.from(cronSchedule)
            .copy(frequency = ScheduleFrequency.DAILY, hourText = "6", minuteText = "45")

        val cadence = draft.diffAgainst(cronSchedule).cadence

        assertThat(cadence?.frequency).isEqualTo(ScheduleFrequency.DAILY)
        assertThat(cadence?.hour).isEqualTo(6)
        assertThat(cadence?.expression).isNull()
    }

    @Test
    fun attachments_are_never_touched() {
        // This client does not manage schedule attachments, and an empty list would detach them.
        val withFiles = cronSchedule.copy(fileIds = listOf("file-1"))

        val body = ScheduleDraft.from(withFiles).copy(prompt = "changed").diffAgainst(withFiles)

        assertThat(body.fileIds).isNull()
    }

    @Test
    fun an_untouched_form_produces_an_empty_body() {
        assertThat(ScheduleDraft.from(cronSchedule).diffAgainst(cronSchedule).isEmpty).isTrue()
    }

    @Test
    fun the_project_scope_is_only_ever_set_never_cleared() {
        // `chatProjectId: null` clears the scope server-side and `explicitNulls = false` cannot
        // express it, so the editor does not offer a clear rather than pretending to.
        val body = ScheduleDraft.from(cronSchedule).copy(chatProjectId = null)
            .diffAgainst(cronSchedule)

        assertThat(body.chatProjectId).isNull()
        assertThat(body.isEmpty).isTrue()
    }

    @Test
    fun a_new_project_scope_is_sent() {
        val body = ScheduleDraft.from(cronSchedule).copy(chatProjectId = "proj_2")
            .diffAgainst(cronSchedule)

        assertThat(body.chatProjectId).isEqualTo("proj_2")
    }

    @Test
    fun a_cadence_below_the_floor_is_refused_before_submit() {
        // The floor ships with the list for this — a 400 after submit is what it exists to avoid.
        val hourly = ScheduleDraft(
            name = "n",
            prompt = "p",
            agentId = "a",
            frequency = ScheduleFrequency.HOURLY,
        )

        assertThat(hourly.firstProblem(ScheduleLimits(minIntervalMinutes = 180)))
            .isEqualTo(ScheduleDraftProblem.TOO_FREQUENT)
        assertThat(hourly.firstProblem(ScheduleLimits(minIntervalMinutes = 60))).isNull()
    }

    @Test
    fun a_cron_cadence_is_submitted_rather_than_judged_locally() {
        // Measuring a cron expression's tightest gap needs a real cron engine; guessing here
        // would refuse cadences the server accepts.
        val draft = ScheduleDraft(
            name = "n",
            prompt = "p",
            agentId = "a",
            frequency = ScheduleFrequency.CRON,
            cronExpression = "* * * * *",
        )

        assertThat(draft.firstProblem(ScheduleLimits(minIntervalMinutes = 1440))).isNull()
    }

    @Test
    fun an_unusable_cron_box_is_caught_before_a_round_trip() {
        val draft = ScheduleDraft(
            name = "n",
            prompt = "p",
            agentId = "a",
            frequency = ScheduleFrequency.CRON,
            cronExpression = "every morning",
        )

        assertThat(draft.firstProblem(limits)).isEqualTo(ScheduleDraftProblem.CRON_SHAPE)
    }

    @Test
    fun a_required_project_blocks_save_only_when_the_user_must_choose_one() {
        val draft = ScheduleDraft(name = "n", prompt = "p", agentId = "a", chatProjectId = null)

        assertThat(draft.firstProblem(ScheduleLimits(requireProject = true)))
            .isEqualTo(ScheduleDraftProblem.PROJECT_REQUIRED)
        // Pinned by the operator: the server supplies the destination, so there is nothing unmet.
        assertThat(
            draft.firstProblem(ScheduleLimits(requireProject = true, projectId = "proj_pinned")),
        ).isNull()
    }

    @Test
    fun a_cron_schedule_loads_with_no_time_rather_than_an_invented_one() {
        // A cron row carries no hour or minute. Pre-filling the structured controls with 09:00
        // would show a time the user never chose, and switching to a structured cadence would
        // commit it. Blank instead, which blocks Save until a real one is entered.
        val draft = ScheduleDraft.from(cronSchedule).copy(frequency = ScheduleFrequency.WEEKLY)

        assertThat(draft.hourText).isEmpty()
        assertThat(draft.firstProblem(limits)).isEqualTo(ScheduleDraftProblem.TIME_REQUIRED)
    }

    @Test
    fun a_structured_schedule_loads_with_its_own_time() {
        val daily = cronSchedule.copy(
            cadence = ScheduleCadence.structured(ScheduleFrequency.DAILY, hour = 6, minute = 45),
        )

        val draft = ScheduleDraft.from(daily).copy(frequency = ScheduleFrequency.WEEKDAYS)

        assertThat(draft.hourText).isEqualTo("6")
        assertThat(draft.minuteText).isEqualTo("45")
    }

    @Test
    fun the_time_fields_can_be_cleared_and_retyped() {
        // Parsing per keystroke and discarding what does not parse makes the field un-clearable —
        // it snaps back under the cursor. Blank is a valid intermediate state that blocks Save.
        val draft = ScheduleDraft(name = "n", prompt = "p", agentId = "a", hourText = "")

        assertThat(draft.hour).isNull()
        assertThat(draft.firstProblem(limits)).isEqualTo(ScheduleDraftProblem.TIME_REQUIRED)
        assertThat(draft.copy(hourText = "07").hour).isEqualTo(7)
        assertThat(draft.copy(hourText = "99").hour).isNull()
    }

    @Test
    fun a_server_with_no_agents_says_so_rather_than_asking_for_one() {
        val draft = ScheduleDraft(name = "n", prompt = "p", agentId = "")

        assertThat(draft.firstProblem(limits, hasNoAgents = true))
            .isEqualTo(ScheduleDraftProblem.NO_AGENTS)
        assertThat(draft.firstProblem(limits, hasNoAgents = false))
            .isEqualTo(ScheduleDraftProblem.AGENT_REQUIRED)
    }

    @Test
    fun a_create_body_carries_the_idempotency_key_and_the_built_cadence() {
        val draft = ScheduleDraft(
            name = "  Morning digest  ",
            prompt = "  Summarise  ",
            agentId = "agent_abc",
            frequency = ScheduleFrequency.WEEKLY,
            hourText = "8",
            minuteText = "30",
            daysOfWeek = setOf(3, 1),
            timezone = "Europe/London",
        )

        val request = draft.toCreateRequest("req-1")

        assertThat(request.name).isEqualTo("Morning digest")
        assertThat(request.prompt).isEqualTo("Summarise")
        assertThat(request.clientRequestId).isEqualTo("req-1")
        assertThat(request.cadence.daysOfWeek).containsExactly(1, 3).inOrder()
        assertThat(request.cadence.expression).isNull()
    }
}

