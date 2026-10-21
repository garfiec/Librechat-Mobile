package com.garfiec.librechat.feature.agents.viewmodel

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.ServerDataStore
import com.garfiec.librechat.core.data.repository.AgentRepository
import com.garfiec.librechat.core.data.repository.FavoritesRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.AgentCategory
import com.garfiec.librechat.core.model.FavoritesLimits
import com.garfiec.librechat.core.model.UserFavorite
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
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
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AgentDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val agentRepository = mockk<AgentRepository>(relaxed = true)
    private val serverDataStore = mockk<ServerDataStore>(relaxed = true)
    private val favorites = MutableStateFlow<List<UserFavorite>>(emptyList())
    private val favoritesRepository = mockk<FavoritesRepository>(relaxed = true)
    private val role = MutableStateFlow<UserRolePermissions?>(null)
    private val roleRepository = mockk<RoleRepository>(relaxed = true)

    private val agent = Agent(id = "agent-1", name = "Coding Assistant")

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        // Stubbed explicitly because a relaxed mock answers `false` for a nullable Boolean, which
        // this screen reads as the list denying edit — the opposite of "no list answer".
        coEvery { agentRepository.listedEditVerdict(any()) } returns null
        every { agentRepository.revision } returns revision
        every { serverDataStore.getBaseUrl() } returns "https://chat.example.com"
        every { favoritesRepository.favorites } returns favorites
        coEvery { favoritesRepository.refresh() } answers { Result.Success(favorites.value) }
        coEvery { favoritesRepository.setFavorites(any()) } answers {
            val list = firstArg<List<UserFavorite>>()
            favorites.value = list
            Result.Success(list)
        }
        every { roleRepository.userPermissions } returns role
    }

    private val revision = MutableStateFlow(0L)

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun forbidden() = Result.Error(exception = ApiException(statusCode = 403, message = "Forbidden"))

    private fun createViewModel() = AgentDetailViewModel(
        agentRepository = agentRepository,
        serverDataStore = serverDataStore,
        favoritesRepository = favoritesRepository,
        roleRepository = roleRepository,
        initialAgentId = "agent-1",
    )

    @Test
    fun `canEdit is true when the edit-gated expanded fetch succeeds`() = runTest(testDispatcher) {
        coEvery { agentRepository.getAgentForEditing("agent-1") } returns Result.Success(agent)

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.agent).isNotNull()
        assertThat(state.canEdit).isTrue()
    }

    @Test
    fun `canEdit is false when the edit endpoint returns a 403 and view fetch succeeds`() = runTest(testDispatcher) {
        coEvery { agentRepository.getAgentForEditing("agent-1") } returns forbidden()
        coEvery { agentRepository.getAgent("agent-1") } returns Result.Success(agent)

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.agent).isNotNull()
        assertThat(state.canEdit).isFalse()
    }

    @Test
    fun `canEdit stays true when the edit endpoint fails transiently but view fetch succeeds`() = runTest(testDispatcher) {
        coEvery { agentRepository.getAgentForEditing("agent-1") } returns
            Result.Error(exception = ApiException(statusCode = 500, message = "Server error"))
        coEvery { agentRepository.getAgent("agent-1") } returns Result.Success(agent)

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.agent).isNotNull()
        assertThat(state.canEdit).isTrue()
    }

    /**
     * The list is the only place `isEditable` is stamped (upstream `getListAgents`), so the verdict
     * has to reach the detail screen through the repository — reading it off the agent this screen
     * fetched leaves the conjunct permanently null and narrows nothing on any server.
     */
    @Test
    fun `canEdit is false when the list said this agent is not editable`() = runTest(testDispatcher) {
        coEvery { agentRepository.getAgent("agent-1") } returns Result.Success(agent)
        coEvery { agentRepository.listedEditVerdict("agent-1") } returns false

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.agent).isNotNull()
        assertThat(viewModel.uiState.value.canEdit).isFalse()
        // The probe could only 403.
        coVerify(exactly = 0) { agentRepository.getAgentForEditing(any()) }
    }

    @Test
    fun `the agent's own editable verdict outranks a stale list denial`() = runTest(testDispatcher) {
        coEvery { agentRepository.getAgent("agent-1") } returns Result.Success(agent.copy(isEditable = true))
        coEvery { agentRepository.listedEditVerdict("agent-1") } returns false

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.canEdit).isTrue()
    }

    /**
     * Absence is UNKNOWN, not permission — but it is also not denial: every server that predates
     * the field answers null for every agent, so the probe has to stay in charge there.
     */
    @Test
    fun `an unknown list verdict leaves the probe in charge`() = runTest(testDispatcher) {
        coEvery { agentRepository.getAgentForEditing("agent-1") } returns Result.Success(agent)
        coEvery { agentRepository.listedEditVerdict("agent-1") } returns null

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.canEdit).isTrue()
    }

    /** A list verdict cannot GRANT edit either: the 403 probe still decides against it. */
    @Test
    fun `a list verdict of true does not override a forbidden probe`() = runTest(testDispatcher) {
        coEvery { agentRepository.getAgentForEditing("agent-1") } returns forbidden()
        coEvery { agentRepository.getAgent("agent-1") } returns Result.Success(agent)
        coEvery { agentRepository.listedEditVerdict("agent-1") } returns true

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.canEdit).isFalse()
    }

    /** `getAgent` can serve a list-projection row from the cache, which carries its own answer. */
    @Test
    fun `the agent's own field wins over the recorded list verdict`() = runTest(testDispatcher) {
        coEvery { agentRepository.getAgentForEditing("agent-1") } returns
            Result.Success(agent.copy(isEditable = false))
        coEvery { agentRepository.listedEditVerdict("agent-1") } returns true

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.canEdit).isFalse()
    }

    @Test
    fun `canEdit is false when the agent fails to load entirely`() = runTest(testDispatcher) {
        coEvery { agentRepository.getAgentForEditing("agent-1") } returns forbidden()
        coEvery { agentRepository.getAgent("agent-1") } returns Result.Error(message = "Not found")

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.agent).isNull()
        assertThat(state.loadError).isEqualTo(AgentLoadError("Not found"))
        assertThat(state.canEdit).isFalse()
    }

    /** Saving in the editor pops back to this same page, so the page has to pick the save up. */
    @Test
    fun `an agent edit elsewhere refreshes the page without a spinner`() = runTest(testDispatcher) {
        coEvery { agentRepository.getAgentForEditing("agent-1") } returns Result.Success(agent)
        val viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { agentRepository.getAgentForEditing("agent-1") } returns
            Result.Success(agent.copy(name = "Renamed"))
        val loadingSeen = mutableListOf<Boolean>()
        // Unconfined: advanceUntilIdle never runs backgroundScope work, so this would record nothing.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { loadingSeen += it.isLoading }
        }
        revision.value += 1
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.agent?.name).isEqualTo("Renamed")
        assertThat(loadingSeen).doesNotContain(true)
    }

    @Test
    fun `a failed refresh keeps the page it was showing`() = runTest(testDispatcher) {
        coEvery { agentRepository.getAgentForEditing("agent-1") } returns Result.Success(agent)
        val viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { agentRepository.getAgentForEditing("agent-1") } returns forbidden()
        coEvery { agentRepository.getAgent("agent-1") } returns Result.Error(message = "Not found")
        revision.value += 1
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.agent?.name).isEqualTo("Coding Assistant")
        assertThat(state.loadError).isNull()
    }

    // --- Full record ---

    @Test
    fun `model and tools count as the agent's own only after the expanded read`() = runTest(testDispatcher) {
        coEvery { agentRepository.getAgentForEditing("agent-1") } returns
            Result.Success(agent.copy(model = "gpt-5", tools = listOf("web_search")))

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isFullRecord).isTrue()
        assertThat(viewModel.uiState.value.agent?.model).isEqualTo("gpt-5")
        assertThat(viewModel.uiState.value.agent?.tools).containsExactly("web_search")
    }

    @Test
    fun `the view-only fallback is not a full record`() = runTest(testDispatcher) {
        coEvery { agentRepository.getAgentForEditing("agent-1") } returns forbidden()
        coEvery { agentRepository.getAgent("agent-1") } returns Result.Success(agent.copy(model = "gpt-5"))

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.agent).isNotNull()
        assertThat(viewModel.uiState.value.isFullRecord).isFalse()
    }

    @Test
    fun `a skipped probe is not a full record either`() = runTest(testDispatcher) {
        coEvery { agentRepository.listedEditVerdict("agent-1") } returns false
        coEvery { agentRepository.getAgent("agent-1") } returns Result.Success(agent)

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isFullRecord).isFalse()
    }

    @Test
    fun `an unnamed agent stays unnamed for the screen to word`() = runTest(testDispatcher) {
        coEvery { agentRepository.getAgentForEditing("agent-1") } returns Result.Success(agent.copy(name = "  "))

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.agent?.name).isNull()
    }

    @Test
    fun `a failed load carries no English of its own`() = runTest(testDispatcher) {
        coEvery { agentRepository.getAgentForEditing("agent-1") } returns forbidden()
        coEvery { agentRepository.getAgent("agent-1") } returns Result.Error(message = null)

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.loadError).isEqualTo(AgentLoadError(message = null))
    }

    // --- Categories ---

    @Test
    fun `categories are read for an agent with a category to label`() = runTest(testDispatcher) {
        val hr = AgentCategory(value = "hr", label = "People team")
        coEvery { agentRepository.getAgentForEditing("agent-1") } returns Result.Success(agent.copy(category = "hr"))
        coEvery { agentRepository.getAgentCategories() } returns Result.Success(listOf(hr))

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.categories).containsExactly(hr)
    }

    @Test
    fun `a general agent reads no categories`() = runTest(testDispatcher) {
        coEvery { agentRepository.getAgentForEditing("agent-1") } returns Result.Success(agent.copy(category = "general"))

        createViewModel()
        advanceUntilIdle()

        coVerify(exactly = 0) { agentRepository.getAgentCategories() }
    }

    // --- Action errors ---

    @Test
    fun `a failed delete is an action error and the page stays`() = runTest(testDispatcher) {
        coEvery { agentRepository.getAgentForEditing("agent-1") } returns Result.Success(agent)
        coEvery { agentRepository.deleteAgent("agent-1") } returns Result.Error(message = "Nope")
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.deleteAgent()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.agent?.name).isEqualTo("Coding Assistant")
        assertThat(state.loadError).isNull()
        assertThat(state.isDeleting).isFalse()
        assertThat(state.actionError).isEqualTo(AgentActionError.Delete("Nope"))

        viewModel.dismissActionError()
        assertThat(viewModel.uiState.value.actionError).isNull()
    }

    @Test
    fun `a failed duplicate is an action error and the page stays`() = runTest(testDispatcher) {
        coEvery { agentRepository.getAgentForEditing("agent-1") } returns Result.Success(agent)
        coEvery { agentRepository.duplicateAgent("agent-1") } returns Result.Error(message = null)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.duplicateAgent()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.agent).isNotNull()
        assertThat(state.loadError).isNull()
        assertThat(state.isDuplicating).isFalse()
        assertThat(state.actionError).isEqualTo(AgentActionError.Duplicate(message = null))
    }

    // --- Duplicate ---

    @Test
    fun `duplicate announces the copy`() = runTest(testDispatcher) {
        coEvery { agentRepository.getAgentForEditing("agent-1") } returns Result.Success(agent)
        coEvery { agentRepository.duplicateAgent("agent-1") } returns Result.Success(agent.copy(id = "agent-2"))
        val viewModel = createViewModel()
        advanceUntilIdle()
        val events = mutableListOf<AgentDetailEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect { events += it } }

        viewModel.duplicateAgent()
        advanceUntilIdle()

        assertThat(events).containsExactly(AgentDetailEvent.Duplicated("agent-2"))
    }

    @Test
    fun `duplicate needs AGENTS CREATE`() = runTest(testDispatcher) {
        coEvery { agentRepository.getAgentForEditing("agent-1") } returns Result.Success(agent)
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.canDuplicate).isTrue()

        role.value = UserRolePermissions(name = "USER", permissions = mapOf("AGENTS" to mapOf("CREATE" to false)))
        advanceUntilIdle()
        viewModel.duplicateAgent()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.canDuplicate).isFalse()
        coVerify(exactly = 0) { agentRepository.duplicateAgent(any()) }
    }

    /** The duplicate route needs EDIT on the source and the delete route needs CREATE: neither is offered. */
    @Test
    fun `a view-only agent offers neither duplicate nor delete even with AGENTS CREATE`() = runTest(testDispatcher) {
        coEvery { agentRepository.getAgentForEditing("agent-1") } returns forbidden()
        coEvery { agentRepository.getAgent("agent-1") } returns Result.Success(agent)
        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.canCreate).isTrue()
        assertThat(state.canEdit).isFalse()
        assertThat(state.canDuplicate).isFalse()
        assertThat(state.canDelete).isFalse()

        viewModel.duplicateAgent()
        viewModel.showDeleteConfirmation()
        viewModel.deleteAgent()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.showDeleteDialog).isFalse()
        coVerify(exactly = 0) { agentRepository.duplicateAgent(any()) }
        coVerify(exactly = 0) { agentRepository.deleteAgent(any()) }
    }

    @Test
    fun `an editable agent without AGENTS CREATE offers neither duplicate nor delete`() = runTest(testDispatcher) {
        coEvery { agentRepository.getAgentForEditing("agent-1") } returns Result.Success(agent)
        role.value = UserRolePermissions(name = "USER", permissions = mapOf("AGENTS" to mapOf("CREATE" to false)))
        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.canEdit).isTrue()
        assertThat(state.canDuplicate).isFalse()
        assertThat(state.canDelete).isFalse()

        viewModel.duplicateAgent()
        viewModel.deleteAgent()
        advanceUntilIdle()

        coVerify(exactly = 0) { agentRepository.duplicateAgent(any()) }
        coVerify(exactly = 0) { agentRepository.deleteAgent(any()) }
    }

    @Test
    fun `an editable agent with AGENTS CREATE offers duplicate and delete`() = runTest(testDispatcher) {
        coEvery { agentRepository.getAgentForEditing("agent-1") } returns Result.Success(agent)
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.canDuplicate).isTrue()
        assertThat(viewModel.uiState.value.canDelete).isTrue()
    }

    // --- Pin ---

    @Test
    fun `pin adds the agent to the favorites list and keeps the others`() = runTest(testDispatcher) {
        val model = UserFavorite(endpoint = "openAI", model = "gpt-5")
        favorites.value = listOf(model)
        coEvery { agentRepository.getAgentForEditing("agent-1") } returns Result.Success(agent)
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.isPinned).isFalse()

        viewModel.togglePin()
        advanceUntilIdle()

        coVerify { favoritesRepository.setFavorites(listOf(model, UserFavorite(agentId = "agent-1"))) }
        assertThat(viewModel.uiState.value.isPinned).isTrue()
    }

    @Test
    fun `unpin removes only this agent`() = runTest(testDispatcher) {
        val other = UserFavorite(agentId = "agent-9")
        favorites.value = listOf(UserFavorite(agentId = "agent-1"), other)
        coEvery { agentRepository.getAgentForEditing("agent-1") } returns Result.Success(agent)
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.isPinned).isTrue()

        viewModel.togglePin()
        advanceUntilIdle()

        coVerify { favoritesRepository.setFavorites(listOf(other)) }
        assertThat(viewModel.uiState.value.isPinned).isFalse()
    }

    @Test
    fun `a pin made elsewhere shows on the page`() = runTest(testDispatcher) {
        coEvery { agentRepository.getAgentForEditing("agent-1") } returns Result.Success(agent)
        val viewModel = createViewModel()
        advanceUntilIdle()

        favorites.value = listOf(UserFavorite(agentId = "agent-1"))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isPinned).isTrue()
    }

    /** A pin writes the whole list: one built on a list never read would wipe every other pin. */
    @Test
    fun `pin never writes a list it could not read`() = runTest(testDispatcher) {
        coEvery { favoritesRepository.refresh() } returns Result.Error(message = "offline")
        coEvery { agentRepository.getAgentForEditing("agent-1") } returns Result.Success(agent)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.togglePin()
        advanceUntilIdle()

        coVerify(exactly = 0) { favoritesRepository.setFavorites(any()) }
        assertThat(viewModel.uiState.value.actionError).isEqualTo(AgentActionError.Pin(message = null))
    }

    @Test
    fun `pin reads the list first when the first read failed`() = runTest(testDispatcher) {
        val model = UserFavorite(endpoint = "openAI", model = "gpt-5")
        coEvery { favoritesRepository.refresh() } returns Result.Error(message = "offline")
        coEvery { agentRepository.getAgentForEditing("agent-1") } returns Result.Success(agent)
        val viewModel = createViewModel()
        advanceUntilIdle()

        coEvery { favoritesRepository.refresh() } answers {
            favorites.value = listOf(model)
            Result.Success(favorites.value)
        }
        viewModel.togglePin()
        advanceUntilIdle()

        coVerify { favoritesRepository.setFavorites(listOf(model, UserFavorite(agentId = "agent-1"))) }
    }

    @Test
    fun `a full pin list is a limit error and nothing is sent`() = runTest(testDispatcher) {
        favorites.value = List(FavoritesLimits.MAX_FAVORITES) { UserFavorite(agentId = "other-$it") }
        coEvery { agentRepository.getAgentForEditing("agent-1") } returns Result.Success(agent)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.togglePin()
        advanceUntilIdle()

        coVerify(exactly = 0) { favoritesRepository.setFavorites(any()) }
        assertThat(viewModel.uiState.value.actionError).isEqualTo(AgentActionError.PinLimit(FavoritesLimits.MAX_FAVORITES))
    }

    @Test
    fun `a failed pin save is an action error`() = runTest(testDispatcher) {
        coEvery { favoritesRepository.setFavorites(any()) } returns Result.Error(message = "Server said no")
        coEvery { agentRepository.getAgentForEditing("agent-1") } returns Result.Success(agent)
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.togglePin()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.actionError).isEqualTo(AgentActionError.Pin("Server said no"))
        assertThat(viewModel.uiState.value.agent).isNotNull()
    }

    // --- Copy link ---

    @Test
    fun `the chat link opens a new chat with the agent on the server`() = runTest(testDispatcher) {
        coEvery { agentRepository.getAgentForEditing("agent-1") } returns Result.Success(agent)
        val viewModel = createViewModel()

        assertThat(viewModel.chatLink()).isEqualTo("https://chat.example.com/c/new?agent_id=agent-1")
    }

    @Test
    fun `the chat link keeps a deployment path and drops a trailing slash`() {
        assertThat(agentChatLink("https://example.com/librechat/", "agent_x"))
            .isEqualTo("https://example.com/librechat/c/new?agent_id=agent_x")
    }
}
