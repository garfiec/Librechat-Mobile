package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.identity.InMemoryActiveAccountProvider
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.response.DuplicateAgentResponse
import com.garfiec.librechat.core.network.api.AgentsApi
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AgentRepositoryDuplicateTest {

    private val agentsApi = mockk<AgentsApi>()

    private fun repository() =
        AgentRepositoryImpl(agentsApi = agentsApi, activeAccountProvider = InMemoryActiveAccountProvider())

    @Test
    fun `duplicate returns the copy from the wrapped response and announces the change`() = runTest {
        coEvery { agentsApi.duplicateAgent("agent-1") } returns
            DuplicateAgentResponse(agent = Agent(id = "agent-2", name = "Copy"))
        val repository = repository()
        val before = repository.revision.value

        val result = repository.duplicateAgent("agent-1")

        assertThat((result as Result.Success).data.id).isEqualTo("agent-2")
        assertThat(repository.revision.value).isGreaterThan(before)
    }
}
