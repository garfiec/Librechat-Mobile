package com.garfiec.librechat.feature.conversations.viewmodel

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConversationRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
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

    @Test
    fun `a failed unarchive is reported`() = runTest(testDispatcher) {
        coEvery { conversationRepository.archive("c1", false) } returns Result.Error(message = "boom")
        val viewModel = ArchivedConversationsViewModel(conversationRepository)
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
        val viewModel = ArchivedConversationsViewModel(conversationRepository)
        advanceUntilIdle()
        val event = backgroundScope.async { viewModel.events.first() }
        advanceUntilIdle()

        viewModel.deleteConversation("c1")
        advanceUntilIdle()

        assertThat(event.await())
            .isEqualTo(ArchivedConversationsEvent.ShowError("Failed to delete conversation"))
    }
}
