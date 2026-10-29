package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.common.identity.AccountState
import com.garfiec.librechat.core.common.identity.InMemoryActiveAccountProvider
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.db.dao.ConversationDao
import com.garfiec.librechat.core.data.db.dao.ConversationTagDao
import com.garfiec.librechat.core.data.db.entity.ConversationTagEntity
import com.garfiec.librechat.core.model.ConversationTag
import com.garfiec.librechat.core.network.api.ConversationsApi
import com.garfiec.librechat.core.network.api.TagsApi
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Before
import org.junit.Test

/**
 * [TagRepository.reorderAfter]: a drop re-reads the server's order, works out its slot from it and
 * moves, one move at a time, in the order asked.
 */
class TagRepositoryReorderAfterTest {

    private val tagsApi = mockk<TagsApi>(relaxed = true)
    private val tagDao = mockk<ConversationTagDao>(relaxed = true)
    private val conversationsApi = mockk<ConversationsApi>(relaxed = true)
    private val conversationDao = mockk<ConversationDao>(relaxed = true)
    private val account = AccountId("srv:user-1")
    private val activeAccountProvider = InMemoryActiveAccountProvider(AccountState.Resolved(account))

    private lateinit var repository: TagRepositoryImpl

    @Before
    fun setup() {
        repository = TagRepositoryImpl(
            tagsApi = tagsApi,
            tagDao = tagDao,
            conversationsApi = conversationsApi,
            conversationDao = conversationDao,
            activeAccountProvider = activeAccountProvider,
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

    /**
     * The account's tag rows as Room holds them, kept by the mocked DAO: a refresh writes [server]'s
     * list in (Room numbering new rows), and a move's optimistic upsert and rollback land here too.
     */
    // Written from mock answers on the IO dispatcher and read from the test thread.
    @Volatile private var cache: List<ConversationTagEntity> = emptyList()

    @Volatile private var server: List<String> = emptyList()

    private fun cachedAndServed(vararg names: String) {
        server = names.toList()
        cache = names.mapIndexed { i, name -> tagRow(i + 1L, name, i) }
        coEvery { tagDao.getTagsForAccount(account.value) } answers { cache.sortedBy { it.position } }
        coEvery { tagDao.replaceAllForAccount(account.value, any()) } answers {
            cache = secondArg<List<ConversationTagEntity>>().mapIndexed { i, row ->
                if (row.id == 0L) row.copy(id = NEW_ROW_IDS + i) else row
            }
        }
        coEvery { tagDao.upsertAll(any()) } answers {
            val written = firstArg<List<ConversationTagEntity>>().associateBy { it.id }
            cache = cache.map { written[it.id] ?: it }
        }
        coEvery { tagsApi.getTags() } answers {
            server.mapIndexed { i, name -> ConversationTag(tag = name, position = i) }
        }
    }

    @Test
    fun `a move re-reads the server's list before it works out the slot`() = runTest {
        cachedAndServed("A", "B", "C", "D")
        // A was deleted on the web; the cache hasn't heard. The re-read brings that in.
        server = listOf("B", "C", "D")

        // D just below B.
        val result = repository.reorderAfter("D", "B")

        assertThat(result).isInstanceOf(Result.Success::class.java)
        coVerifyOrder {
            tagsApi.getTags()
            tagsApi.updateTag("D", null, null, 1)
        }
        coVerify(exactly = 0) { tagsApi.updateTag("D", null, null, 2) }
    }

    @Test
    fun `a move whose re-read fails sends nothing`() = runTest {
        cachedAndServed("A", "B", "C")
        coEvery { tagsApi.getTags() } throws RuntimeException("offline")

        assertThat(repository.reorderAfter("C", null)).isInstanceOf(Result.Error::class.java)
        coVerify(exactly = 0) { tagsApi.updateTag(any(), any(), any(), any()) }
    }

    @Test
    fun `a move to the top sends the first card's position`() = runTest {
        cachedAndServed("A", "B", "C")

        repository.reorderAfter("C", null)

        coVerify(exactly = 1) { tagsApi.updateTag("C", null, null, 0) }
    }

    @Test
    fun `a move whose card or anchor is gone, or that is already in place, sends nothing`() = runTest {
        cachedAndServed("A", "B", "C")

        assertThat(repository.reorderAfter("B", "D")).isInstanceOf(Result.Success::class.java)
        assertThat(repository.reorderAfter("D", "A")).isInstanceOf(Result.Success::class.java)
        assertThat(repository.reorderAfter("B", "A")).isInstanceOf(Result.Success::class.java)
        coVerify(exactly = 0) { tagsApi.updateTag(any(), any(), any(), any()) }
    }

    @Test
    fun `a second move waits for the first, and a refused first leaves it the order the cache went back to`() =
        runTest {
            cachedAndServed("A", "B", "C", "D")
            val firstPut = CompletableDeferred<Unit>()
            coEvery { tagsApi.updateTag("A", any(), any(), any()) } coAnswers {
                firstPut.await()
                throw IllegalStateException("500")
            }

            // A to the bottom, then B just below D.
            val first = async(start = CoroutineStart.UNDISPATCHED) { repository.reorderAfter("A", "D") }
            val second = async(start = CoroutineStart.UNDISPATCHED) { repository.reorderAfter("B", "D") }
            coVerify(timeout = WAIT_MS, exactly = 1) { tagsApi.updateTag("A", null, null, 3) }
            // Real time for B's chain, which runs off the test's clock: it must not have re-read yet.
            withContext(Dispatchers.Default) { delay(SETTLE_MS) }
            coVerify(exactly = 1) { tagsApi.getTags() }
            coVerify(exactly = 0) { tagsApi.updateTag("B", any(), any(), any()) }

            // Refused: the cache is A, B, C, D again, where below D is position 3, not 2.
            firstPut.complete(Unit)

            assertThat(first.await()).isInstanceOf(Result.Error::class.java)
            assertThat(second.await()).isInstanceOf(Result.Success::class.java)
            coVerify(exactly = 1) { tagsApi.updateTag("B", null, null, 3) }
            coVerify(exactly = 0) { tagsApi.updateTag("B", null, null, 2) }
        }

    @Test
    fun `a second move works out its slot from the order the first one left`() = runTest {
        cachedAndServed("A", "B", "C", "D")
        val firstPut = CompletableDeferred<Unit>()
        coEvery { tagsApi.updateTag("A", any(), any(), any()) } coAnswers {
            firstPut.await()
            // Landed: the server now holds A at the bottom.
            server = listOf("B", "C", "D", "A")
            ConversationTag(tag = "A", position = 3)
        }

        // A to the bottom, then B just below D.
        val first = async(start = CoroutineStart.UNDISPATCHED) { repository.reorderAfter("A", "D") }
        val second = async(start = CoroutineStart.UNDISPATCHED) { repository.reorderAfter("B", "D") }
        coVerify(timeout = WAIT_MS, exactly = 1) { tagsApi.updateTag("A", null, null, 3) }
        // Real time for B's chain, which runs off the test's clock: it must wait for A, re-read unasked.
        withContext(Dispatchers.Default) { delay(SETTLE_MS) }
        coVerify(exactly = 1) { tagsApi.getTags() }
        coVerify(exactly = 0) { tagsApi.updateTag("B", any(), any(), any()) }

        firstPut.complete(Unit)

        assertThat(first.await()).isInstanceOf(Result.Success::class.java)
        assertThat(second.await()).isInstanceOf(Result.Success::class.java)
        // Read after A: B, C, D, A, so below D is position 2. Read before A it would be 3.
        coVerify(exactly = 1) { tagsApi.updateTag("B", null, null, 2) }
        coVerify(exactly = 0) { tagsApi.updateTag("B", null, null, 3) }
    }

    @Test
    fun `a move asked under one account sends nothing once another is live`() = runTest {
        cachedAndServed("A", "B", "C")
        val gate = CompletableDeferred<Unit>()
        coEvery { tagsApi.getTags() } coAnswers {
            gate.await()
            server.mapIndexed { i, name -> ConversationTag(tag = name, position = i) }
        }
        // Holds the move queue: the one asked behind it finds the other account live on its turn.
        val holder = async(start = CoroutineStart.UNDISPATCHED) { repository.reorderAfter("C", null) }
        val queued = async(start = CoroutineStart.UNDISPATCHED) { repository.reorderAfter("B", null) }
        coVerify(timeout = WAIT_MS) { tagsApi.getTags() }
        activeAccountProvider.set(AccountId("srv:user-2"))
        gate.complete(Unit)

        holder.await()
        val result = queued.await()

        assertThat(result).isInstanceOf(Result.Error::class.java)
        assertThat((result as Result.Error).exception).isInstanceOf(AccountSwitchedException::class.java)
        coVerify(exactly = 0) { tagsApi.updateTag("B", any(), any(), any()) }
    }

    @Test
    fun `placing after an anchor counts in the order without the moved card`() {
        val order = listOf("A", "B", "C", "D")
        assertThat(placeAfter(order, "B", "D")).isEqualTo(3)
        assertThat(placeAfter(order, "D", null)).isEqualTo(0)
        assertThat(placeAfter(order, "A", "C")).isEqualTo(2)
        assertThat(placeAfter(order, "C", "A")).isEqualTo(1)
        assertThat(placeAfter(order, "E", "A")).isNull()
        assertThat(placeAfter(order, "A", "E")).isNull()
    }

    private companion object {
        const val WAIT_MS = 5_000L
        const val SETTLE_MS = 300L
        const val NEW_ROW_IDS = 100L
    }
}
