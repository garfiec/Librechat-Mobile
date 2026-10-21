package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.common.identity.AccountState
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

/**
 * The agent page's VIEW read and the list labels it borrows.
 *
 * The list projection has no model, provider or `isPublic`; the VIEW route has them but no
 * `category` or `is_promoted`. The page needs both halves.
 */
class AgentRepositoryViewReadTest {

    private val agentsApi = mockk<AgentsApi>()

    private fun repository(provider: InMemoryActiveAccountProvider = InMemoryActiveAccountProvider()) =
        AgentRepositoryImpl(agentsApi = agentsApi, activeAccountProvider = provider)

    @Test
    fun `the view read skips a warm list cache`() = runTest {
        coEvery { agentsApi.getAgents(null) } returns AgentListResponse(data = listOf(Agent(id = "a1")))
        coEvery { agentsApi.getAgent("a1") } returns Agent(id = "a1", model = "gpt-5", isPublic = true)
        val repository = repository()
        repository.getAgents()

        val viewed = (repository.getAgentForViewing("a1") as Result.Success).data

        assertThat(viewed.model).isEqualTo("gpt-5")
        assertThat(viewed.isPublic).isTrue()
        coVerify(exactly = 1) { agentsApi.getAgent("a1") }
    }

    // What decoding a VIEW body yields: the model's "general" default and not promoted.
    private fun viewBody(id: String) = Agent(id = id, model = "gpt-5")

    private suspend fun AgentRepositoryImpl.viewed(id: String) = (getAgentForViewing(id) as Result.Success).data

    @Test
    fun `the view read takes category and Top pick from the last paginated list row`() = runTest {
        coEvery { agentsApi.getAgentsPaginated(any(), any(), any(), any(), any(), any()) } returns AgentListResponse(
            data = listOf(Agent(id = "a1", category = "hr", isPromoted = true)),
        )
        coEvery { agentsApi.getAgent("a1") } returns viewBody("a1")
        val repository = repository()
        repository.getAgentsPaginated(limit = 20)

        val viewed = repository.viewed("a1")

        assertThat(viewed.category).isEqualTo("hr")
        assertThat(viewed.isPromoted).isTrue()
        assertThat(viewed.model).isEqualTo("gpt-5")
    }

    @Test
    fun `the unpaginated list read records them too`() = runTest {
        coEvery { agentsApi.getAgents(null) } returns AgentListResponse(data = listOf(Agent(id = "a1", category = "rd")))
        coEvery { agentsApi.getAgent("a1") } returns viewBody("a1")
        val repository = repository()
        repository.getAgents()

        assertThat(repository.viewed("a1").category).isEqualTo("rd")
    }

    /** The decoded "general" default is not the agent's category; an unlisted agent has none. */
    @Test
    fun `an agent never listed has no category and is not a Top pick`() = runTest {
        coEvery { agentsApi.getAgent("a1") } returns viewBody("a1").copy(category = "general", isPromoted = true)

        val viewed = repository().viewed("a1")

        assertThat(viewed.category).isNull()
        assertThat(viewed.isPromoted).isFalse()
    }

    @Test
    fun `labels recorded for one account are not served to another`() = runTest {
        coEvery { agentsApi.getAgentsPaginated(any(), any(), any(), any(), any(), any()) } returns AgentListResponse(
            data = listOf(Agent(id = "a1", category = "hr")),
        )
        coEvery { agentsApi.getAgent("a1") } returns viewBody("a1")
        val provider = InMemoryActiveAccountProvider(AccountState.Resolved(AccountId("srv:userA")))
        val repository = repository(provider)
        repository.getAgentsPaginated(limit = 20)

        provider.set(AccountId("srv:userB"))

        assertThat(repository.viewed("a1").category).isNull()
    }
}
