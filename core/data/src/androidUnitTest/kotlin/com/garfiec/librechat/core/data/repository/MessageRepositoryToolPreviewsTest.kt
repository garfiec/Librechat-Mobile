package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.common.identity.AccountState
import com.garfiec.librechat.core.common.identity.InMemoryActiveAccountProvider
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.AccountRoster
import com.garfiec.librechat.core.data.db.dao.MessageDao
import com.garfiec.librechat.core.data.db.dao.ToolCallPartDao
import com.garfiec.librechat.core.data.db.entity.MessageEntity
import com.garfiec.librechat.core.data.db.entity.ToolCallPartEntity
import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.content.AgentToolCall
import com.garfiec.librechat.core.model.content.MessageContentPart
import com.garfiec.librechat.core.model.response.ToolCallPartResponse
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

/** Tool-call previews at the repository: who opts in, what reaches Room, and what never does. */
@OptIn(ExperimentalCoroutinesApi::class)
class MessageRepositoryToolPreviewsTest {

    private val messagesApi = mockk<MessagesApi>(relaxed = true)
    private val messageDao = mockk<MessageDao>(relaxed = true)
    private val roster = mockk<AccountRoster>(relaxed = true)
    private val toolCallPartDao = mockk<ToolCallPartDao>(relaxed = true)
    private val account = AccountId("srv:user-a")
    private lateinit var repository: MessageRepositoryImpl

    private val preview = AgentToolCall(id = "c1", output = "head…tail", outputTruncated = true, outputLength = 9000)
    private val previewed = Message(
        messageId = "m1",
        conversationId = "conv-1",
        content = listOf(MessageContentPart(type = ContentType.TOOL_CALL, toolCall = preview)),
    )

    @Before
    fun setup() {
        repository = MessageRepositoryImpl(
            messagesApi = messagesApi,
            messageDao = messageDao,
            toolCallPartDao = toolCallPartDao,
            activeAccountProvider = InMemoryActiveAccountProvider(AccountState.Resolved(account)),
            roster = roster,
            dispatcher = UnconfinedTestDispatcher(),
        )
        coEvery { roster.contains(any()) } returns true
        coEvery { messageDao.observeMessagesForAccount(any(), any()) } returns flowOf(emptyList())
    }

    @Test
    fun `a previewed load is cached with its markers`() = runTest {
        coEvery { messagesApi.getMessages("conv-1", true) } returns listOf(previewed)
        val written = slot<List<MessageEntity>>()
        coEvery { messageDao.upsertAllIndexed(capture(written)) } returns Unit

        repository.getMessages("conv-1", toolPreviews = true)

        coVerify(exactly = 1) { messagesApi.getMessages("conv-1", true) }
        assertThat(written.captured.single().content).contains("\"outputTruncated\":true")
        assertThat(written.captured.single().content).contains("\"outputLength\":9000")
    }

    @Test
    fun `refresh passes the opt-in through`() = runTest {
        repository.refreshMessages("conv-1", originAccount = null, toolPreviews = false)
        coVerify(exactly = 1) { messagesApi.getMessages("conv-1", false) }
    }

    @Test
    fun `a full fetch sends no opt-in and never writes the cache`() = runTest {
        coEvery { messagesApi.getMessagesOnce("conv-1") } returns listOf(previewed.copy(content = null))

        val result = repository.fetchMessagesFull("conv-1")

        assertThat(result).isInstanceOf(Result.Success::class.java)
        coVerify(exactly = 1) { messagesApi.getMessagesOnce("conv-1") }
        coVerify(exactly = 0) { messageDao.upsertAllIndexed(any()) }
        coVerify(exactly = 0) { messageDao.replaceAllForConversation(any(), any(), any()) }
    }

    @Test
    fun `a part is fetched network-direct and never cached`() = runTest {
        coEvery { messagesApi.getToolCallPart("conv-1", "m1", 2, "c1", "s1", null) } returns
            ToolCallPartResponse(toolCall = AgentToolCall(id = "c1", output = "full"))

        val result = repository.getToolCallPart("conv-1", "m1", 2, "c1", "s1", null)

        assertThat((result as Result.Success).data.output).isEqualTo("full")
        coVerify(exactly = 0) { messageDao.upsertAllIndexed(any()) }
    }

    @Test
    fun `only full calls are stored, under the account that fetched them, with blanks as absent`() = runTest {
        val rows = slot<List<ToolCallPartEntity>>()
        coEvery { toolCallPartDao.storeWithinBudget(capture(rows), any()) } returns 1
        val full = AgentToolCall(id = "c1", output = "x".repeat(9000))

        repository.storeToolCallParts(
            "conv-1",
            mapOf(
                ToolCallPartRef("m1", "c1", stepId = null, agentId = "") to full,
                ToolCallPartRef("m1", "c2", stepId = "s", agentId = null) to preview,
            ),
            originAccount = null,
        )

        val row = rows.captured.single()
        assertThat(row.toolCallId).isEqualTo("c1")
        assertThat(row.stepId).isEmpty()
        assertThat(row.agentId).isEmpty()
        assertThat(row.accountId).isEqualTo(account.value)
        coVerify(exactly = 0) { messageDao.upsertAllIndexed(any()) }
    }

    @Test
    fun `a stored row reads back by its ref, and a stored preview never does`() = runTest {
        fun row(id: String, json: String) = ToolCallPartEntity(
            messageId = "m1", toolCallId = id, stepId = "", agentId = "", accountId = account.value,
            conversationId = "conv-1", toolCall = json, chars = 1, storedAt = 1,
        )
        coEvery { toolCallPartDao.forMessages(account.value, "conv-1", listOf("m1")) } returns listOf(
            row("c1", """{"id":"c1","output":"full"}"""),
            row("c2", """{"id":"c2","output":"cut","outputTruncated":true}"""),
        )

        val stored = repository.storedToolCallParts(
            "conv-1",
            listOf(ToolCallPartRef("m1", "c1", null, null), ToolCallPartRef("m1", "c2", "", "")),
        )

        assertThat(stored.keys).containsExactly(ToolCallPartRef("m1", "c1", null, null))
        assertThat(stored.values.single().output).isEqualTo("full")
    }
}
