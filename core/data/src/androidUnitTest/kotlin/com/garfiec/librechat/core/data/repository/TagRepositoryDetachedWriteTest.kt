package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.common.identity.AccountState
import com.garfiec.librechat.core.common.identity.InMemoryActiveAccountProvider
import com.garfiec.librechat.core.data.db.dao.ConversationDao
import com.garfiec.librechat.core.data.db.dao.ConversationTagDao
import com.garfiec.librechat.core.data.db.entity.ConversationTagEntity
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.ConversationTag
import com.garfiec.librechat.core.network.api.ConversationsApi
import com.garfiec.librechat.core.network.api.TagsApi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * A write the screen asked for finishes when the screen goes: cancelling the caller (its
 * ViewModel cleared) ends only its wait, never the requests still to go or the follow-up.
 */
class TagRepositoryDetachedWriteTest {

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

    private fun tagRow(id: Long, name: String, position: Int) = ConversationTagEntity(
        id = id,
        tag = name,
        user = "user-1",
        description = null,
        count = 0,
        position = position,
        createdAt = 0L,
        updatedAt = 0L,
        accountId = account.value,
    )

    @Test
    fun `a move whose caller is cancelled partway through the spread still sends the rest and re-reads`() = runTest {
        // Tied at 0, as the old mobile picker left them: the move first spreads them, one PUT each.
        val tied = listOf(tagRow(1, "A", 0), tagRow(2, "B", 0), tagRow(3, "C", 0))
        coEvery { tagDao.getTagsForAccount(account.value) } returns tied
        val puts = AtomicInteger(0)
        val firstPut = CompletableDeferred<Unit>()
        coEvery { tagsApi.updateTag(any(), any(), any(), any()) } coAnswers {
            if (puts.getAndIncrement() == 0) firstPut.await()
            ConversationTag(tag = firstArg())
        }

        val caller = launch(start = CoroutineStart.UNDISPATCHED) { repository.reorderTag("C", toIndex = 0) }
        waitUntil { puts.get() >= 1 }
        caller.cancelAndJoin()
        firstPut.complete(Unit)

        // Three spread PUTs and the move itself, then the server's order is read back.
        waitUntil { puts.get() == 4 }
        // C goes twice: once in the spread, once for the move.
        coVerify(exactly = 2) { tagsApi.updateTag("C", null, null, any()) }
        coVerify(timeout = WAIT_MS, exactly = 1) { tagsApi.getTags() }
    }

    @Test
    fun `a drop whose caller is cancelled during the list re-read is still sent`() = runTest {
        coEvery { tagDao.getTagsForAccount(account.value) } returns
            listOf(tagRow(1, "A", 0), tagRow(2, "B", 1), tagRow(3, "C", 2))
        val read = CompletableDeferred<Unit>()
        val reading = CompletableDeferred<Unit>()
        coEvery { tagsApi.getTags() } coAnswers {
            reading.complete(Unit)
            read.await()
            listOf("A", "B", "C").mapIndexed { i, name -> ConversationTag(tag = name, position = i) }
        }

        // The user drops C at the top and leaves the index before the re-read answers.
        val caller = launch(start = CoroutineStart.UNDISPATCHED) { repository.reorderAfter("C", after = null) }
        withContext(Dispatchers.Default) { withTimeout(WAIT_MS) { reading.await() } }
        caller.cancelAndJoin()
        read.complete(Unit)

        coVerify(timeout = WAIT_MS, exactly = 1) { tagsApi.updateTag("C", null, null, 0) }
    }

    @Test
    fun `a tick whose caller is cancelled during the read is still saved`() = runTest {
        val read = CompletableDeferred<Unit>()
        val reading = CompletableDeferred<Unit>()
        coEvery { conversationsApi.getConversation("convo-1") } coAnswers {
            reading.complete(Unit)
            read.await()
            Conversation(conversationId = "convo-1", tags = emptyList())
        }
        coEvery { tagsApi.updateConversationTags("convo-1", any()) } returns listOf("Work")

        val caller = launch(start = CoroutineStart.UNDISPATCHED) {
            repository.setConversationTag("convo-1", "Work", attached = true)
        }
        withContext(Dispatchers.Default) { withTimeout(WAIT_MS) { reading.await() } }
        caller.cancelAndJoin()
        read.complete(Unit)

        coVerify(timeout = WAIT_MS, exactly = 1) { tagsApi.updateConversationTags("convo-1", listOf("Work")) }
        coVerify(timeout = WAIT_MS, exactly = 1) { tagsApi.getTags() }
    }

    @Test
    fun `a new bookmark whose caller is cancelled during the create is still added to the chat`() = runTest {
        val posted = CompletableDeferred<Unit>()
        val posting = CompletableDeferred<Unit>()
        coEvery { tagsApi.createTag("Recipes", any(), any(), any()) } coAnswers {
            posting.complete(Unit)
            posted.await()
            ConversationTag(tag = "Recipes")
        }
        coEvery { conversationsApi.getConversation("convo-1") } returns
            Conversation(conversationId = "convo-1", tags = emptyList())
        coEvery { tagsApi.updateConversationTags("convo-1", any()) } returns listOf("Recipes")

        val caller = launch(start = CoroutineStart.UNDISPATCHED) {
            repository.createTag("Recipes", addToConversationId = "convo-1")
        }
        withContext(Dispatchers.Default) { withTimeout(WAIT_MS) { posting.await() } }
        caller.cancelAndJoin()
        posted.complete(Unit)

        coVerify(timeout = WAIT_MS, exactly = 1) { tagsApi.updateConversationTags("convo-1", listOf("Recipes")) }
        coVerify(timeout = WAIT_MS, exactly = 1) { tagsApi.getTags() }
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
