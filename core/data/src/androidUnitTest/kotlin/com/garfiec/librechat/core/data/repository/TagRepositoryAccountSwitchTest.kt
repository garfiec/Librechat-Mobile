package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.common.identity.AccountState
import com.garfiec.librechat.core.common.identity.InMemoryActiveAccountProvider
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.db.dao.ConversationDao
import com.garfiec.librechat.core.data.db.dao.ConversationTagDao
import com.garfiec.librechat.core.data.db.entity.ConversationTagEntity
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.ConversationTag
import com.garfiec.librechat.core.network.api.ConversationsApi
import com.garfiec.librechat.core.network.api.TagsApi
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * A per-chat tag write is for the account it was asked under. Once another account is live, no
 * further leg (read, create, PUT) goes out: the transport would send it to the new account's server.
 */
class TagRepositoryAccountSwitchTest {

    private val tagsApi = mockk<TagsApi>(relaxed = true)
    private val tagDao = mockk<ConversationTagDao>(relaxed = true)
    private val conversationsApi = mockk<ConversationsApi>(relaxed = true)
    private val conversationDao = mockk<ConversationDao>(relaxed = true)
    private val accounts = InMemoryActiveAccountProvider(AccountState.Resolved(AccountId("srv:user-1")))

    private lateinit var repository: TagRepositoryImpl

    @Before
    fun setup() {
        repository = TagRepositoryImpl(
            tagsApi = tagsApi,
            tagDao = tagDao,
            conversationsApi = conversationsApi,
            conversationDao = conversationDao,
            activeAccountProvider = accounts,
            changes = TagChangeSignal(),
            applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
    }

    private fun switchAccount() = accounts.set(AccountId("srv:user-2"))

    @Test
    fun `writes queued when the account switches send nothing more`() = runTest {
        val reads = AtomicInteger(0)
        val firstRead = CompletableDeferred<Unit>()
        coEvery { conversationsApi.getConversation("convo-1") } coAnswers {
            if (reads.getAndIncrement() == 0) firstRead.await()
            Conversation(conversationId = "convo-1", tags = emptyList())
        }

        val writes = listOf("work", "personal").map { name ->
            async(start = CoroutineStart.UNDISPATCHED) { repository.setConversationTag("convo-1", name, attached = true) }
        }
        waitUntil { reads.get() >= 1 }
        switchAccount()
        firstRead.complete(Unit)

        writes.awaitAll().forEach { assertThat(it).isInstanceOf(Result.Error::class.java) }
        // The queued one never read; the one under way stopped before its create and PUT.
        assertThat(reads.get()).isEqualTo(1)
        coVerify(exactly = 0) { tagsApi.createTag(any(), any(), any(), any()) }
        coVerify(exactly = 0) { tagsApi.updateConversationTags(any(), any()) }
        coVerify(exactly = 0) { tagsApi.getTags() }
    }

    @Test
    fun `a switch between the read and the create makes no bookmark on the new account`() = runTest {
        coEvery { conversationsApi.getConversation("convo-1") } answers {
            switchAccount()
            Conversation(conversationId = "convo-1", tags = emptyList())
        }

        val result = repository.setConversationTag("convo-1", "work", attached = true)

        assertThat(result).isInstanceOf(Result.Error::class.java)
        coVerify(exactly = 0) { tagsApi.createTag(any(), any(), any(), any()) }
        coVerify(exactly = 0) { tagsApi.updateConversationTags(any(), any()) }
    }

    @Test
    fun `a new bookmark's attach is not sent once the account switched during its create`() = runTest {
        coEvery { tagsApi.createTag(any(), any(), any(), any()) } answers {
            switchAccount()
            ConversationTag(tag = "Recipes")
        }

        val result = repository.createTag("Recipes", addToConversationId = "convo-1")

        assertThat((result as Result.Success).data.attachFailed).isTrue()
        coVerify(exactly = 0) { conversationsApi.getConversation(any()) }
        coVerify(exactly = 0) { tagsApi.updateConversationTags(any(), any()) }
    }

    @Test
    fun `a chat's tags read behind a write are not read from the new account's server`() = runTest {
        val reads = AtomicInteger(0)
        val firstRead = CompletableDeferred<Unit>()
        coEvery { conversationsApi.getConversation("convo-1") } coAnswers {
            if (reads.getAndIncrement() == 0) firstRead.await()
            Conversation(conversationId = "convo-1", tags = emptyList())
        }
        val write = async(start = CoroutineStart.UNDISPATCHED) {
            repository.setConversationTag("convo-1", "work", attached = true)
        }
        waitUntil { reads.get() >= 1 }
        // Asked under the old account, it waits for the write's lock.
        val read = async(start = CoroutineStart.UNDISPATCHED) { repository.getConversationTags("convo-1") }

        switchAccount()
        firstRead.complete(Unit)

        assertThat(write.await()).isInstanceOf(Result.Error::class.java)
        assertThat(read.await()).isInstanceOf(Result.Error::class.java)
        assertThat(reads.get()).isEqualTo(1)
    }

    @Test
    fun `a move stops at an account switch mid-spread, puts the old order back and reads no list`() = runTest {
        val tied = listOf("A", "B", "C").mapIndexed { i, name ->
            ConversationTagEntity(
                id = i + 1L,
                tag = name,
                user = "user-1",
                description = null,
                count = 0,
                position = 0,
                createdAt = 0L,
                updatedAt = 0L,
                accountId = "srv:user-1",
            )
        }
        coEvery { tagDao.getTagsForAccount("srv:user-1") } returns tied
        coEvery { tagsApi.updateTag(any(), any(), any(), any()) } answers {
            switchAccount()
            ConversationTag(tag = firstArg())
        }

        val result = repository.reorderTag("C", toIndex = 0)

        assertThat(result).isInstanceOf(Result.Error::class.java)
        coVerify(exactly = 1) { tagsApi.updateTag(any(), any(), any(), any()) }
        coVerify(exactly = 1) { tagDao.replaceAllForAccount("srv:user-1", tied) }
        coVerify(exactly = 0) { tagsApi.getTags() }
    }

    /** Real time: the repository's calls run on Dispatchers.IO, past the test's virtual clock. */
    private suspend fun waitUntil(condition: () -> Boolean) = withContext(Dispatchers.Default) {
        withTimeout(WAIT_MS) { while (!condition()) delay(POLL_MS) }
    }

    private companion object {
        const val WAIT_MS = 5_000L
        const val POLL_MS = 5L
    }
}
