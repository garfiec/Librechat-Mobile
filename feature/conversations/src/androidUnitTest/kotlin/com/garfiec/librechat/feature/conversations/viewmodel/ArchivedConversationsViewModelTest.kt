package com.garfiec.librechat.feature.conversations.viewmodel

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.AgentRepository
import com.garfiec.librechat.core.data.repository.ConfigRepository
import com.garfiec.librechat.core.data.repository.ConversationRepository
import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.core.model.config.ModelSpecs
import com.garfiec.librechat.feature.conversations.toArchivedDisplayData
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArchivedConversationsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val conversationRepository = mockk<ConversationRepository>()
    private val configRepository = mockk<ConfigRepository> {
        every { endpointConfigs } returns MutableStateFlow(emptyMap())
        every { startupConfig } returns MutableStateFlow(null)
    }
    private val agentRepository = mockk<AgentRepository>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { conversationRepository.observeConversations(isArchived = true) } returns
            flowOf(Result.Success(emptyList()))
        coEvery { conversationRepository.loadNextPage(cursor = null, isArchived = true) } returns
            Result.Success(null)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = ArchivedConversationsViewModel(conversationRepository, configRepository, agentRepository)

    @Test
    fun `scrolling to the end loads the next page until the cursor runs out`() = runTest(testDispatcher) {
        coEvery { conversationRepository.loadNextPage(cursor = null, isArchived = true) } returns Result.Success("c2")
        coEvery { conversationRepository.loadNextPage(cursor = "c2", isArchived = true) } returns Result.Success(null)
        val viewModel = viewModel()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.hasMore).isTrue()

        viewModel.loadMore()
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.hasMore).isFalse()
        coVerify(exactly = 1) { conversationRepository.loadNextPage(cursor = "c2", isArchived = true) }
    }

    @Test
    fun `a failed next page stops asking until Retry, which resends the same cursor`() = runTest(testDispatcher) {
        coEvery { conversationRepository.loadNextPage(cursor = null, isArchived = true) } returns Result.Success("c2")
        coEvery { conversationRepository.loadNextPage(cursor = "c2", isArchived = true) } returns
            Result.Error(message = "offline")
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.loadMore()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.loadMoreFailed).isTrue()
        viewModel.loadMore()
        advanceUntilIdle()
        coVerify(exactly = 1) { conversationRepository.loadNextPage(cursor = "c2", isArchived = true) }

        coEvery { conversationRepository.loadNextPage(cursor = "c2", isArchived = true) } returns Result.Success(null)
        viewModel.retryLoadMore()
        advanceUntilIdle()

        coVerify(exactly = 2) { conversationRepository.loadNextPage(cursor = "c2", isArchived = true) }
        assertThat(viewModel.uiState.value.loadMoreFailed).isFalse()
        assertThat(viewModel.uiState.value.hasMore).isFalse()
    }

    @Test
    fun `refresh starts over from the first page`() = runTest(testDispatcher) {
        coEvery { conversationRepository.loadNextPage(cursor = null, isArchived = true) } returns Result.Success("c2")
        coEvery { conversationRepository.loadNextPage(cursor = "c2", isArchived = true) } returns
            Result.Error(message = "offline")
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()

        coVerify(exactly = 2) { conversationRepository.loadNextPage(cursor = null, isArchived = true) }
        assertThat(viewModel.uiState.value.loadMoreFailed).isFalse()
        assertThat(viewModel.uiState.value.hasMore).isTrue()
    }

    @Test
    fun `a failed refresh keeps paging from where the list was`() = runTest(testDispatcher) {
        coEvery { conversationRepository.loadNextPage(cursor = null, isArchived = true) } returns Result.Success("c2")
        coEvery { conversationRepository.loadNextPage(cursor = "c2", isArchived = true) } returns
            Result.Error(message = "offline")
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()

        coEvery { conversationRepository.loadNextPage(cursor = null, isArchived = true) } returns
            Result.Error(message = "offline")
        viewModel.refresh()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.loadMoreFailed).isTrue()

        coEvery { conversationRepository.loadNextPage(cursor = "c2", isArchived = true) } returns Result.Success(null)
        viewModel.retryLoadMore()
        advanceUntilIdle()

        coVerify(exactly = 2) { conversationRepository.loadNextPage(cursor = "c2", isArchived = true) }
        assertThat(viewModel.uiState.value.hasMore).isFalse()
    }

    @Test
    fun `a next page that lands after a refresh doesn't replace the new cursor`() = runTest(testDispatcher) {
        val stalePage = CompletableDeferred<Result<String?>>()
        coEvery { conversationRepository.loadNextPage(cursor = null, isArchived = true) } returns Result.Success("c2")
        coEvery { conversationRepository.loadNextPage(cursor = "c2", isArchived = true) } coAnswers { stalePage.await() }
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()
        stalePage.complete(Result.Success(null))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.hasMore).isTrue()
    }

    @Test
    fun `the empty state waits for the first page, not the cache's first emission`() = runTest(testDispatcher) {
        val firstPage = CompletableDeferred<Result<String?>>()
        coEvery { conversationRepository.loadNextPage(cursor = null, isArchived = true) } coAnswers { firstPage.await() }
        val viewModel = viewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isLoading).isTrue()
        firstPage.complete(Result.Success(null))
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.isLoading).isFalse()
    }

    @Test
    fun `an agent chat shows the agent's name`() = runTest(testDispatcher) {
        every { conversationRepository.observeConversations(isArchived = true) } returns flowOf(
            Result.Success(listOf(Conversation(conversationId = "c1", endpoint = "agents", model = "agent_abc"))),
        )
        coEvery { agentRepository.getAgent("agent_abc") } returns Result.Success(Agent(id = "agent_abc", name = "Researcher"))
        val viewModel = viewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.conversations.single().modelLabel).isEqualTo("Researcher")
    }

    @Test
    fun `a spec's label wins over the model id`() {
        val specs = ModelSpecs(list = listOf(ModelSpec(name = "fast", label = "Fast")))
        val conversation = Conversation(conversationId = "c1", model = "gpt-4o", spec = "fast")

        assertThat(conversation.toArchivedDisplayData(modelSpecs = specs).modelLabel).isEqualTo("Fast")
        assertThat(conversation.copy(spec = null).toArchivedDisplayData(modelSpecs = specs).modelLabel)
            .isEqualTo("gpt-4o")
    }

    @Test
    fun `a failed unarchive is reported`() = runTest(testDispatcher) {
        coEvery { conversationRepository.archive("c1", false) } returns Result.Error(message = "boom")
        val viewModel = viewModel()
        advanceUntilIdle()
        val event = backgroundScope.async { viewModel.events.first() }
        advanceUntilIdle()

        viewModel.unarchiveConversation("c1")
        advanceUntilIdle()

        assertThat(event.await())
            .isEqualTo(ArchivedConversationsEvent.ShowError("Failed to unarchive conversation"))
    }

    @Test
    fun `a failed delete is reported`() = runTest(testDispatcher) {
        coEvery { conversationRepository.delete("c1") } returns Result.Error(message = "boom")
        val viewModel = viewModel()
        advanceUntilIdle()
        val event = backgroundScope.async { viewModel.events.first() }
        advanceUntilIdle()

        viewModel.deleteConversation("c1")
        advanceUntilIdle()

        assertThat(event.await())
            .isEqualTo(ArchivedConversationsEvent.ShowError("Failed to delete conversation"))
    }
}
