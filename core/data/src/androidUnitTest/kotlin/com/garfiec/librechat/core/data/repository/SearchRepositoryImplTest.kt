package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.response.ConversationListResponse
import com.garfiec.librechat.core.network.api.ConversationsApi
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SearchRepositoryImplTest {

    private val api = mockk<ConversationsApi>()
    private val repository = SearchRepositoryImpl(api)

    private fun page(ids: IntRange, next: String?) =
        ConversationListResponse(conversations = ids.map { Conversation(conversationId = "c$it") }, nextCursor = next)

    @Test
    fun `one page by default, at the server's default size`() = runTest {
        coEvery { api.getConversations(cursor = null, limit = 25, search = "paint") } returns page(1..25, next = "t1")

        val result = repository.search("paint")

        assertThat((result as Result.Success).data).hasSize(25)
        coVerify(exactly = 1) { api.getConversations(cursor = any(), limit = any(), search = any()) }
    }

    @Test
    fun `follows the cursor in the largest pages until the last one`() = runTest {
        coEvery { api.getConversations(cursor = null, limit = 100, search = "paint") } returns page(1..100, next = "t1")
        coEvery { api.getConversations(cursor = "t1", limit = 100, search = "paint") } returns page(101..200, next = "t2")
        coEvery { api.getConversations(cursor = "t2", limit = 100, search = "paint") } returns page(201..230, next = null)

        val result = repository.search("paint", SearchRepository.ALL_MATCHES)

        assertThat((result as Result.Success).data.map { it.conversationId }).containsExactlyElementsIn((1..230).map { "c$it" }).inOrder()
    }

    @Test
    fun `stops at the maximum, asking the last page for only what's left`() = runTest {
        coEvery { api.getConversations(cursor = null, limit = 100, search = "paint") } returns page(1..100, next = "t1")
        coEvery { api.getConversations(cursor = "t1", limit = 50, search = "paint") } returns page(101..150, next = "t2")

        val result = repository.search("paint", maxResults = 150)

        assertThat((result as Result.Success).data).hasSize(150)
        coVerify(exactly = 0) { api.getConversations(cursor = "t2", limit = any(), search = any()) }
    }

    @Test
    fun `a later page failing keeps what came before, the first failing is an error`() = runTest {
        coEvery { api.getConversations(cursor = null, limit = 100, search = "paint") } returns page(1..100, next = "t1")
        coEvery { api.getConversations(cursor = "t1", limit = 100, search = "paint") } throws IllegalStateException("boom")

        assertThat((repository.search("paint", SearchRepository.ALL_MATCHES) as Result.Success).data).hasSize(100)

        coEvery { api.getConversations(cursor = null, limit = 100, search = "rust") } throws IllegalStateException("boom")
        assertThat(repository.search("rust", SearchRepository.ALL_MATCHES)).isInstanceOf(Result.Error::class.java)
    }
}
