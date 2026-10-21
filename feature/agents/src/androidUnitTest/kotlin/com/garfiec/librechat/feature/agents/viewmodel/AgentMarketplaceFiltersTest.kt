package com.garfiec.librechat.feature.agents.viewmodel

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.AgentCategory
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Chips, search and the role gates: what each read asks the server for. */
@OptIn(ExperimentalCoroutinesApi::class)
class AgentMarketplaceFiltersTest : AgentMarketplaceViewModelTestBase() {

    // --- Default chip ---

    @Test
    fun `the default chip is Top picks when the server has promoted agents, read once`() = runTest {
        coEvery { agentRepository.getAgentCategories() } returns Result.Success(categoriesWithSentinels)

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.selectedChip).isEqualTo(AgentChip.TopPicks)
        coVerify(exactly = 1) { agentRepository.getAgentsPaginated(any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 1) {
            agentRepository.getAgentsPaginated(20, null, search = null, category = null, promoted = true, null)
        }
    }

    @Test
    fun `the default chip is All when nothing is promoted`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.selectedChip).isEqualTo(AgentChip.All)
        coVerify(exactly = 1) {
            agentRepository.getAgentsPaginated(20, null, search = null, category = null, promoted = false, null)
        }
    }

    @Test
    fun `nothing is read before the categories are in`() = runTest {
        val categories = CompletableDeferred<Result<List<AgentCategory>>>()
        coEvery { agentRepository.getAgentCategories() } coAnswers { categories.await() }

        viewModel = createViewModel()
        advanceUntilIdle()
        coVerify(exactly = 0) { agentRepository.getAgentsPaginated(any(), any(), any(), any(), any(), any()) }
        assertThat(viewModel.uiState.value.isLoading).isTrue()

        categories.complete(Result.Success(categoriesWithSentinels))
        advanceUntilIdle()

        coVerify(exactly = 1) { agentRepository.getAgentsPaginated(any(), any(), any(), any(), any(), any()) }
        assertThat(viewModel.uiState.value.selectedChip).isEqualTo(AgentChip.TopPicks)
    }

    @Test
    fun `a chip tapped before the first read is the one read`() = runTest {
        val role = CompletableDeferred<UserRolePermissions?>()
        coEvery { permissionGate.awaitRole() } coAnswers { role.await() }
        coEvery { agentRepository.getAgentCategories() } returns Result.Success(categoriesWithSentinels)

        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onChipSelected(AgentChip.Category("writing"))
        advanceUntilIdle()
        coVerify(exactly = 0) { agentRepository.getAgentsPaginated(any(), any(), any(), any(), any(), any()) }

        role.complete(null)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.selectedChip).isEqualTo(AgentChip.Category("writing"))
        coVerify(exactly = 1) { agentRepository.getAgentsPaginated(any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 1) { agentRepository.getAgentsPaginated(20, null, null, "writing", false, null) }
    }

    // --- Role gates ---

    @Test
    fun `the first resume before the role gate reads nothing`() = runTest {
        val role = CompletableDeferred<UserRolePermissions?>()
        coEvery { permissionGate.awaitRole() } coAnswers { role.await() }

        viewModel = createViewModel()
        advanceUntilIdle()
        // ON_RESUME fires on first composition, while the role is still being awaited.
        viewModel.refreshOnReturn()
        advanceUntilIdle()
        coVerify(exactly = 0) { agentRepository.getAgentsPaginated(any(), any(), any(), any(), any(), any()) }

        role.complete(null)
        advanceUntilIdle()

        coVerify(exactly = 1) { agentRepository.getAgentsPaginated(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a role without MARKETPLACE use reads nothing`() = runTest {
        val role = UserRolePermissions(name = "USER", permissions = mapOf("MARKETPLACE" to mapOf("USE" to false)))
        coEvery { permissionGate.awaitRole() } returns role
        roleFlow.value = role

        viewModel = createViewModel()
        advanceUntilIdle()

        coVerify(exactly = 0) { agentRepository.getAgentsPaginated(any(), any(), any(), any(), any(), any()) }
        val state = viewModel.uiState.value
        assertThat(state.canBrowse).isFalse()
        assertThat(state.isLoading).isFalse()
    }

    @Test
    fun `a role without AGENTS use reads nothing`() = runTest {
        val role = UserRolePermissions(name = "USER", permissions = mapOf("AGENTS" to mapOf("USE" to false)))
        coEvery { permissionGate.awaitRole() } returns role
        roleFlow.value = role

        viewModel = createViewModel()
        advanceUntilIdle()

        coVerify(exactly = 0) { agentRepository.getAgentsPaginated(any(), any(), any(), any(), any(), any()) }
        assertThat(viewModel.uiState.value.canBrowse).isFalse()
    }

    // --- Chips ---

    @Test
    fun `the chip row is Top picks, All, Editable, then the real categories`() = runTest {
        coEvery { agentRepository.getAgentCategories() } returns Result.Success(categoriesWithSentinels)

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.chips).containsExactly(
            AgentChip.TopPicks,
            AgentChip.All,
            AgentChip.Editable,
            AgentChip.Category("coding"),
            AgentChip.Category("writing"),
            AgentChip.Category("education"),
        ).inOrder()
    }

    @Test
    fun `Editable is offered only with AGENTS create`() = runTest {
        roleFlow.value = UserRolePermissions(name = "USER", permissions = mapOf("AGENTS" to mapOf("CREATE" to false)))

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.chips).doesNotContain(AgentChip.Editable)
    }

    @Test
    fun `Editable asks for the agents the user can edit`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onChipSelected(AgentChip.Editable)
        advanceUntilIdle()

        coVerify(exactly = 1) { agentRepository.getAgentsPaginated(20, null, null, null, false, requiredPermission = 2) }
    }

    @Test
    fun `searching under Editable keeps the edit filter`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onChipSelected(AgentChip.Editable)
        advanceUntilIdle()

        viewModel.onSearchQueryChanged("math")
        advanceUntilIdle()

        coVerify(exactly = 1) { agentRepository.getAgentsPaginated(20, null, "math", null, false, requiredPermission = 2) }
    }

    @Test
    fun `losing AGENTS create while on Editable falls back to All`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onChipSelected(AgentChip.Editable)
        advanceUntilIdle()

        roleFlow.value = UserRolePermissions(name = "USER", permissions = mapOf("AGENTS" to mapOf("CREATE" to false)))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.selectedChip).isEqualTo(AgentChip.All)
        assertThat(viewModel.uiState.value.shownQuery).isEqualTo(AgentListQuery(AgentChip.All, ""))
    }

    @Test
    fun `onChipSelected sets the chip and reloads`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onChipSelected(AgentChip.Category("coding"))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.selectedChip).isEqualTo(AgentChip.Category("coding"))
        coVerify(exactly = 1) { agentRepository.getAgentsPaginated(20, null, null, "coding", false, null) }
    }

    @Test
    fun `tapping the selected chip does nothing`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onChipSelected(AgentChip.Category("coding"))
        advanceUntilIdle()

        viewModel.onChipSelected(AgentChip.Category("coding"))
        advanceUntilIdle()

        // One chip is always selected: no toggle back to none, and no second read.
        assertThat(viewModel.uiState.value.selectedChip).isEqualTo(AgentChip.Category("coding"))
        coVerify(exactly = 1) { agentRepository.getAgentsPaginated(any(), any(), any(), "coding", any(), any()) }
        coVerify(exactly = 2) { agentRepository.getAgentsPaginated(any(), any(), any(), any(), any(), any()) }
    }

    // --- The categories route's two sentinels (upstream AgentGrid.tsx queryParams) ---

    @Test
    fun `the promoted chip asks for promoted agents, not a promoted category`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onChipSelected(AgentChip.TopPicks)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            agentRepository.getAgentsPaginated(20, null, search = null, category = null, promoted = true, null)
        }
        coVerify(exactly = 0) { agentRepository.getAgentsPaginated(any(), any(), any(), "promoted", any(), any()) }
    }

    @Test
    fun `the all chip sends no filter`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onChipSelected(AgentChip.Category("coding"))
        advanceUntilIdle()

        viewModel.onChipSelected(AgentChip.All)
        advanceUntilIdle()

        // The init load and the All load are the same unfiltered read.
        coVerify(exactly = 2) {
            agentRepository.getAgentsPaginated(20, null, search = null, category = null, promoted = false, null)
        }
        coVerify(exactly = 0) { agentRepository.getAgentsPaginated(any(), any(), any(), "all", any(), any()) }
    }

    @Test
    fun `searching under the promoted chip searches every agent`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onChipSelected(AgentChip.TopPicks)
        advanceUntilIdle()

        viewModel.onSearchQueryChanged("math")
        advanceUntilIdle()

        coVerify(exactly = 1) {
            agentRepository.getAgentsPaginated(20, null, search = "math", category = null, promoted = false, null)
        }
    }

    @Test
    fun `searching under the all chip searches every agent`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onSearchQueryChanged("math")
        advanceUntilIdle()

        coVerify(exactly = 1) {
            agentRepository.getAgentsPaginated(20, null, search = "math", category = null, promoted = false, null)
        }
    }

    @Test
    fun `searching under a real category keeps the category`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onChipSelected(AgentChip.Category("education"))
        advanceUntilIdle()

        viewModel.onSearchQueryChanged("math")
        advanceUntilIdle()

        coVerify(exactly = 1) {
            agentRepository.getAgentsPaginated(20, null, search = "math", category = "education", promoted = false, null)
        }
    }

    @Test
    fun `load more under the promoted chip keeps the promoted filter`() = runTest {
        stubPage("c1") returns page(listOf(Agent(id = "agent-4")))
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onChipSelected(AgentChip.TopPicks)
        advanceUntilIdle()

        viewModel.loadMore()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            agentRepository.getAgentsPaginated(20, "c1", search = null, category = null, promoted = true, null)
        }
    }

    // --- Search ---

    @Test
    fun `onSearchQueryChanged updates query and reloads after the debounce`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onSearchQueryChanged("coding")
        advanceTimeBy(AgentMarketplaceViewModel.SEARCH_DEBOUNCE_MS - 1)
        runCurrent()
        coVerify(exactly = 0) { agentRepository.getAgentsPaginated(any(), any(), "coding", any(), any(), any()) }

        advanceUntilIdle()
        assertThat(viewModel.uiState.value.searchQuery).isEqualTo("coding")
        assertThat(viewModel.uiState.value.shownQuery).isEqualTo(AgentListQuery(AgentChip.All, "coding"))
        coVerify(exactly = 1) { agentRepository.getAgentsPaginated(any(), any(), "coding", any(), any(), any()) }
    }

    @Test
    fun `the search is read trimmed, and a trailing space doesn't read again`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onSearchQueryChanged("  math")
        advanceUntilIdle()
        viewModel.onSearchQueryChanged("  math ")
        advanceUntilIdle()

        coVerify(exactly = 1) { agentRepository.getAgentsPaginated(any(), any(), "math", any(), any(), any()) }
        assertThat(viewModel.uiState.value.searchQuery).isEqualTo("  math ")
    }

    @Test
    fun `a slow answer for an earlier search never replaces a later one`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        coEvery { agentRepository.getAgentsPaginated(any(), null, "old", any(), any(), any()) } coAnswers {
            delay(5_000)
            page(listOf(Agent(id = "old-hit")))
        }
        coEvery { agentRepository.getAgentsPaginated(any(), null, "new", any(), any(), any()) } returns
            page(listOf(Agent(id = "new-hit")))

        viewModel.onSearchQueryChanged("old")
        advanceTimeBy(AgentMarketplaceViewModel.SEARCH_DEBOUNCE_MS + 1)
        runCurrent()
        viewModel.onSearchQueryChanged("new")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.agents.map { it.id }).containsExactly("new-hit")
        assertThat(viewModel.uiState.value.shownQuery).isEqualTo(AgentListQuery(AgentChip.All, "new"))
    }

    @Test
    fun `searchAllAgents keeps the search and drops the category`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onChipSelected(AgentChip.Category("education"))
        viewModel.onSearchQueryChanged("payroll")
        advanceUntilIdle()

        viewModel.searchAllAgents()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.selectedChip).isEqualTo(AgentChip.All)
        assertThat(viewModel.uiState.value.searchQuery).isEqualTo("payroll")
        coVerify(exactly = 1) { agentRepository.getAgentsPaginated(20, null, "payroll", null, false, null) }
    }

    @Test
    fun `showAllAgents clears the search and selects All`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onChipSelected(AgentChip.Category("education"))
        viewModel.onSearchQueryChanged("payroll")
        advanceUntilIdle()

        viewModel.showAllAgents()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.selectedChip).isEqualTo(AgentChip.All)
        assertThat(viewModel.uiState.value.searchQuery).isEmpty()
        assertThat(viewModel.uiState.value.shownQuery).isEqualTo(AgentListQuery(AgentChip.All, ""))
    }
}
