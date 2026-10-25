package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.common.identity.AccountState
import com.garfiec.librechat.core.common.identity.InMemoryActiveAccountProvider
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.AccountRoster
import com.garfiec.librechat.core.data.db.dao.MessageDao
import com.garfiec.librechat.core.data.db.entity.MessageEntity
import com.garfiec.librechat.core.data.mapper.toEntity
import com.garfiec.librechat.core.data.mapper.toModel
import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.content.MessageContentPart
import com.garfiec.librechat.core.model.request.UpdateMessageRequest
import com.garfiec.librechat.core.network.api.MessagesApi
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * An indexed content-part edit (`PUT` with `index`).
 *
 * Two things cannot be seen from above this module and so are asserted here: that the request
 * carries the `index` at all (without it the server updates a flat `text` field that a reply
 * rendered from content parts never displays, so the edit reappears as the old text on the next
 * fetch), and that the cached row is rewritten locally — the PUT answers a sanitized subset of the
 * message that omits `content` entirely, so there is nothing in the response to cache.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MessageContentPartEditTest {

    private val messagesApi = mockk<MessagesApi>(relaxed = true)
    private val messageDao = mockk<MessageDao>(relaxed = true)
    private val roster = mockk<AccountRoster>(relaxed = true)
    private val account = AccountId("srv:user-a")

    private lateinit var repository: MessageRepositoryImpl

    @Before
    fun setup() {
        repository = MessageRepositoryImpl(
            messagesApi = messagesApi,
            messageDao = messageDao,
            activeAccountProvider = InMemoryActiveAccountProvider(AccountState.Resolved(account)),
            roster = roster,
            dispatcher = UnconfinedTestDispatcher(),
        )
        coEvery { roster.contains(any()) } returns true
        coEvery { messageDao.observeMessagesForAccount(any(), any()) } returns flowOf(emptyList())
    }

    private fun cached(vararg parts: MessageContentPart): MessageEntity =
        Message(
            messageId = "a1",
            conversationId = "c1",
            parentMessageId = "u1",
            content = parts.toList(),
        ).toEntity().copy(accountId = account.value)

    private fun textPart(text: String) = MessageContentPart(type = ContentType.TEXT, text = text)

    @Test
    fun `the request carries the part index`() = runTest {
        coEvery { messageDao.getByIdForAccount(any(), any()) } returns null
        val body = slot<UpdateMessageRequest>()
        coEvery { messagesApi.updateMessage("c1", "a1", capture(body)) } returns mockk(relaxed = true)

        val result = repository.updateMessageContentPart("c1", "a1", index = 2, text = "edited")

        assertThat(result).isInstanceOf(Result.Success::class.java)
        assertThat(body.captured.index).isEqualTo(2)
        assertThat(body.captured.text).isEqualTo("edited")
    }

    @Test
    fun `the flat-text edit still carries no index`() = runTest {
        val body = slot<UpdateMessageRequest>()
        coEvery { messagesApi.updateMessage("c1", "a1", capture(body)) } returns mockk(relaxed = true)

        repository.updateMessageText("c1", "a1", "edited")

        // Null is what selects the server's text branch; sending 0 would rewrite the first part.
        assertThat(body.captured.index).isNull()
    }

    @Test
    fun `the edited part is rewritten in the cache and its siblings are left alone`() = runTest {
        coEvery { messageDao.getByIdForAccount("a1", account.value) } returns
            cached(textPart("first"), textPart("second"), textPart("third"))
        val written = slot<MessageEntity>()
        coEvery { messageDao.upsertReindexed(capture(written)) } returns Unit

        repository.updateMessageContentPart("c1", "a1", index = 1, text = "second edited")

        val parts = written.captured.toModel().content!!
        assertThat(parts.map { it.text }).containsExactly("first", "second edited", "third").inOrder()
    }

    @Test
    fun `a reasoning part is written into think, not text`() = runTest {
        coEvery { messageDao.getByIdForAccount("a1", account.value) } returns
            cached(MessageContentPart(type = ContentType.THINK, think = "first thought"))
        val written = slot<MessageEntity>()
        coEvery { messageDao.upsertReindexed(capture(written)) } returns Unit

        repository.updateMessageContentPart("c1", "a1", index = 0, text = "a better thought")

        // The server writes the field the part's own type names; a `text` here would render the
        // reasoning as the answer on the next read and leave the old thought in place.
        val part = written.captured.toModel().content!!.single()
        assertThat(part.think).isEqualTo("a better thought")
        assertThat(part.text).isNull()
    }

    @Test
    fun `a part the server kept other fields on keeps them locally too`() = runTest {
        coEvery { messageDao.getByIdForAccount("a1", account.value) } returns
            cached(MessageContentPart(type = ContentType.TEXT, text = "answer", agentId = "agent_a"))
        val written = slot<MessageEntity>()
        coEvery { messageDao.upsertReindexed(capture(written)) } returns Unit

        repository.updateMessageContentPart("c1", "a1", index = 0, text = "edited")

        // The route spreads the new text over the stored part rather than replacing it, so a
        // comparison lane's attribution survives an edit. Dropping it here would re-attribute the
        // part to the primary agent until the next fetch.
        val part = written.captured.toModel().content!!.single()
        assertThat(part.text).isEqualTo("edited")
        assertThat(part.agentId).isEqualTo("agent_a")
    }

    @Test
    fun `the edit reaches the search index`() = runTest {
        coEvery { messageDao.getByIdForAccount("a1", account.value) } returns cached(textPart("aardvark"))

        repository.updateMessageContentPart("c1", "a1", index = 0, text = "zebra")

        // Through `upsertReindexed`, not `upsertAllIndexed`: the latter rewrites a search row only
        // when `updatedAt` moved, and a read-modify-write leaves it alone — so in-conversation
        // search would keep matching "aardvark" and never find "zebra" until a server refetch.
        coVerify(exactly = 1) { messageDao.upsertReindexed(any()) }
        coVerify(exactly = 0) { messageDao.upsertAllIndexed(any()) }
    }

    @Test
    fun `an out-of-range index writes nothing locally`() = runTest {
        coEvery { messageDao.getByIdForAccount("a1", account.value) } returns cached(textPart("only"))

        repository.updateMessageContentPart("c1", "a1", index = 7, text = "edited")

        // The server already answered 400 for this; the cache must not invent a part.
        coVerify(exactly = 0) { messageDao.upsertReindexed(any()) }
    }

    @Test
    fun `a failed request leaves the cache untouched`() = runTest {
        coEvery { messageDao.getByIdForAccount("a1", account.value) } returns cached(textPart("only"))
        coEvery { messagesApi.updateMessage(any(), any(), any()) } throws RuntimeException("500")

        val result = repository.updateMessageContentPart("c1", "a1", index = 0, text = "edited")

        assertThat(result).isInstanceOf(Result.Error::class.java)
        coVerify(exactly = 0) { messageDao.upsertReindexed(any()) }
    }
}
