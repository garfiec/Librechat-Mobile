package com.garfiec.librechat.feature.conversations.components

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.AgentRepository
import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.Conversation
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AgentNameLookupTest {

    private val agentRepository = mockk<AgentRepository>()

    private fun agentChat(agentId: String?, model: String? = null) =
        Conversation(conversationId = "c-$agentId-$model", endpoint = "agents", agentId = agentId, model = model)

    @Test
    fun `each agent is looked up once`() = runTest {
        coEvery { agentRepository.getAgent("agent_x") } returns Result.Success(Agent(id = "agent_x", name = "Pelican Pal"))
        val lookup = AgentNameLookup(agentRepository, this)

        lookup.request(listOf(agentChat("agent_x"), agentChat("agent_x", model = "gpt-4o-mini")))
        lookup.request(listOf(agentChat(null, model = "agent_x")))
        advanceUntilIdle()

        assertThat(lookup.names.value).containsExactly("agent_x", "Pelican Pal")
        coVerify(exactly = 1) { agentRepository.getAgent("agent_x") }
    }

    @Test
    fun `chats with no agent are not looked up`() = runTest {
        val lookup = AgentNameLookup(agentRepository, this)

        lookup.request(listOf(agentChat("ephemeral", model = "gpt-4o"), Conversation(conversationId = "c", model = "gpt-4o")))
        advanceUntilIdle()

        assertThat(lookup.names.value).isEmpty()
        coVerify(exactly = 0) { agentRepository.getAgent(any()) }
    }

    @Test
    fun `a failed or blank lookup leaves the agent unnamed`() = runTest {
        coEvery { agentRepository.getAgent("agent_gone") } returns Result.Error(message = "404")
        coEvery { agentRepository.getAgent("agent_blank") } returns Result.Success(Agent(id = "agent_blank", name = " "))
        val lookup = AgentNameLookup(agentRepository, this)

        lookup.request(listOf(agentChat("agent_gone"), agentChat("agent_blank")))
        advanceUntilIdle()

        assertThat(lookup.names.value).isEmpty()
    }

    @Test
    fun `clear forgets the names and looks them up again`() = runTest {
        coEvery { agentRepository.getAgent("agent_x") } returns Result.Success(Agent(id = "agent_x", name = "Pelican Pal"))
        val lookup = AgentNameLookup(agentRepository, this)
        lookup.request(listOf(agentChat("agent_x")))
        advanceUntilIdle()

        lookup.clear()
        assertThat(lookup.names.value).isEmpty()
        lookup.request(listOf(agentChat("agent_x")))
        advanceUntilIdle()

        assertThat(lookup.names.value).containsExactly("agent_x", "Pelican Pal")
        coVerify(exactly = 2) { agentRepository.getAgent("agent_x") }
    }
}
