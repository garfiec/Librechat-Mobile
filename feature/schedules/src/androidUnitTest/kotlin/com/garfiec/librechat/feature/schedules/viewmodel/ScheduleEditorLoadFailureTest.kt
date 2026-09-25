package com.garfiec.librechat.feature.schedules.viewmodel

import com.garfiec.librechat.core.common.result.ApiException
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
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * What happens when the row an Edit was opened on cannot be read.
 *
 * The defect this exists for is silent and irreversible from the app: a failed read leaves
 * `original` null while the editor stays in Edit mode over a blank draft, the first keystroke
 * clears the error banner, and Save then takes the CREATE branch — so the user ends up with two
 * recurring automations firing on the server instead of the one they meant to change. The 409
 * path is worse still, because a conflict is proof the row exists.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleEditorLoadFailureTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val scheduleRepository = mockk<ScheduleRepository>(relaxed = true)
    private val agentRepository = mockk<AgentRepository>(relaxed = true)
    private val projectRepository = mockk<ProjectRepository>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { scheduleRepository.listSchedules() } returns
            Result.Success(ScheduleListResponse())
        coEvery { agentRepository.getAgents() } returns
            Result.Success(listOf(Agent(id = "agent-1", name = "Agent")))
        coEvery { projectRepository.listProjects() } returns Result.Error(message = "no projects")
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun editor(scheduleId: String?) = ScheduleEditorViewModel(
        scheduleRepository = scheduleRepository,
        agentRepository = agentRepository,
        projectRepository = projectRepository,
        scheduleId = scheduleId,
    )

    private fun existing() = Schedule(
        id = SCHEDULE_ID,
        name = "Morning digest",
        prompt = "Summarise overnight mail",
        agentId = "agent-1",
        cadence = ScheduleCadence.structured(frequency = "daily", hour = 7, minute = 30),
        timezone = "UTC",
    )

    private fun fillInAValidDraft(viewModel: ScheduleEditorViewModel) {
        viewModel.update { it.copy(name = "Renamed", prompt = "Do the thing", agentId = "agent-1") }
    }

    @Test
    fun `a failed load cannot be saved as a new schedule`() = runTest {
        coEvery { scheduleRepository.getSchedule(SCHEDULE_ID) } returns
            Result.Error(message = "network")

        val viewModel = editor(SCHEDULE_ID)
        // The keystroke that clears the error banner, which is what made the state look recovered.
        fillInAValidDraft(viewModel)

        assertThat(viewModel.uiState.value.error).isNull()
        assertThat(viewModel.uiState.value.loadFailed).isTrue()
        assertThat(viewModel.uiState.value.canSave).isFalse()

        viewModel.save()

        coVerify(exactly = 0) { scheduleRepository.createSchedule(any()) }
        coVerify(exactly = 0) { scheduleRepository.updateSchedule(any(), any()) }
    }

    @Test
    fun `a conflict reload that fails cannot turn the fence into a duplicate`() = runTest {
        // A 409 proves the row exists. If the reload it forces then fails, the old code had the
        // editor in exactly the state that creates a second schedule.
        coEvery { scheduleRepository.getSchedule(SCHEDULE_ID) } returns Result.Success(existing())
        coEvery { scheduleRepository.updateSchedule(any(), any()) } returns Result.Error(
            exception = ApiException(statusCode = 409, message = "conflict"),
            message = "conflict",
        )

        val viewModel = editor(SCHEDULE_ID)
        viewModel.update { it.copy(name = "Renamed") }
        viewModel.save()
        assertThat(viewModel.uiState.value.hasConflict).isTrue()

        coEvery { scheduleRepository.getSchedule(SCHEDULE_ID) } returns
            Result.Error(message = "network")
        viewModel.reload()
        fillInAValidDraft(viewModel)
        viewModel.save()

        coVerify(exactly = 0) { scheduleRepository.createSchedule(any()) }
    }

    @Test
    fun `a failed load is recoverable rather than a dead form`() = runTest {
        coEvery { scheduleRepository.getSchedule(SCHEDULE_ID) } returns
            Result.Error(message = "network")
        val viewModel = editor(SCHEDULE_ID)
        assertThat(viewModel.uiState.value.canSave).isFalse()

        coEvery { scheduleRepository.getSchedule(SCHEDULE_ID) } returns Result.Success(existing())
        viewModel.reload()

        assertThat(viewModel.uiState.value.loadFailed).isFalse()
        assertThat(viewModel.uiState.value.draft.name).isEqualTo("Morning digest")
        assertThat(viewModel.uiState.value.canSave).isTrue()

        viewModel.save()
        coVerify(exactly = 1) { scheduleRepository.updateSchedule(eq(SCHEDULE_ID), any()) }
        coVerify(exactly = 0) { scheduleRepository.createSchedule(any()) }
    }

    @Test
    fun `a new schedule is unaffected by the guard`() = runTest {
        // The guard keys on the editor being in Edit mode, so a genuine create must still create.
        coEvery { scheduleRepository.createSchedule(any()) } returns Result.Success(existing())

        val viewModel = editor(scheduleId = null)
        fillInAValidDraft(viewModel)

        assertThat(viewModel.uiState.value.loadFailed).isFalse()
        viewModel.save()

        coVerify(exactly = 1) { scheduleRepository.createSchedule(any()) }
    }

    private companion object {
        const val SCHEDULE_ID = "sched-1"
    }
}
