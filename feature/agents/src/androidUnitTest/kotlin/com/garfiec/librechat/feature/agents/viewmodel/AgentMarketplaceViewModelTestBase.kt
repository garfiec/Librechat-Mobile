package com.garfiec.librechat.feature.agents.viewmodel

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.ServerDataStore
import com.garfiec.librechat.core.data.repository.AgentRepository
import com.garfiec.librechat.core.data.repository.RoleRepository
import com.garfiec.librechat.core.data.util.PermissionGate
import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.AgentCategory
import com.garfiec.librechat.core.model.PaginatedAgents
import com.garfiec.librechat.core.model.permissions.UserRolePermissions
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before

/** Shared fixture for the marketplace VM tests: a permissive role, three agents, three categories. */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class AgentMarketplaceViewModelTestBase {

    protected val testDispatcher = StandardTestDispatcher()

    protected val agentRepository = mockk<AgentRepository>(relaxed = true)
    protected val serverDataStore = mockk<ServerDataStore>(relaxed = true)
    protected val roleRepository = mockk<RoleRepository>(relaxed = true)
    protected val permissionGate = mockk<PermissionGate>(relaxed = true)
    protected val roleFlow = MutableStateFlow<UserRolePermissions?>(null)

    protected lateinit var viewModel: AgentMarketplaceViewModel

    protected val testAgents = listOf(
        Agent(id = "agent-1", name = "Coding Assistant", description = "Helps with code", category = "coding"),
        Agent(id = "agent-2", name = "Writer", description = "Creative writing", category = "writing"),
        Agent(id = "agent-3", name = "Math Tutor", description = "Teaches math", category = "education"),
    )

    protected val testCategories = listOf(
        AgentCategory(value = "coding", label = "Coding", count = 5),
        AgentCategory(value = "writing", label = "Writing", count = 3),
        AgentCategory(value = "education", label = "Education", count = 2),
    )

    // What the categories route serves when some agent is promoted: both sentinels appended.
    protected val categoriesWithSentinels = testCategories +
        AgentCategory(value = "promoted", label = "com_agents_top_picks") +
        AgentCategory(value = "all", label = "com_agents_all")

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        // A null role is permissive: the flags stay `true`, and awaitRole() answering null
        // (a timeout) lets the gated first read through.
        every { roleRepository.userPermissions } returns roleFlow
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
    protected fun stubPage(cursor: String?) = coEvery {
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
    protected fun page(agents: List<Agent>, next: String? = null): Result<PaginatedAgents> =
        Result.Success(PaginatedAgents(agents = agents, hasMore = next != null, nextCursor = next))

    protected fun createViewModel() = AgentMarketplaceViewModel(
        agentRepository = agentRepository,
        serverDataStore = serverDataStore,
        roleRepository = roleRepository,
        permissionGate = permissionGate,
    )
}
