package com.garfiec.librechat.feature.conversations.viewmodel

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ProjectRepository
import com.garfiec.librechat.core.model.ChatProject
import com.garfiec.librechat.core.model.config.ChatProjectsConfig
import com.garfiec.librechat.core.model.config.StartupConfig
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectInstructionsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val projectRepository = mockk<ProjectRepository>(relaxed = true)
    private val startupConfig = MutableStateFlow<StartupConfig?>(StartupConfig())

    private fun viewModel() = ProjectInstructionsViewModel(
        projectId = "p1",
        projectRepository = projectRepository,
        configRepository = mockk(relaxed = true) { every { startupConfig } returns this@ProjectInstructionsViewModelTest.startupConfig },
    )

    private fun project(text: String?, revision: Int) =
        Result.Success(ChatProject(id = "p1", name = "P", instructions = text, contextRevision = revision))

    private val conflict = Result.Error(
        ApiException(409, "x", body = """{"message":"Project was changed by another session","code":"CHAT_PROJECT_CONFLICT"}"""),
    )

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        coEvery { projectRepository.getProject("p1") } returns project("Be brief", 4)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `save is off until the draft differs and sends the trimmed text with the held revision`() = runTest(dispatcher) {
        coEvery { projectRepository.updateInstructions("p1", "Be kind", 4) } returns project("Be kind", 5)
        val vm = viewModel()
        advanceUntilIdle()
        assertThat(vm.uiState.value.canSave).isFalse()

        vm.onDraftChange("Be brief  ")
        assertThat(vm.uiState.value.canSave).isFalse()

        vm.onDraftChange("  Be kind ")
        vm.save()
        advanceUntilIdle()

        coVerify { projectRepository.updateInstructions("p1", "Be kind", 4) }
        assertThat(vm.uiState.value.exitRequested).isTrue()
    }

    @Test
    fun `a conflict keeps the draft, reloads the revision and offers theirs`() = runTest(dispatcher) {
        coEvery { projectRepository.updateInstructions("p1", "Mine", 4) } returns conflict
        val vm = viewModel()
        val events = mutableListOf<ProjectInstructionsEvent>()
        val collector = launch { vm.events.collect { events += it } }
        advanceUntilIdle()
        coEvery { projectRepository.getProject("p1") } returns project("Theirs", 7)

        vm.onDraftChange("Mine")
        vm.save()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertThat(state.draft).isEqualTo("Mine")
        assertThat(state.revision).isEqualTo(7)
        assertThat(state.theirs).isEqualTo("Theirs")
        assertThat(state.canSave).isTrue()
        assertThat(state.exitRequested).isFalse()
        assertThat(events).containsExactly(ProjectInstructionsEvent.Conflict)
        collector.cancel()

        coEvery { projectRepository.updateInstructions("p1", "Mine", 7) } returns project("Mine", 8)
        vm.save()
        advanceUntilIdle()
        assertThat(vm.uiState.value.exitRequested).isTrue()
    }

    @Test
    fun `use theirs replaces the draft and leaves the editor clean`() = runTest(dispatcher) {
        coEvery { projectRepository.updateInstructions(any(), any(), any()) } returns conflict
        val vm = viewModel()
        advanceUntilIdle()
        coEvery { projectRepository.getProject("p1") } returns project("Theirs", 7)
        vm.onDraftChange("Mine")
        vm.save()
        advanceUntilIdle()

        vm.viewTheirs()
        assertThat(vm.uiState.value.showTheirs).isTrue()
        vm.useTheirs()

        assertThat(vm.uiState.value.draft).isEqualTo("Theirs")
        assertThat(vm.uiState.value.isDirty).isFalse()
        assertThat(vm.uiState.value.showTheirs).isFalse()
    }

    @Test
    fun `a 404 on save closes the editor`() = runTest(dispatcher) {
        coEvery { projectRepository.updateInstructions(any(), any(), any()) } returns
            Result.Error(ApiException(404, "Project not found"))
        val vm = viewModel()
        advanceUntilIdle()
        vm.onDraftChange("x")
        vm.save()
        advanceUntilIdle()
        assertThat(vm.uiState.value.exitRequested).isTrue()
    }

    @Test
    fun `any other failure keeps the draft and reports it`() = runTest(dispatcher) {
        coEvery { projectRepository.updateInstructions(any(), any(), any()) } returns Result.Error(ApiException(400, "x"))
        val vm = viewModel()
        val events = mutableListOf<ProjectInstructionsEvent>()
        val collector = launch { vm.events.collect { events += it } }
        advanceUntilIdle()
        vm.onDraftChange("x")
        vm.save()
        advanceUntilIdle()
        assertThat(vm.uiState.value.draft).isEqualTo("x")
        assertThat(vm.uiState.value.exitRequested).isFalse()
        assertThat(events).containsExactly(ProjectInstructionsEvent.SaveFailed)
        collector.cancel()
    }

    @Test
    fun `the draft stops at the configured limit`() = runTest(dispatcher) {
        startupConfig.value = StartupConfig(projects = ChatProjectsConfig(maxInstructionsLength = 5))
        val vm = viewModel()
        advanceUntilIdle()
        vm.onDraftChange("1234567")
        assertThat(vm.uiState.value.draft).isEqualTo("12345")
    }

    @Test
    fun `leaving asks first while dirty and is ignored while saving`() = runTest(dispatcher) {
        coEvery { projectRepository.updateInstructions(any(), any(), any()) } coAnswers { kotlinx.coroutines.awaitCancellation() }
        val vm = viewModel()
        advanceUntilIdle()
        vm.onBackRequested()
        assertThat(vm.uiState.value.exitRequested).isTrue()

        val dirty = viewModel()
        advanceUntilIdle()
        dirty.onDraftChange("changed")
        dirty.onBackRequested()
        assertThat(dirty.uiState.value.discardPrompt).isTrue()
        dirty.dismissDiscard()

        dirty.save()
        advanceUntilIdle()
        dirty.onBackRequested()
        assertThat(dirty.uiState.value.discardPrompt).isFalse()
        assertThat(dirty.uiState.value.exitRequested).isFalse()
    }
}
