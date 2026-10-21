package com.garfiec.librechat.feature.chat.viewmodel

import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.LastUsedSelection
import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.EndpointConfig
import io.mockk.coEvery
import io.mockk.every
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A conversation starter tapped on an agent page is staged in [PromptInsertionHandoff] by the nav
 * host, which then opens `NewChat(agentId)`. The landing's chat screen calls
 * [ChatViewModel.consumePendingPromptInsertion] when it enters composition; these call it where the
 * screen would, and assert the starter sits unsent in the composer with the agent still selected.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelStarterHandoffTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val fixture = ChatViewModelTestFixture()

    /** Held open so the agents list (one of model seeding's inputs) lands after the composer fill. */
    private val agentsList = CompletableDeferred<Result<List<Agent>>>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fixture.stubDefaults()
        with(fixture) {
            // A non-agent last-used: the seeder's Tier-0 agent override has to win over it.
            every { settingsDataStore.lastUsedSelection } returns MutableStateFlow(
                LastUsedSelection(endpoint = OTHER_ENDPOINT, model = OTHER_MODEL, spec = null),
            )
            every { configRepository.endpointConfigs } returns MutableStateFlow(
                mapOf(OTHER_ENDPOINT to EndpointConfig(), EndpointConstants.AGENTS to EndpointConfig()),
            )
            every { configRepository.availableModels } returns MutableStateFlow(
                mapOf(OTHER_ENDPOINT to listOf(OTHER_MODEL), EndpointConstants.AGENTS to listOf(AGENT_ID)),
            )
            coEvery { permissionGate.awaitRole() } returns null
            coEvery { agentRepository.getAgents(any()) } coAnswers { agentsList.await() }
            coEvery { agentRepository.getAgentProvider(any()) } returns Result.Success("openAI")
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a starter reaches the composer of a fresh agent landing, unsent`() = landingTest { vm ->
        // Staged before the landing exists, as the nav host does on the tap.
        fixture.promptInsertionHandoff.put(STARTER)
        vm.consumePendingPromptInsertion()

        // Model seeding finishes after the fill: the agents list lands now.
        agentsList.complete(Result.Success(listOf(Agent(id = AGENT_ID))))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(STARTER, state.inputText)
        assertEquals(EndpointConstants.AGENTS, state.selectedEndpoint)
        assertEquals(AGENT_ID, state.selectedModel)
        assertTrue(state.messages.isEmpty())
        assertNull(fixture.promptInsertionHandoff.take())
    }

    @Test
    fun `a new-chat draft restored after the fill does not replace the starter`() {
        val draft = CompletableDeferred<String?>()
        coEvery { fixture.draftRepository.awaitDraft(any()) } coAnswers { draft.await() }
        landingTest { vm ->
            fixture.promptInsertionHandoff.put(STARTER)
            vm.consumePendingPromptInsertion()
            draft.complete("an older draft")
            agentsList.complete(Result.Success(listOf(Agent(id = AGENT_ID))))
            advanceUntilIdle()

            assertEquals(STARTER, vm.uiState.value.inputText)
        }
    }

    @Test
    fun `a starter replaces a new-chat draft restored before it`() {
        coEvery { fixture.draftRepository.awaitDraft(any()) } returns "an older draft"
        landingTest { vm ->
            assertEquals("an older draft", vm.uiState.value.inputText)
            fixture.promptInsertionHandoff.put(STARTER)
            vm.consumePendingPromptInsertion()
            advanceUntilIdle()

            assertEquals(STARTER, vm.uiState.value.inputText)
        }
    }

    @Test
    fun `an existing agent landing takes a starter when it re-enters`() = landingTest { vm ->
        agentsList.complete(Result.Success(listOf(Agent(id = AGENT_ID))))
        advanceUntilIdle()
        // First composition found nothing waiting.
        vm.consumePendingPromptInsertion()
        assertEquals("", vm.uiState.value.inputText)

        // The stack unwinds to this same NewChat(agentId) entry after a starter tap.
        fixture.promptInsertionHandoff.put(STARTER)
        vm.consumePendingPromptInsertion()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(STARTER, state.inputText)
        assertEquals(EndpointConstants.AGENTS, state.selectedEndpoint)
        assertEquals(AGENT_ID, state.selectedModel)
        assertNull(fixture.promptInsertionHandoff.take())
    }

    private fun landingTest(body: suspend TestScope.(ChatViewModel) -> Unit) = runTest(testDispatcher) {
        val vm = fixture.build(defaultDispatcher = testDispatcher, initialAgentId = AGENT_ID)
        advanceUntilIdle()
        try {
            body(vm)
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    private companion object {
        const val AGENT_ID = "agent_X"
        const val OTHER_ENDPOINT = "anthropic"
        const val OTHER_MODEL = "claude-haiku-4-5"
        const val STARTER = "What can you help me plan this week?"
    }
}
