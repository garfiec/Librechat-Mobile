package com.garfiec.librechat.feature.agents.viewmodel

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.ServerDataStore
import com.garfiec.librechat.core.data.repository.AgentRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.data.util.PermissionGate
import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.AgentCategory
import com.garfiec.librechat.core.model.PaginatedAgents
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
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
class AgentMarketplaceViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val agentRepository = mockk<AgentRepository>(relaxed = true)
    private val serverDataStore = mockk<ServerDataStore>(relaxed = true)
    private val roleRepository = mockk<RoleRepository>(relaxed = true)
    private val permissionGate = mockk<PermissionGate>(relaxed = true)

    private lateinit var viewModel: AgentMarketplaceViewModel

    private val testAgents = listOf(
        Agent(id = "agent-1", name = "Coding Assistant", description = "Helps with code", category = "coding"),
        Agent(id = "agent-2", name = "Writer", description = "Creative writing", category = "writing"),
        Agent(id = "agent-3", name = "Math Tutor", description = "Teaches math", category = "education"),
    )

    private val testCategories = listOf(
        AgentCategory(value = "coding", label = "Coding", count = 5),
        AgentCategory(value = "writing", label = "Writing", count = 3),
        AgentCategory(value = "education", label = "Education", count = 2),
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        // Permissive-by-null StateFlow for role repository so the collector leaves
        // flags at their default `true`. awaitRole() returns null (no timeout needed
        // in tests — the suspend returns null immediately), so the gated loadAgents()
        // path fires via `?: != false` permissive.
        every { roleRepository.userPermissions } returns MutableStateFlow(null)
        coEvery { permissionGate.awaitRole() } returns null
        every { serverDataStore.getBaseUrl() } returns "https://chat.example.com"
        stubPage(null) returns page(testAgents, next = "c1")
        coEvery { agentRepository.getAgentCategories() } returns Result.Success(testCategories)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Stubs the page read by [cursor] (null = the first page), whatever the filter. */
    private fun stubPage(cursor: String?) = coEvery {
        agentRepository.getAgentsPaginated(
            limit = any(),
            cursor = cursor,
            search = any(),
            category = any(),
            promoted = any(),
            requiredPermission = any(),
        )
    }

    /** One page; [next] is its `after`, and there is more exactly when there is one. */
    private fun page(agents: List<Agent>, next: String? = null): Result<PaginatedAgents> =
        Result.Success(PaginatedAgents(agents = agents, hasMore = next != null, nextCursor = next))

    private fun createViewModel() = AgentMarketplaceViewModel(
        agentRepository = agentRepository,
        serverDataStore = serverDataStore,
        roleRepository = roleRepository,
        permissionGate = permissionGate,
    )

    @Test
    fun `initial state loads agents`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.agents).hasSize(3)
        assertThat(state.filteredAgents).hasSize(3)
        assertThat(state.isLoading).isFalse()
        assertThat(state.hasMore).isTrue()
        assertThat(state.nextCursor).isEqualTo("c1")
    }

    @Test
    fun `initial state loads categories`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.categories).hasSize(3)
        assertThat(state.categories.map { it.value }).containsExactly("coding", "writing", "education")
    }

    @Test
    fun `agents display data has correct names`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val names = viewModel.uiState.value.agents.map { it.name }
        assertThat(names).containsExactly("Coding Assistant", "Writer", "Math Tutor")
    }

    @Test
    fun `loadAgents error surfaces error message`() = runTest {
        stubPage(null) returns Result.Error(message = "Network error")

        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.error).isEqualTo("Network error")
        assertThat(state.isLoading).isFalse()
    }

    @Test
    fun `loadMore fetches next page and appends agents`() = runTest {
        val page2Agents = listOf(
            Agent(id = "agent-4", name = "Translator", description = "Translates text"),
        )
        stubPage("c1") returns page(page2Agents)

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.loadMore()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.agents).hasSize(4)
        assertThat(state.nextCursor).isNull()
        assertThat(state.hasMore).isFalse()
        assertThat(state.isLoadingMore).isFalse()
    }

    @Test
    fun `loadMore reads by the previous page's after`() = runTest {
        stubPage("c1") returns page(listOf(Agent(id = "agent-4")), next = "c2")
        stubPage("c2") returns page(listOf(Agent(id = "agent-5")))

        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()

        coVerifyOrder {
            agentRepository.getAgentsPaginated(20, null, null, null, false, null)
            agentRepository.getAgentsPaginated(20, "c1", null, null, false, null)
            agentRepository.getAgentsPaginated(20, "c2", null, null, false, null)
        }
        assertThat(viewModel.uiState.value.agents.map { it.id })
            .containsExactly("agent-1", "agent-2", "agent-3", "agent-4", "agent-5").inOrder()
        assertThat(viewModel.uiState.value.hasMore).isFalse()
    }

    @Test
    fun `a filter change reads from the first page again`() = runTest {
        stubPage("c1") returns page(listOf(Agent(id = "agent-4")), next = "c2")

        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.nextCursor).isEqualTo("c2")

        viewModel.onCategorySelected("coding")
        advanceUntilIdle()

        coVerify(exactly = 1) {
            agentRepository.getAgentsPaginated(any(), cursor = null, any(), category = "coding", any(), any())
        }
        coVerify(exactly = 0) {
            agentRepository.getAgentsPaginated(any(), cursor = "c2", any(), any(), any(), any())
        }
        assertThat(viewModel.uiState.value.nextCursor).isEqualTo("c1")
    }

    @Test
    fun `loadMore without a cursor reads nothing`() = runTest {
        // has_more with no `after` can only re-read the first page; the repository reports it as
        // the end, but a page that says more without a cursor must still not be re-read.
        stubPage(null) returns Result.Success(PaginatedAgents(testAgents, hasMore = true, nextCursor = null))

        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()

        coVerify(exactly = 1) { agentRepository.getAgentsPaginated(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `first page drops repeated agents`() = runTest {
        // The grid keys cards by id, so a repeat would crash it (#541).
        stubPage(null) returns page(testAgents + testAgents[0])

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.agents.map { it.id })
            .containsExactly("agent-1", "agent-2", "agent-3").inOrder()
    }

    @Test
    fun `refresh drops repeated agents`() = runTest {
        stubPage(null) returns page(testAgents, next = "c1") andThen
            page(testAgents + testAgents[1], next = "c1")

        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.refresh()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.agents.map { it.id })
            .containsExactly("agent-1", "agent-2", "agent-3").inOrder()
    }

    @Test
    fun `loadMore drops an agent repeated across the page boundary`() = runTest {
        stubPage("c1") returns page(listOf(testAgents[2], Agent(id = "agent-4")))

        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.agents.map { it.id })
            .containsExactly("agent-1", "agent-2", "agent-3", "agent-4").inOrder()
    }

    @Test
    fun `loadMore is no-op when hasMore is false`() = runTest {
        stubPage(null) returns page(testAgents)

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.loadMore()
        advanceUntilIdle()

        // Only the first page is read.
        coVerify(exactly = 1) { agentRepository.getAgentsPaginated(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `loadMore error surfaces error message`() = runTest {
        stubPage("c1") returns Result.Error(message = "Load more failed")

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.loadMore()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.error).isEqualTo("Load more failed")
        assertThat(viewModel.uiState.value.isLoadingMore).isFalse()
    }

    @Test
    fun `onSearchQueryChanged updates query and triggers reload after debounce`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onSearchQueryChanged("coding")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.searchQuery).isEqualTo("coding")
    }

    @Test
    fun `onCategorySelected sets category and reloads`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onCategorySelected("coding")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.selectedCategory).isEqualTo("coding")
    }

    @Test
    fun `onCategorySelected toggles off already selected category`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onCategorySelected("coding")
        advanceUntilIdle()

        viewModel.onCategorySelected("coding")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.selectedCategory).isNull()
    }

    @Test
    fun `refresh reads from the first page again`() = runTest {
        stubPage("c1") returns page(listOf(Agent(id = "agent-4")), next = "c2")

        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isRefreshing).isFalse()
        coVerify(exactly = 2) { agentRepository.getAgentsPaginated(any(), cursor = null, any(), any(), any(), any()) }
        assertThat(state.nextCursor).isEqualTo("c1")
        assertThat(state.agents.map { it.id }).containsExactly("agent-1", "agent-2", "agent-3").inOrder()
    }

    @Test
    fun `refresh error surfaces error message`() = runTest {
        // First call succeeds (init), subsequent calls fail
        stubPage(null) returns page(testAgents, next = "c1") andThen
            Result.Error(message = "Refresh failed")

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.error).isEqualTo("Refresh failed")
        assertThat(viewModel.uiState.value.isRefreshing).isFalse()
    }

    @Test
    fun `dismissError clears error state`() = runTest {
        stubPage(null) returns Result.Error(message = "Error")

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.error).isNotNull()

        viewModel.dismissError()

        assertThat(viewModel.uiState.value.error).isNull()
    }

    @Test
    fun `agent with null name shows as Unnamed Agent`() = runTest {
        val agentNoName = Agent(id = "agent-x", name = null)
        stubPage(null) returns page(listOf(agentNoName))

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.agents[0].name).isEqualTo("Unnamed Agent")
    }

    @Test
    fun `categories failure is non-critical and does not show error`() = runTest {
        coEvery { agentRepository.getAgentCategories() } returns
            Result.Error(message = "Categories unavailable")

        viewModel = createViewModel()
        advanceUntilIdle()

        // Error should not be set for category failures
        assertThat(viewModel.uiState.value.error).isNull()
        assertThat(viewModel.uiState.value.categories).isEmpty()
    }
}
