package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.identity.InMemoryActiveAccountProvider
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.response.AgentListResponse
import com.garfiec.librechat.core.network.api.AgentsApi
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AgentRepositoryPaginationTest {

    private val agentsApi = mockk<AgentsApi>()

    private fun repository() =
        AgentRepositoryImpl(agentsApi = agentsApi, activeAccountProvider = InMemoryActiveAccountProvider())

    private fun stub(response: AgentListResponse) {
        coEvery { agentsApi.getAgentsPaginated(any(), any(), any(), any(), any(), any()) } returns response
    }

    @Test
    fun `the response's after becomes the next cursor`() = runTest {
        stub(AgentListResponse(data = listOf(Agent(id = "a1")), hasMore = true, after = "cursor-2"))

        val page = (repository().getAgentsPaginated(limit = 1) as Result.Success).data

        assertThat(page.agents.map { it.id }).containsExactly("a1")
        assertThat(page.nextCursor).isEqualTo("cursor-2")
        assertThat(page.hasMore).isTrue()
    }

    @Test
    fun `has_more without an after reads as the last page`() = runTest {
        stub(AgentListResponse(data = listOf(Agent(id = "a1")), hasMore = true, after = null))

        val page = (repository().getAgentsPaginated(limit = 1) as Result.Success).data

        assertThat(page.hasMore).isFalse()
        assertThat(page.nextCursor).isNull()
    }

    @Test
    fun `every filter is passed through to the route`() = runTest {
        stub(AgentListResponse())

        repository().getAgentsPaginated(
            limit = 20,
            cursor = "c1",
            search = "math",
            category = "education",
            promoted = true,
            requiredPermission = 2,
        )

        coVerify {
            agentsApi.getAgentsPaginated(
                limit = 20,
                cursor = "c1",
                search = "math",
                category = "education",
                promoted = true,
                requiredPermission = 2,
            )
        }
    }
}
