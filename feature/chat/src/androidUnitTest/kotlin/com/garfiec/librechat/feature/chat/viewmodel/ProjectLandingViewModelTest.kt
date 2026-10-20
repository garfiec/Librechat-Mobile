package com.garfiec.librechat.feature.chat.viewmodel

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ProjectRepository
import com.garfiec.librechat.core.model.ChatProject
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectLandingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val projectRepository = mockk<ProjectRepository>(relaxed = true)
    private val revision = MutableStateFlow(0L)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { projectRepository.revision } returns revision
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `opening asks for nothing, since the route's name is fresh`() = runTest(testDispatcher) {
        val vm = ProjectLandingViewModel("p1", "Thesis", projectRepository)
        advanceUntilIdle()

        assertThat(vm.state.value).isEqualTo(ProjectLandingState("Thesis"))
        coVerify(exactly = 0) { projectRepository.getProject(any()) }
    }

    @Test
    fun `a rename elsewhere renames the screen`() = runTest(testDispatcher) {
        coEvery { projectRepository.getProject("p1") } returns Result.Success(ChatProject(id = "p1", name = "Dissertation"))
        val vm = ProjectLandingViewModel("p1", "Thesis", projectRepository)
        advanceUntilIdle()

        revision.value = 1
        advanceUntilIdle()

        assertThat(vm.state.value).isEqualTo(ProjectLandingState("Dissertation"))
    }

    @Test
    fun `a project deleted elsewhere closes the screen`() = runTest(testDispatcher) {
        coEvery { projectRepository.getProject("p1") } returns
            Result.Error(ApiException(statusCode = 404, message = "Project not found"))
        val vm = ProjectLandingViewModel("p1", "Thesis", projectRepository)
        advanceUntilIdle()

        revision.value = 1
        advanceUntilIdle()

        assertThat(vm.state.value.deleted).isTrue()
    }

    @Test
    fun `any other failure keeps the screen as it was`() = runTest(testDispatcher) {
        coEvery { projectRepository.getProject("p1") } returns Result.Error(message = "offline")
        val vm = ProjectLandingViewModel("p1", "Thesis", projectRepository)
        advanceUntilIdle()

        revision.value = 1
        advanceUntilIdle()

        assertThat(vm.state.value).isEqualTo(ProjectLandingState("Thesis"))
    }
}
