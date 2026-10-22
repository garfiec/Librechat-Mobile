package com.garfiec.librechat.feature.skills.viewmodel

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.data.repository.SkillsRepository
import com.garfiec.librechat.core.data.repository.UserRepository
import com.garfiec.librechat.core.model.SkillSummary
import com.garfiec.librechat.core.model.User
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.model.response.SkillListResponse
import com.garfiec.librechat.feature.skills.SkillSaveNotices
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test

/** Each row's active state and ownership, and the fetches behind them. */
@OptIn(ExperimentalCoroutinesApi::class)
class SkillsListRowInputsTest {

    private val testDispatcher = StandardTestDispatcher()
    private val skillsRepository = mockk<SkillsRepository>(relaxed = true)
    private val roleRepository = mockk<RoleRepository>(relaxed = true)
    private val userRepository = mockk<UserRepository>(relaxed = true)
    private val configRepository = mockk<ConfigRepository>(relaxed = true)
    private val json = Json { ignoreUnknownKeys = true }

    private val me = "65f0c0ffee0000000000abcd"

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { roleRepository.userPermissions } returns MutableStateFlow(null)
        every { configRepository.startupConfig } returns MutableStateFlow<StartupConfig?>(null)
        coEvery { skillsRepository.getSkillStates() } returns Result.Success(emptyMap())
        coEvery { userRepository.getUser() } returns Result.Success(
            json.decodeFromString(User.serializer(), """{"id":"$me","email":"ada@example.com","role":"USER"}"""),
        )
    }

    @After
    fun teardown() = Dispatchers.resetMain()

    private fun vm() = SkillsListViewModel(skillsRepository, roleRepository, userRepository, configRepository, SkillSaveNotices())

    private fun summary(id: String) = SkillSummary(id = id, name = "n-$id")

    @Test
    fun `rows resolve the active state from the states map and ownership`() = runTest(testDispatcher) {
        val owned = SkillSummary(id = "own", name = "own", author = me)
        val shared = SkillSummary(id = "shr", name = "shr", author = "65f0c0ffee00000000002222")
        val sharedOn = SkillSummary(id = "on", name = "on", author = "65f0c0ffee00000000002222")
        coEvery { skillsRepository.listSkills(search = null, cursor = null) } returns
            Result.Success(SkillListResponse(skills = listOf(owned, shared, sharedOn)))
        coEvery { skillsRepository.getSkillStates() } returns Result.Success(mapOf("on" to true))
        val viewModel = vm()
        viewModel.loadFirstPage()
        advanceUntilIdle()

        val rows = viewModel.uiState.value.rows.associateBy { it.summary.id }
        assertThat(rows.getValue("own").isActive).isTrue()
        assertThat(rows.getValue("own").isOwnedByViewer).isTrue()
        assertThat(rows.getValue("shr").isActive).isFalse()
        assertThat(rows.getValue("shr").isOwnedByViewer).isFalse()
        assertThat(rows.getValue("on").isActive).isTrue()
        coVerify(exactly = 1) { skillsRepository.getSkillStates() }
    }

    @Test
    fun `a row's active state stays unknown until the states map loads`() = runTest(testDispatcher) {
        coEvery { skillsRepository.listSkills(search = null, cursor = null) } returns
            Result.Success(SkillListResponse(skills = listOf(summary("a"))))
        coEvery { skillsRepository.getSkillStates() } returns Result.Error(message = "boom")
        val viewModel = vm()
        viewModel.loadFirstPage()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.rows.single().isActive).isNull()
    }

    @Test
    fun `a viewer without an id is fetched again on the next load`() = runTest(testDispatcher) {
        coEvery { skillsRepository.listSkills(search = null, cursor = null) } returns
            Result.Success(SkillListResponse(skills = listOf(SkillSummary(id = "own", name = "own", author = me))))
        coEvery { userRepository.getUser() } returnsMany listOf(
            Result.Success(json.decodeFromString(User.serializer(), """{"email":"ada@example.com","role":"USER"}""")),
            Result.Success(json.decodeFromString(User.serializer(), """{"id":"$me","email":"ada@example.com","role":"USER"}""")),
        )
        val viewModel = vm()
        viewModel.loadFirstPage()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.rows.single().isActive).isNull()

        viewModel.refresh()
        advanceUntilIdle()

        val row = viewModel.uiState.value.rows.single()
        assertThat(row.isActive).isTrue()
        assertThat(row.isOwnedByViewer).isTrue()
        coVerify(exactly = 2) { userRepository.getUser() }
    }

    @Test
    fun `a search refetches the list only, not the states, the bits or the viewer`() = runTest(testDispatcher) {
        coEvery { skillsRepository.listSkills(search = any(), cursor = null) } returns
            Result.Success(SkillListResponse(skills = listOf(summary("a"))))
        val viewModel = vm()
        viewModel.loadFirstPage()
        advanceUntilIdle()

        viewModel.onSearchQueryChanged("pdf")
        advanceUntilIdle()
        viewModel.onSearchQueryChanged("pdf tables")
        advanceUntilIdle()

        coVerify(exactly = 1) { skillsRepository.listSkills(search = "pdf tables", cursor = null) }
        coVerify(exactly = 1) { skillsRepository.getSkillStates() }
        coVerify(exactly = 0) { skillsRepository.getSkillEffectivePermissions() }
        coVerify(exactly = 1) { userRepository.getUser() }
    }

    @Test
    fun `an older states fetch that lands last doesn't overwrite a newer one`() = runTest(testDispatcher) {
        coEvery { skillsRepository.listSkills(search = null, cursor = null) } returns
            Result.Success(SkillListResponse(skills = listOf(SkillSummary(id = "s", name = "s", author = "65f0c0ffee00000000002222"))))
        val older = CompletableDeferred<Result<Map<String, Boolean>>>()
        coEvery { skillsRepository.getSkillStates() } coAnswers { older.await() } andThen Result.Success(mapOf("s" to true))
        val viewModel = vm()
        viewModel.loadFirstPage()
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.rows.single().isActive).isTrue()

        older.complete(Result.Success(emptyMap()))
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.rows.single().isActive).isTrue()
    }
}
