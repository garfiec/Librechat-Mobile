package com.garfiec.librechat.feature.skills.viewmodel

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.data.repository.SkillsRepository
import com.garfiec.librechat.core.data.repository.UserRepository
import com.garfiec.librechat.core.model.SkillSummary
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.model.response.SkillListResponse
import com.garfiec.librechat.feature.skills.SkillSaveNotices
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/** The list's one-shot events while the screen isn't collecting them. */
@OptIn(ExperimentalCoroutinesApi::class)
class SkillsListEventsTest {

    private val testDispatcher = StandardTestDispatcher()
    private val skillsRepository = mockk<SkillsRepository>(relaxed = true)
    private val roleRepository = mockk<RoleRepository>(relaxed = true)
    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val configRepository = mockk<ConfigRepository>(relaxed = true)
    private val saveNotices = SkillSaveNotices()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { roleRepository.userPermissions } returns MutableStateFlow(null)
        every { configRepository.startupConfig } returns MutableStateFlow<StartupConfig?>(null)
        coEvery { skillsRepository.getSkillStates() } returns Result.Success(emptyMap())
        coEvery { userRepository.getUser() } returns Result.Error(message = "offline")
        coEvery { configRepository.getCategories() } returns Result.Success(emptyList())
    }

    @After
    fun teardown() = Dispatchers.resetMain()

    private fun vm() = SkillsListViewModel(skillsRepository, roleRepository, userRepository, configRepository, saveNotices)

    private fun summary(id: String) = SkillSummary(id = id, name = "n-$id")

    private fun TestScope.collectEvents(viewModel: SkillsListViewModel): List<SkillsListEvent> {
        val events = mutableListOf<SkillsListEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.toList(events) }
        return events
    }

    @Test
    fun `a failure queued off screen is dropped once the return reload lands`() = runTest(testDispatcher) {
        coEvery { skillsRepository.listSkills(search = null, cursor = null) } returns
            Result.Success(SkillListResponse(skills = listOf(summary("a"))))
        val viewModel = vm()
        viewModel.loadFirstPage()
        advanceUntilIdle()
        // Off screen: nothing collects, so the failure waits in the channel.
        coEvery { skillsRepository.listSkills(search = null, cursor = null) } returns Result.Error(message = "boom")
        viewModel.refresh()
        advanceUntilIdle()

        coEvery { skillsRepository.listSkills(search = null, cursor = null) } returns
            Result.Success(SkillListResponse(skills = listOf(summary("a"))))
        saveNotices.post("a", listOf("Heads up"))
        viewModel.refreshOnReturn()
        advanceUntilIdle()
        val events = collectEvents(viewModel)

        assertThat(events).containsExactly(SkillsListEvent.SaveWarnings(listOf("Heads up")))
    }
}
