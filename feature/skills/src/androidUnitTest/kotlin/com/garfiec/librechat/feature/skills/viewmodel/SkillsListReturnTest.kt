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

/** A return to the list while a fetch sent before the user left is still in flight, and the search text sent. */
@OptIn(ExperimentalCoroutinesApi::class)
class SkillsListReturnTest {

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
    fun `a return while a page is in flight rereads the states and asks for the page again`() = runTest(testDispatcher) {
        val shared = SkillSummary(id = "shr", name = "shr", author = "65f0c0ffee00000000002222")
        coEvery { skillsRepository.listSkills(search = null, cursor = null) } returns
            Result.Success(SkillListResponse(skills = listOf(shared), hasMore = true, after = "cur1"))
        coEvery { skillsRepository.getSkillStates() } returns Result.Success(mapOf("shr" to true))
        val viewModel = vm()
        viewModel.refreshOnReturn()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.rows.single().isActive).isTrue()

        // A page starts, then the user opens the skill, switches it off and comes back first.
        val stalePage = CompletableDeferred<Result<SkillListResponse>>()
        coEvery { skillsRepository.listSkills(search = null, cursor = "cur1") } coAnswers { stalePage.await() } andThen
            Result.Success(SkillListResponse(skills = listOf(summary("b1")), hasMore = false, after = null))
        viewModel.loadMore()
        advanceUntilIdle()
        coEvery { skillsRepository.getSkillStates() } returns Result.Success(mapOf("shr" to false))
        viewModel.refreshOnReturn()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.rows.first().isActive).isFalse()
        assertThat(viewModel.uiState.value.skills.map { it.id }).containsExactly("shr", "b1").inOrder()
        stalePage.complete(Result.Success(SkillListResponse(skills = listOf(summary("stale")))))
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.skills.map { it.id }).containsExactly("shr", "b1").inOrder()
    }

    @Test
    fun `a return while a reload is in flight replaces it, so a skill created meanwhile shows`() = runTest(testDispatcher) {
        coEvery { skillsRepository.listSkills(search = null, cursor = null) } returns
            Result.Success(SkillListResponse(skills = listOf(summary("a"))))
        val viewModel = vm()
        viewModel.refreshOnReturn()
        advanceUntilIdle()

        val staleReload = CompletableDeferred<Result<SkillListResponse>>()
        coEvery { skillsRepository.listSkills(search = null, cursor = null) } coAnswers { staleReload.await() } andThen
            Result.Success(SkillListResponse(skills = listOf(summary("a"), summary("new"))))
        viewModel.refresh()
        advanceUntilIdle()
        viewModel.refreshOnReturn()
        // Restarted as a pull-to-refresh: the indicator the user started stays.
        assertThat(viewModel.uiState.value.isRefreshing).isTrue()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.skills.map { it.id }).containsExactly("a", "new").inOrder()

        staleReload.complete(Result.Success(SkillListResponse(skills = listOf(summary("a")))))
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.skills.map { it.id }).containsExactly("a", "new").inOrder()
        assertThat(viewModel.uiState.value.isRefreshing).isFalse()
    }

    @Test
    fun `a search goes out trimmed`() = runTest(testDispatcher) {
        coEvery { skillsRepository.listSkills(search = any(), cursor = null) } returns
            Result.Success(SkillListResponse(skills = listOf(summary("pdf"))))
        val viewModel = vm()
        viewModel.refreshOnReturn()
        advanceUntilIdle()

        viewModel.onSearchQueryChanged("pdf ")
        advanceUntilIdle()

        coVerify(exactly = 1) { skillsRepository.listSkills(search = "pdf", cursor = null) }
        coVerify(exactly = 0) { skillsRepository.listSkills(search = "pdf ", cursor = null) }
        assertThat(viewModel.uiState.value.searchQuery).isEqualTo("pdf ")
    }
}
