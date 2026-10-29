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
import com.garfiec.librechat.core.model.ConversationTag
import com.garfiec.librechat.core.model.SAVED_TAG
import com.garfiec.librechat.core.network.api.ConversationsApi
import com.garfiec.librechat.core.network.api.TagsApi
import com.google.common.truth.Truth.assertThat
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TagRepositoryImplTest {

    private val tagsApi = mockk<TagsApi>(relaxed = true)
    private val tagDao = mockk<ConversationTagDao>(relaxed = true)
    private val conversationsApi = mockk<ConversationsApi>(relaxed = true)
    private val conversationDao = mockk<ConversationDao>(relaxed = true)
    private val account = AccountId("srv:user-1")
    private val activeAccountProvider = InMemoryActiveAccountProvider(AccountState.Resolved(account))
    private val changes = TagChangeSignal()

    private lateinit var repository: TagRepositoryImpl

    private val serverTags = listOf(
        ConversationTag(tag = "work", count = 3, position = 0),
        ConversationTag(tag = "personal", count = 2, position = 1),
        ConversationTag(tag = SAVED_TAG, count = 5, position = 2),
    )

    @Before
    fun setup() {
        repository = TagRepositoryImpl(
            tagsApi = tagsApi,
            tagDao = tagDao,
            conversationsApi = conversationsApi,
            conversationDao = conversationDao,
            activeAccountProvider = activeAccountProvider,
            changes = changes,
        )
        // Every name these tests attach already exists, unless a test says otherwise.
        coEvery { tagDao.getTagsForAccount(account.value) } returns
            listOf("work", "personal", "web-only", SAVED_TAG).mapIndexed { i, name -> tagRow(i + 1L, name, i) }
    }

    private fun tagRow(id: Long, name: String, position: Int, count: Int = 0) = ConversationTagEntity(
        id = id,
        tag = name,
        user = "user-1",
        description = null,
        count = count,
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

    private fun serverHas(conversationId: String, vararg tags: String) {
        coEvery { conversationsApi.getConversation(conversationId) } returns
            Conversation(conversationId = conversationId, tags = tags.toList())
    }

    // --- refreshTags / observeTags ---

    @Test
    fun `refreshTags fetches from api and replaces this account's tags, stamping accountId`() = runTest {
        coEvery { tagsApi.getTags() } returns serverTags
        coEvery { tagDao.replaceAllForAccount(any(), any()) } just Runs

        val result = repository.refreshTags()

        assertThat(result).isInstanceOf(Result.Success::class.java)
        coVerify(exactly = 1) { tagsApi.getTags() }
        coVerify(exactly = 1) {
            tagDao.replaceAllForAccount(
                account.value,
                match { entities ->
                    entities.size == 3 &&
                        entities.map { it.tag }.containsAll(listOf("work", "personal", SAVED_TAG)) &&
                        entities.all { it.accountId == account.value }
                },
            )
        }
    }

    @Test
    fun `refreshTags drops blank and missing names before they reach the cache`() = runTest {
        coEvery { tagsApi.getTags() } returns serverTags + ConversationTag(tag = null) + ConversationTag(tag = "  ")
        val written = slot<List<ConversationTagEntity>>()
        coEvery { tagDao.replaceAllForAccount(any(), capture(written)) } just Runs

        repository.refreshTags()

        assertThat(written.captured.map { it.tag }).containsExactly("work", "personal", SAVED_TAG)
    }

    @Test
    fun `refreshTags returns Error when api throws`() = runTest {
        coEvery { tagsApi.getTags() } throws RuntimeException("network down")

        val result = repository.refreshTags()

        assertThat(result).isInstanceOf(Result.Error::class.java)
        coVerify(exactly = 0) { tagDao.replaceAllForAccount(any(), any()) }
    }

    @Test
    fun `refreshTags does not bump the revision, so a screen refreshing on it can't loop`() = runTest {
        coEvery { tagsApi.getTags() } returns serverTags

        repository.refreshTags()

        assertThat(repository.revision.value).isEqualTo(0L)
    }

    @Test
    fun `observeTags maps dao entities to domain models sorted by position`() = runTest {
        val entities = listOf(tagRow(1, "work", 0, count = 3), tagRow(2, "personal", 1, count = 2))
        every { tagDao.observeTagsForAccount(account.value) } returns flowOf(entities)

        val result = repository.observeTags().first()

        assertThat(result).hasSize(2)
        assertThat(result.map { it.tag }).containsExactly("work", "personal").inOrder()
        assertThat(result.map { it.count }).containsExactly(3, 2).inOrder()
    }

    @Test
    fun `observeTags drops a blank row left in the cache`() = runTest {
        every { tagDao.observeTagsForAccount(account.value) } returns
            flowOf(listOf(tagRow(1, "", 0), tagRow(2, "work", 1)))

        assertThat(repository.observeTags().first().map { it.tag }).containsExactly("work")
    }

    @Test
    fun `bookmarks excludes Saved and blank names and keeps the order`() {
        val tags = listOf(
            ConversationTag(tag = "b", position = 0),
            ConversationTag(tag = SAVED_TAG, position = 1),
            ConversationTag(tag = null, position = 2),
            ConversationTag(tag = "a", position = 3),
        )

        assertThat(tags.bookmarks().map { it.tag }).containsExactly("b", "a").inOrder()
    }

    // --- setConversationTag: read-then-write ---

    @Test
    fun `attaching keeps tags set elsewhere, which the cache never saw`() = runTest {
        serverHas("convo-1", "web-only", SAVED_TAG)
        coEvery { tagsApi.updateConversationTags(any(), any()) } returns listOf("web-only", SAVED_TAG, "work")

        val result = repository.setConversationTag("convo-1", "work", attached = true)

        coVerify(exactly = 1) {
            tagsApi.updateConversationTags("convo-1", listOf("web-only", SAVED_TAG, "work"))
        }
        assertThat((result as Result.Success).data).containsExactly("web-only", SAVED_TAG, "work").inOrder()
    }

    @Test
    fun `detaching sends the server's set minus the one tag`() = runTest {
        serverHas("convo-1", "web-only", "work")
        coEvery { tagsApi.updateConversationTags(any(), any()) } returns listOf("web-only")

        repository.setConversationTag("convo-1", "work", attached = false)

        coVerify(exactly = 1) { tagsApi.updateConversationTags("convo-1", listOf("web-only")) }
    }

    @Test
    fun `the PUT's returned list is what gets cached, not the one sent`() = runTest {
        serverHas("convo-1")
        coEvery { tagsApi.updateConversationTags(any(), any()) } returns listOf("work", "added-by-server")

        repository.setConversationTag("convo-1", "work", attached = true)

        coVerify(exactly = 1) {
            conversationDao.updateTags("convo-1", """["work","added-by-server"]""", any(), account.value)
        }
    }

    @Test
    fun `the server read is cached with its tags, replacing the stale cached ones`() = runTest {
        serverHas("convo-1", "web-only")
        coEvery { tagsApi.updateConversationTags(any(), any()) } returns listOf("web-only", "work")
        val upserted = slot<ConversationEntity>()
        coEvery { conversationDao.upsert(capture(upserted)) } just Runs

        repository.setConversationTag("convo-1", "work", attached = true)

        assertThat(upserted.captured.tags).isEqualTo("""["web-only"]""")
        assertThat(upserted.captured.accountId).isEqualTo(account.value)
    }

    @Test
    fun `a failed PUT writes nothing to the chat's cached tags and doesn't bump`() = runTest {
        serverHas("convo-1", "web-only")
        coEvery { tagsApi.updateConversationTags(any(), any()) } throws RuntimeException("boom")

        val result = repository.setConversationTag("convo-1", "work", attached = true)

        assertThat(result).isInstanceOf(Result.Error::class.java)
        coVerify(exactly = 0) { conversationDao.updateTags(any(), any(), any(), any()) }
        coVerify(exactly = 0) { tagsApi.getTags() }
        assertThat(repository.revision.value).isEqualTo(0L)
    }

    @Test
    fun `a failed read sends no PUT`() = runTest {
        coEvery { conversationsApi.getConversation("convo-1") } throws RuntimeException("offline")

        val result = repository.setConversationTag("convo-1", "work", attached = true)

        assertThat(result).isInstanceOf(Result.Error::class.java)
        coVerify(exactly = 0) { tagsApi.updateConversationTags(any(), any()) }
    }

    @Test
    fun `a write that changes nothing on the server sends no PUT`() = runTest {
        serverHas("convo-1", "work")

        val result = repository.setConversationTag("convo-1", "work", attached = true)

        coVerify(exactly = 0) { tagsApi.updateConversationTags(any(), any()) }
        assertThat((result as Result.Success).data).containsExactly("work")
    }

    @Test
    fun `a successful write refreshes the tag list and bumps the revision`() = runTest {
        serverHas("convo-1")
        coEvery { tagsApi.updateConversationTags(any(), any()) } returns listOf("work")

        repository.setConversationTag("convo-1", "work", attached = true)

        coVerifyOrder {
            tagsApi.updateConversationTags("convo-1", listOf("work"))
            tagsApi.getTags()
        }
        assertThat(repository.revision.value).isEqualTo(1L)
    }

    @Test
    fun `a name the account doesn't have is created through POST, before the chat PUT`() = runTest {
        serverHas("convo-1")
        coEvery { tagsApi.updateConversationTags(any(), any()) } returns listOf("brand new")

        repository.setConversationTag("convo-1", "brand new", attached = true)

        coVerifyOrder {
            tagsApi.createTag("brand new", null, false, null)
            tagsApi.updateConversationTags("convo-1", listOf("brand new"))
        }
    }

    @Test
    fun `an existing name is not re-created`() = runTest {
        serverHas("convo-1")
        coEvery { tagsApi.updateConversationTags(any(), any()) } returns listOf("work")

        repository.setConversationTag("convo-1", "work", attached = true)

        coVerify(exactly = 0) { tagsApi.createTag(any(), any(), any(), any()) }
    }

    @Test
    fun `blank names are refused, and blank server entries dropped`() = runTest {
        assertThat(repository.setConversationTag("convo-1", "  ", attached = true)).isInstanceOf(Result.Error::class.java)
        coVerify(exactly = 0) { conversationsApi.getConversation(any()) }

        serverHas("convo-1", "", "web-only")
        coEvery { tagsApi.updateConversationTags(any(), any()) } returns listOf("web-only", "work", "")
        val result = repository.setConversationTag("convo-1", " work ", attached = true)

        coVerify { tagsApi.updateConversationTags("convo-1", listOf("web-only", "work")) }
        assertThat((result as Result.Success).data).containsExactly("web-only", "work").inOrder()
    }

    @Test
    fun `concurrent writes to one chat read and write one after the other`() = runTest {
        // The server holds whatever the last PUT stored; each read must see the previous write.
        var stored = listOf<String>()
        coEvery { conversationsApi.getConversation("convo-1") } answers {
            Conversation(conversationId = "convo-1", tags = stored)
        }
        coEvery { tagsApi.updateConversationTags("convo-1", any()) } answers {
            stored = secondArg()
            stored
        }

        listOf(
            async { repository.setConversationTag("convo-1", "work", attached = true) },
            async { repository.setConversationTag("convo-1", "personal", attached = true) },
        ).awaitAll()

        assertThat(stored).containsExactly("work", "personal")
    }

    // --- toggleFavorite ---

    @Test
    fun `toggleFavorite adds SAVED_TAG to the server's set when the row showed it off`() = runTest {
        serverHas("convo-1", "work", "web-only")
        coEvery { tagsApi.updateConversationTags(any(), any()) } returns listOf("work", "web-only", SAVED_TAG)

        val result = repository.toggleFavorite("convo-1", currentTags = listOf("work"))

        assertThat(result).isInstanceOf(Result.Success::class.java)
        coVerify(exactly = 1) {
            tagsApi.updateConversationTags("convo-1", listOf("work", "web-only", SAVED_TAG))
        }
        coVerify(exactly = 1) {
            conversationDao.updateTags("convo-1", """["work","web-only","Saved"]""", any(), account.value)
        }
    }

    @Test
    fun `toggleFavorite removes SAVED_TAG and keeps the rest of the server's set`() = runTest {
        serverHas("convo-1", "web-only", SAVED_TAG)
        coEvery { tagsApi.updateConversationTags(any(), any()) } returns listOf("web-only")

        repository.toggleFavorite("convo-1", currentTags = listOf(SAVED_TAG))

        coVerify(exactly = 1) { tagsApi.updateConversationTags("convo-1", listOf("web-only")) }
    }

    @Test
    fun `toggleFavorite removes SAVED_TAG when it is the only tag`() = runTest {
        serverHas("convo-1", SAVED_TAG)
        coEvery { tagsApi.updateConversationTags(any(), any()) } returns emptyList()

        repository.toggleFavorite("convo-1", currentTags = listOf(SAVED_TAG))

        coVerify(exactly = 1) { tagsApi.updateConversationTags("convo-1", emptyList()) }
    }

    @Test
    fun `toggleFavorite returns Error when api throws and skips local update`() = runTest {
        serverHas("convo-1")
        coEvery { tagsApi.updateConversationTags(any(), any()) } throws RuntimeException("boom")

        val result = repository.toggleFavorite("convo-1", currentTags = emptyList())

        assertThat(result).isInstanceOf(Result.Error::class.java)
        coVerify(exactly = 0) { conversationDao.updateTags(any(), any(), any(), any()) }
    }

    // --- getConversationTags ---

    @Test
    fun `getConversationTags returns and caches the server's tags`() = runTest {
        serverHas("convo-1", "web-only", SAVED_TAG)
        val upserted = slot<ConversationEntity>()
        coEvery { conversationDao.upsert(capture(upserted)) } just Runs

        val result = repository.getConversationTags("convo-1")

        assertThat((result as Result.Success).data).containsExactly("web-only", SAVED_TAG).inOrder()
        assertThat(upserted.captured.tags).isEqualTo("""["web-only","Saved"]""")
    }

    // --- create / update / delete ---

    @Test
    fun `createTag posts the trimmed name, refreshes and bumps`() = runTest {
        coEvery { tagsApi.createTag(any(), any(), any(), any()) } returns ConversationTag(tag = "Recipes")

        val result = repository.createTag("  Recipes ", description = "  ")

        coVerify(exactly = 1) { tagsApi.createTag("Recipes", null, false, null) }
        coVerify(exactly = 1) { tagsApi.getTags() }
        assertThat((result as Result.Success).data.tag).isEqualTo("Recipes")
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
    fun `updateTag sends the fields given, refreshes and bumps`() = runTest {
        coEvery { tagsApi.updateTag(any(), any(), any(), any()) } returns ConversationTag(tag = "work")

        repository.updateTag("work", description = "")

        coVerify(exactly = 1) { tagsApi.updateTag("work", null, "", null) }
        coVerify(exactly = 1) { tagsApi.getTags() }
        assertThat(repository.revision.value).isEqualTo(1L)
    }

    @Test
    fun `a rename follows in the cached chats without touching their order`() = runTest {
        coEvery { tagsApi.updateTag(any(), any(), any(), any()) } returns ConversationTag(tag = "job")
        coEvery { conversationDao.taggedForAccount(account.value) } returns listOf(
            chatRow("a", """["work","Saved"]"""),
            chatRow("b", """["personal"]"""),
        )

        repository.updateTag("work", newName = " job ")

        coVerify(exactly = 1) { tagsApi.updateTag("work", "job", null, null) }
        coVerify(exactly = 1) { conversationDao.replaceTags("a", """["job","Saved"]""", account.value) }
        coVerify(exactly = 0) { conversationDao.replaceTags("b", any(), any()) }
        coVerify(exactly = 0) { conversationDao.updateTags(any(), any(), any(), any()) }
    }

    @Test
    fun `a failed update changes nothing locally and doesn't bump`() = runTest {
        coEvery { tagsApi.updateTag(any(), any(), any(), any()) } throws RuntimeException("500")

        val result = repository.updateTag("work", newName = "personal")

        assertThat(result).isInstanceOf(Result.Error::class.java)
        coVerify(exactly = 0) { conversationDao.replaceTags(any(), any(), any()) }
        assertThat(repository.revision.value).isEqualTo(0L)
    }

    @Test
    fun `updateTag refuses a blank new name`() = runTest {
        assertThat(repository.updateTag("work", newName = "  ")).isInstanceOf(Result.Error::class.java)
        coVerify(exactly = 0) { tagsApi.updateTag(any(), any(), any(), any()) }
    }

    @Test
    fun `deleteTag takes the tag off the cached chats, refreshes and bumps`() = runTest {
        coEvery { conversationDao.taggedForAccount(account.value) } returns listOf(
            chatRow("a", """["work","Saved"]"""),
            chatRow("b", """["personal"]"""),
        )

        val result = repository.deleteTag("work")

        assertThat(result).isInstanceOf(Result.Success::class.java)
        coVerify(exactly = 1) { tagsApi.deleteTag("work") }
        coVerify(exactly = 1) { conversationDao.replaceTags("a", """["Saved"]""", account.value) }
        coVerify(exactly = 0) { conversationDao.replaceTags("b", any(), any()) }
        coVerify(exactly = 1) { tagsApi.getTags() }
        assertThat(repository.revision.value).isEqualTo(1L)
    }

    @Test
    fun `a failed delete leaves the cached chats alone`() = runTest {
        coEvery { tagsApi.deleteTag(any()) } throws RuntimeException("404")

        assertThat(repository.deleteTag("work")).isInstanceOf(Result.Error::class.java)
        coVerify(exactly = 0) { conversationDao.replaceTags(any(), any(), any()) }
    }

    // --- reorderTag ---

    /** Room `[A0, Saved1, B2, C3]`: Saved sits between bookmarks, hidden from the list. */
    private val ordered = listOf(
        tagRow(1, "A", 0),
        tagRow(2, SAVED_TAG, 1),
        tagRow(3, "B", 2),
        tagRow(4, "C", 3),
    )

    @Test
    fun `reorder sends the stored position of the tag in the target slot`() = runTest {
        coEvery { tagDao.getTagsForAccount(account.value) } returns ordered

        repository.reorderTag("C", toIndex = 1)
        coVerify(exactly = 1) { tagsApi.updateTag("C", null, null, 2) }

        repository.reorderTag("B", toIndex = 0)
        coVerify(exactly = 1) { tagsApi.updateTag("B", null, null, 0) }
    }

    @Test
    fun `reorder applies the server's shift to the cache at once`() = runTest {
        coEvery { tagDao.getTagsForAccount(account.value) } returns ordered
        val written = slot<List<ConversationTagEntity>>()
        coEvery { tagDao.upsertAll(capture(written)) } just Runs

        repository.reorderTag("A", toIndex = 2)

        // A moves to C's position (3); everything in (0, 3] shifts down one.
        assertThat(written.captured.sortedBy { it.position }.map { it.tag })
            .containsExactly(SAVED_TAG, "B", "C", "A").inOrder()
        coVerify(exactly = 1) { tagsApi.updateTag("A", null, null, 3) }
        assertThat(repository.revision.value).isEqualTo(1L)
    }

    @Test
    fun `a refused reorder puts the cached order back`() = runTest {
        coEvery { tagDao.getTagsForAccount(account.value) } returns ordered
        coEvery { tagsApi.updateTag(any(), any(), any(), any()) } throws RuntimeException("500")

        val result = repository.reorderTag("C", toIndex = 0)

        assertThat(result).isInstanceOf(Result.Error::class.java)
        coVerifyOrder {
            tagDao.upsertAll(any())
            tagDao.replaceAllForAccount(account.value, ordered)
        }
        assertThat(repository.revision.value).isEqualTo(0L)
    }

    @Test
    fun `reorder to the same slot or out of range sends nothing`() = runTest {
        coEvery { tagDao.getTagsForAccount(account.value) } returns ordered

        assertThat(repository.reorderTag("B", toIndex = 1)).isInstanceOf(Result.Success::class.java)
        assertThat(repository.reorderTag("B", toIndex = 3)).isInstanceOf(Result.Error::class.java)
        assertThat(repository.reorderTag(SAVED_TAG, toIndex = 0)).isInstanceOf(Result.Error::class.java)
        coVerify(exactly = 0) { tagsApi.updateTag(any(), any(), any(), any()) }
        coVerify(exactly = 0) { tagDao.upsertAll(any()) }
    }

    @Test
    fun `clearCache deletes only the active account's tags`() = runTest {
        coEvery { tagDao.deleteAllForAccount(any()) } just Runs

        repository.clearCache()

        coVerify(exactly = 1) { tagDao.deleteAllForAccount(account.value) }
    }
}
