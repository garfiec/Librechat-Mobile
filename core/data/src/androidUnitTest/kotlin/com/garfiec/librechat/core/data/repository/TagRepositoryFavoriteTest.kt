package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.common.identity.AccountState
import com.garfiec.librechat.core.common.identity.InMemoryActiveAccountProvider
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.db.dao.ConversationDao
import com.garfiec.librechat.core.data.db.dao.ConversationTagDao
import com.garfiec.librechat.core.data.db.entity.ConversationEntity
import com.garfiec.librechat.core.data.db.entity.ConversationTagEntity
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.SAVED_TAG
import com.garfiec.librechat.core.network.api.ConversationsApi
import com.garfiec.librechat.core.network.api.TagsApi
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

/**
 * The favorite star is `SAVED_TAG` written through [TagRepositoryImpl.setConversationTag], with the
 * user's intent (add or remove), never a flip against the server's set.
 */
class TagRepositoryFavoriteTest {

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
        coEvery { tagDao.getTagsForAccount(account.value) } returns listOf(
            ConversationTagEntity(
                id = 1L,
                tag = SAVED_TAG,
                user = "user-1",
                description = null,
                count = 0,
                position = 0,
                createdAt = 0L,
                updatedAt = 0L,
                accountId = account.value,
            ),
        )
    }

    private fun serverHas(conversationId: String, vararg tags: String) {
        coEvery { conversationsApi.getConversation(conversationId) } returns
            Conversation(conversationId = conversationId, tags = tags.toList())
    }

    @Test
    fun `favoriting adds SAVED_TAG to the server's set`() = runTest {
        serverHas("convo-1", "work", "web-only")
        coEvery { tagsApi.updateConversationTags(any(), any()) } returns listOf("work", "web-only", SAVED_TAG)

        val result = repository.setConversationTag("convo-1", SAVED_TAG, attached = true)

        assertThat(result).isInstanceOf(Result.Success::class.java)
        coVerify(exactly = 1) {
            tagsApi.updateConversationTags("convo-1", listOf("work", "web-only", SAVED_TAG))
        }
        coVerify(exactly = 1) {
            conversationDao.updateTags("convo-1", """["work","web-only","Saved"]""", any(), account.value)
        }
    }

    @Test
    fun `un-favoriting removes SAVED_TAG and keeps the rest of the server's set`() = runTest {
        serverHas("convo-1", "web-only", SAVED_TAG)
        coEvery { tagsApi.updateConversationTags(any(), any()) } returns listOf("web-only")

        repository.setConversationTag("convo-1", SAVED_TAG, attached = false)

        coVerify(exactly = 1) { tagsApi.updateConversationTags("convo-1", listOf("web-only")) }
    }

    @Test
    fun `un-favoriting a chat whose only tag is SAVED_TAG sends an empty set`() = runTest {
        serverHas("convo-1", SAVED_TAG)
        coEvery { tagsApi.updateConversationTags(any(), any()) } returns emptyList()

        repository.setConversationTag("convo-1", SAVED_TAG, attached = false)

        coVerify(exactly = 1) { tagsApi.updateConversationTags("convo-1", emptyList()) }
    }

    @Test
    fun `a failed favorite PUT skips the local update`() = runTest {
        serverHas("convo-1")
        coEvery { tagsApi.updateConversationTags(any(), any()) } throws RuntimeException("boom")

        val result = repository.setConversationTag("convo-1", SAVED_TAG, attached = true)

        assertThat(result).isInstanceOf(Result.Error::class.java)
        coVerify(exactly = 0) { conversationDao.updateTags(any(), any(), any(), any()) }
    }

    @Test
    fun `favoriting a chat the server already has as a favorite drops nothing`() = runTest {
        // A row that showed no star yet (favorited on the web since the last sync): the user asked
        // to add it, so it must not come off. The read is cached, so the row shows the star.
        serverHas("convo-1", "web-only", SAVED_TAG)

        val result = repository.setConversationTag("convo-1", SAVED_TAG, attached = true)

        coVerify(exactly = 0) { tagsApi.updateConversationTags(any(), any()) }
        coVerify(exactly = 1) {
            conversationDao.upsert(match<ConversationEntity> { it.tags == """["web-only","Saved"]""" })
        }
        assertThat((result as Result.Success).data).containsExactly("web-only", SAVED_TAG).inOrder()
    }

    @Test
    fun `two favorite taps in a row leave the chat a favorite`() = runTest {
        val stored = AtomicReference(listOf("web-only"))
        coEvery { conversationsApi.getConversation("convo-1") } answers {
            Conversation(conversationId = "convo-1", tags = stored.get())
        }
        coEvery { tagsApi.updateConversationTags("convo-1", any()) } answers {
            stored.set(secondArg())
            stored.get()
        }

        listOf(
            async { repository.setConversationTag("convo-1", SAVED_TAG, attached = true) },
            async { repository.setConversationTag("convo-1", SAVED_TAG, attached = true) },
        ).awaitAll()

        assertThat(stored.get()).containsExactly("web-only", SAVED_TAG).inOrder()
        coVerify(exactly = 1) { tagsApi.updateConversationTags(any(), any()) }
    }
}
