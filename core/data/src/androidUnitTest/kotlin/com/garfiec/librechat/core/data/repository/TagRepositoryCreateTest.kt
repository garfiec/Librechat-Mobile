package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.common.identity.AccountState
import com.garfiec.librechat.core.common.identity.InMemoryActiveAccountProvider
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.db.dao.ConversationDao
import com.garfiec.librechat.core.data.db.dao.ConversationTagDao
import com.garfiec.librechat.core.data.db.entity.ConversationEntity
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.ConversationTag
import com.garfiec.librechat.core.network.api.ConversationsApi
import com.garfiec.librechat.core.network.api.TagsApi
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * [TagRepositoryImpl.createTag]: the POST, the attach to a chat when asked, and which of the two
 * failed.
 */
class TagRepositoryCreateTest {

    private val tagsApi = mockk<TagsApi>(relaxed = true)
    private val tagDao = mockk<ConversationTagDao>(relaxed = true)
    private val conversationsApi = mockk<ConversationsApi>(relaxed = true)
    private val conversationDao = mockk<ConversationDao>(relaxed = true)
    private val account = AccountId("srv:user-1")

    private lateinit var repository: TagRepositoryImpl

    @Before
    fun setup() {
        repository = TagRepositoryImpl(
            tagsApi = tagsApi,
            tagDao = tagDao,
            conversationsApi = conversationsApi,
            conversationDao = conversationDao,
            activeAccountProvider = InMemoryActiveAccountProvider(AccountState.Resolved(account)),
            changes = TagChangeSignal(),
            applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
    }

    private fun serverHas(conversationId: String, vararg tags: String) {
        coEvery { conversationsApi.getConversation(conversationId) } returns
            Conversation(conversationId = conversationId, tags = tags.toList())
    }

    @Test
    fun `createTag posts the trimmed name, refreshes and bumps`() = runTest {
        coEvery { tagsApi.createTag(any(), any(), any(), any()) } returns ConversationTag(tag = "Recipes")

        val result = repository.createTag("  Recipes ", description = "  ")

        coVerify(exactly = 1) { tagsApi.createTag("Recipes", null, false, null) }
        coVerify(exactly = 1) { tagsApi.getTags() }
        assertThat((result as Result.Success).data.tag.tag).isEqualTo("Recipes")
        assertThat(result.data.attachFailed).isFalse()
        assertThat(repository.revision.value).isEqualTo(1L)
    }

    @Test
    fun `createTag refuses a blank name without a request`() = runTest {
        assertThat(repository.createTag(" ")).isInstanceOf(Result.Error::class.java)
        coVerify(exactly = 0) { tagsApi.createTag(any(), any(), any(), any()) }
    }

    @Test
    fun `createTag for a chat asks the server to attach it, then caches the chat's tags`() = runTest {
        coEvery { tagsApi.createTag(any(), any(), any(), any()) } returns ConversationTag(tag = "Recipes")
        // A new name: the server attached it during the POST, so the read already shows it.
        serverHas("convo-1", "web-only", "Recipes")

        repository.createTag("Recipes", description = "Food", addToConversationId = "convo-1")

        coVerify(exactly = 1) { tagsApi.createTag("Recipes", "Food", true, "convo-1") }
        coVerify(exactly = 0) { tagsApi.updateConversationTags(any(), any()) }
        coVerify(exactly = 1) { conversationDao.upsert(match<ConversationEntity> { it.tags == """["web-only","Recipes"]""" }) }
    }

    @Test
    fun `createTag of a name that already existed still attaches it to the chat`() = runTest {
        // The server returns an existing tag early and skips the attach.
        coEvery { tagsApi.createTag(any(), any(), any(), any()) } returns ConversationTag(tag = "work")
        serverHas("convo-1", "web-only")
        coEvery { tagsApi.updateConversationTags(any(), any()) } returns listOf("web-only", "work")

        coEvery { tagDao.getTagsForAccount(account.value) } returns emptyList() // cache hasn't caught up

        repository.createTag("work", addToConversationId = "convo-1")

        coVerify(exactly = 1) { tagsApi.updateConversationTags("convo-1", listOf("web-only", "work")) }
        // The POST already made sure the name exists; the attach doesn't post it again.
        coVerify(exactly = 1) { tagsApi.createTag(any(), any(), any(), any()) }
    }

    @Test
    fun `createTag whose attach fails says so, and still refreshes and bumps, since the bookmark exists`() = runTest {
        coEvery { tagsApi.createTag(any(), any(), any(), any()) } returns ConversationTag(tag = "Trips")
        coEvery { conversationsApi.getConversation("convo-1") } throws RuntimeException("connection lost")

        val result = repository.createTag("Trips", addToConversationId = "convo-1")

        // Made, so not an error: the caller must not offer to create it again.
        val created = (result as Result.Success).data
        assertThat(created.tag.tag).isEqualTo("Trips")
        assertThat(created.attachFailed).isTrue()
        coVerify(exactly = 1) { tagsApi.getTags() }
        assertThat(repository.revision.value).isEqualTo(1L)
    }

    @Test
    fun `createTag whose POST fails is an error, and touches neither the chat nor the tag list`() = runTest {
        coEvery { tagsApi.createTag(any(), any(), any(), any()) } throws RuntimeException("500")

        val result = repository.createTag("Trips", addToConversationId = "convo-1")

        assertThat(result).isInstanceOf(Result.Error::class.java)
        coVerify(exactly = 0) { conversationsApi.getConversation(any()) }
        coVerify(exactly = 0) { tagsApi.updateConversationTags(any(), any()) }
        coVerify(exactly = 0) { tagsApi.getTags() }
        assertThat(repository.revision.value).isEqualTo(0L)
    }
}
