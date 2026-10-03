package com.garfiec.librechat.feature.schedules.viewmodel

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.AgentRepository
import com.garfiec.librechat.core.data.repository.ProjectRepository
import com.garfiec.librechat.core.data.repository.ScheduleRepository
import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.schedule.Schedule
import com.garfiec.librechat.core.model.schedule.ScheduleCadence
import com.garfiec.librechat.core.model.schedule.ScheduleListResponse
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/** Back from the schedule editor asks before throwing edits away — and only then. */
@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleEditorUnsavedChangesTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val scheduleRepository = mockk<ScheduleRepository>(relaxed = true)
    private val agentRepository = mockk<AgentRepository>(relaxed = true)
    private val projectRepository = mockk<ProjectRepository>(relaxed = true)

    private val schedule = Schedule(
        id = "sched-1",
        name = "Morning digest",
        prompt = "Summarise overnight mail",
        agentId = "agent-1",
        cadence = ScheduleCadence.structured(frequency = "daily", hour = 7, minute = 30),
        timezone = "UTC",
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { scheduleRepository.listSchedules() } returns Result.Success(ScheduleListResponse())
        coEvery { scheduleRepository.getSchedule(schedule.id) } returns Result.Success(schedule)
        coEvery { agentRepository.getAgents() } returns
            Result.Success(listOf(Agent(id = "agent-1", name = "Agent"), Agent(id = "agent-2", name = "Other")))
        coEvery { projectRepository.listProjects() } returns Result.Error(message = "no projects")
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun editor(scheduleId: String? = schedule.id) = ScheduleEditorViewModel(
        scheduleRepository = scheduleRepository,
        agentRepository = agentRepository,
        projectRepository = projectRepository,
        scheduleId = scheduleId,
    )

    @Test
    fun `a freshly loaded schedule leaves without asking`() = runTest {
        val viewModel = editor()

        assertThat(viewModel.uiState.value.hasUnsavedChanges).isFalse()
        viewModel.onBackRequested()

        assertThat(viewModel.uiState.value.showDiscardConfirm).isFalse()
        assertThat(viewModel.uiState.value.exitRequested).isTrue()
    }

    @Test
    fun `an edit asks before leaving, and discarding then leaves`() = runTest {
        val viewModel = editor()

        viewModel.update { it.copy(prompt = "Summarise overnight mail and chats") }
        viewModel.onBackRequested()
        assertThat(viewModel.uiState.value.showDiscardConfirm).isTrue()
        assertThat(viewModel.uiState.value.exitRequested).isFalse()

        viewModel.discardChanges()
        assertThat(viewModel.uiState.value.showDiscardConfirm).isFalse()
        assertThat(viewModel.uiState.value.exitRequested).isTrue()
    }

    @Test
    fun `cancelling the prompt stays in the editor`() = runTest {
        val viewModel = editor()
        viewModel.update { it.copy(enabled = false) }
        viewModel.onBackRequested()

        viewModel.dismissDiscardConfirmation()

        assertThat(viewModel.uiState.value.showDiscardConfirm).isFalse()
        assertThat(viewModel.uiState.value.exitRequested).isFalse()
        assertThat(viewModel.uiState.value.draft.enabled).isFalse()
    }

    @Test
    fun `undoing an edit by hand is clean again`() = runTest {
        val viewModel = editor()

        viewModel.update { it.copy(agentId = "agent-2") }
        assertThat(viewModel.uiState.value.hasUnsavedChanges).isTrue()
        viewModel.update { it.copy(agentId = "agent-1") }

        assertThat(viewModel.uiState.value.hasUnsavedChanges).isFalse()
    }

    /** The blank form is filled in at load (timezone, first agent) — those defaults are not edits. */
    @Test
    fun `an untouched create form leaves without asking, a named one asks`() = runTest {
        val viewModel = editor(scheduleId = null)

        assertThat(viewModel.uiState.value.draft.agentId).isEqualTo("agent-1")
        assertThat(viewModel.uiState.value.draft.timezone).isNotEmpty()
        assertThat(viewModel.uiState.value.hasUnsavedChanges).isFalse()

        viewModel.update { it.copy(name = "Weekly report") }
        assertThat(viewModel.uiState.value.hasUnsavedChanges).isTrue()
    }

    @Test
    fun `reloading discards the edit and starts clean`() = runTest {
        val viewModel = editor()
        viewModel.update { it.copy(name = "Renamed") }

        viewModel.reload()

        assertThat(viewModel.uiState.value.draft.name).isEqualTo("Morning digest")
        assertThat(viewModel.uiState.value.hasUnsavedChanges).isFalse()
    }

    /** A save navigates when it lands; leaving first would let it pop the screen behind this one. */
    @Test
    fun `back is held while a save is in flight`() = runTest {
        coEvery { scheduleRepository.updateSchedule(schedule.id, any()) } coAnswers { awaitCancellation() }
        val viewModel = editor()
        viewModel.update { it.copy(name = "Renamed") }
        viewModel.save()
        assertThat(viewModel.uiState.value.isSaving).isTrue()

        viewModel.onBackRequested()

        assertThat(viewModel.uiState.value.showDiscardConfirm).isFalse()
        assertThat(viewModel.uiState.value.exitRequested).isFalse()
    }
}
