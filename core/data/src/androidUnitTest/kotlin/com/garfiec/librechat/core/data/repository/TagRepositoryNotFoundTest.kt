package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.common.identity.AccountState
import com.garfiec.librechat.core.common.identity.InMemoryActiveAccountProvider
import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.db.dao.ConversationDao
import com.garfiec.librechat.core.data.db.dao.ConversationTagDao
import com.garfiec.librechat.core.data.db.entity.ConversationEntity
import com.garfiec.librechat.core.data.db.entity.ConversationTagEntity
import com.garfiec.librechat.core.network.api.ConversationsApi
import com.garfiec.librechat.core.network.api.TagsApi
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * A 404 from `PUT`/`DELETE /api/tags/:tag`: the bookmark was deleted or renamed elsewhere. A delete
 * counts as done; an edit or move stays an error but re-reads the list, so the stale card goes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TagRepositoryNotFoundTest {

    private val tagsApi = mockk<TagsApi>(relaxed = true)
    private val tagDao = mockk<ConversationTagDao>(relaxed = true)
    private val conversationsApi = mockk<ConversationsApi>(relaxed = true)
    private val conversationDao = mockk<ConversationDao>(relaxed = true)
    private val account = AccountId("srv:user-1")

    private lateinit var repository: TagRepositoryImpl

    private val notFound get() = ApiException(statusCode = 404, message = "Tag not found")

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

    @Test
    fun `a delete of a bookmark already gone counts as done, with the same cleanup`() = runTest {
        coEvery { tagsApi.deleteTag("work") } throws notFound
        coEvery { conversationDao.taggedForAccount(account.value) } returns listOf(chatRow("a", """["work","personal"]"""))
        val seen = mutableListOf<TagEvent>()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.events.collect { seen += it }
        }

        val result = repository.deleteTag("work")

        assertThat(result).isInstanceOf(Result.Success::class.java)
        coVerify(exactly = 1) { conversationDao.replaceTags("a", """["personal"]""", account.value) }
        coVerify(exactly = 1) { tagsApi.getTags() }
        assertThat(seen).containsExactly(TagEvent.Deleted("work"))
        assertThat(repository.revision.value).isEqualTo(1L)
        collector.cancel()
    }

    @Test
    fun `a delete refused for another reason is still an error`() = runTest {
        coEvery { tagsApi.deleteTag("work") } throws ApiException(statusCode = 500, message = "Server error")

        assertThat(repository.deleteTag("work")).isInstanceOf(Result.Error::class.java)
        coVerify(exactly = 0) { conversationDao.replaceTags(any(), any(), any()) }
        assertThat(repository.revision.value).isEqualTo(0L)
    }

    @Test
    fun `an edit of a bookmark gone from the server is an error that re-reads the list`() = runTest {
        coEvery { tagsApi.updateTag(any(), any(), any(), any()) } throws notFound

        val result = repository.updateTag("work", newName = "job", description = "notes")

        assertThat(result).isInstanceOf(Result.Error::class.java)
        coVerify(exactly = 0) { conversationDao.replaceTags(any(), any(), any()) }
        coVerify(exactly = 1) { tagsApi.getTags() }
        assertThat(repository.revision.value).isEqualTo(1L)
    }

    @Test
    fun `a failed edit for another reason re-reads nothing`() = runTest {
        coEvery { tagsApi.updateTag(any(), any(), any(), any()) } throws ApiException(statusCode = 500, message = "x")

        assertThat(repository.updateTag("work", description = "notes")).isInstanceOf(Result.Error::class.java)
        coVerify(exactly = 0) { tagsApi.getTags() }
        assertThat(repository.revision.value).isEqualTo(0L)
    }

    @Test
    fun `a rename the server refuses as a duplicate re-reads the list`() = runTest {
        // Upstream's updateConversationTag throws "Tag already exists", which the route sends as a 500.
        coEvery { tagsApi.updateTag(any(), any(), any(), any()) } throws
            ApiException(statusCode = 500, message = "Tag already exists")

        val result = repository.updateTag("job", newName = "Work")

        assertThat(result).isInstanceOf(Result.Error::class.java)
        coVerify(exactly = 0) { conversationDao.replaceTags(any(), any(), any()) }
        coVerify(exactly = 1) { tagsApi.getTags() }
        // Nothing changed on the server: the re-read reaches every screen through the cache alone.
        assertThat(repository.revision.value).isEqualTo(0L)
    }

    @Test
    fun `a move of a bookmark gone from the server puts the order back and re-reads the list`() = runTest {
        val ordered = listOf(tagRow(1, "A", 0), tagRow(2, "B", 1), tagRow(3, "C", 2))
        coEvery { tagDao.getTagsForAccount(account.value) } returns ordered
        coEvery { tagsApi.updateTag(any(), any(), any(), any()) } throws notFound

        val result = repository.reorderTag("C", toIndex = 0)

        assertThat(result).isInstanceOf(Result.Error::class.java)
        coVerifyOrder {
            tagDao.upsertAll(any())
            tagDao.replaceAllForAccount(account.value, ordered)
            tagsApi.getTags()
        }
        assertThat(repository.revision.value).isEqualTo(1L)
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

    private fun chatRow(id: String, tagsJson: String) = ConversationEntity(
        conversationId = id,
        title = "Title $id",
        user = "user-1",
        endpoint = null,
        endpointType = null,
        model = null,
        agentId = null,
        isArchived = false,
        tags = tagsJson,
        iconURL = null,
        greeting = null,
        modelParams = null,
        createdAt = 0L,
        updatedAt = 0L,
        lastSyncedAt = 0L,
        accountId = account.value,
    )
}
