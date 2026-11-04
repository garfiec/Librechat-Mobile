package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.MessageRepository
import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.content.AgentToolCall
import com.garfiec.librechat.core.model.content.MessageContentPart
import com.garfiec.librechat.feature.chat.util.ToolPartKey
import com.garfiec.librechat.feature.chat.util.ToolPartLoad
import com.garfiec.librechat.feature.chat.util.ToolPartStatus
import com.garfiec.librechat.feature.chat.util.serializeMessageForClipboard
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Tool-call preview hydration: fetches land in memory only, a failure is retried by the user, a
 * switch drops everything, the budget never evicts an open card, and a copy never takes a preview.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ToolPartHydrationDelegateTest {

    private val repository = mockk<MessageRepository>(relaxed = true)
    private val conversationId = MutableStateFlow<String?>("c1")
    private val account = MutableStateFlow<String?>("acct-a")

    private fun preview(id: String, output: String = "head\n…\ntail", length: Int = 5000) = AgentToolCall(
        id = id,
        stepId = "step-$id",
        name = "read_file",
        output = output,
        outputTruncated = true,
        outputLength = length,
        previewRevision = "rev",
    )

    private fun part(call: AgentToolCall) = MessageContentPart(type = ContentType.TOOL_CALL, toolCall = call)

    private val messages = MutableStateFlow(
        listOf(
            Message(
                messageId = "m1",
                conversationId = "c1",
                content = listOf(MessageContentPart(type = ContentType.TEXT, text = "t"), part(preview("a")), part(preview("b"))),
            ),
        ),
    )

    private fun key(id: String) = requireNotNull(ToolPartKey.of(preview(id), null))

    private fun TestScope.delegate(budget: Long = ToolPartHydrationDelegate.DEFAULT_BUDGET_CHARS) =
        ToolPartHydrationDelegate(
            // Not backgroundScope: advanceUntilIdle never runs background work, so a fetch would never land.
            scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job()),
            messageRepository = repository,
            currentConversationId = { conversationId.value },
            currentMessages = { messages.value },
            currentAccountId = { account.value },
            defaultDispatcher = StandardTestDispatcher(testScheduler),
            budgetChars = budget,
        )

    private fun full(id: String, length: Int = 5000) = AgentToolCall(id = id, stepId = "step-$id", output = "f".repeat(length))

    @Test
    fun `a request fetches the part by its identity and raw index, once`() = runTest {
        coEvery { repository.getToolCallPart("c1", "m1", 2, "b", "step-b", null) } returns Result.Success(full("b"))
        val delegate = delegate()

        delegate.request(key("b"))
        delegate.request(key("b"))
        assertThat(delegate.loads.value[key("b")]).isEqualTo(ToolPartLoad.Loading)
        advanceUntilIdle()

        assertThat(delegate.loaded.value[key("b")]?.output).hasLength(5000)
        coVerify(exactly = 1) { repository.getToolCallPart(any(), any(), any(), any(), any(), any()) }
        // Loaded is a no-op too.
        delegate.request(key("b"))
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.getToolCallPart(any(), any(), any(), any(), any(), any()) }
        // Memory only: nothing reaches the cache.
        coVerify(exactly = 0) { repository.cacheMessages(any(), any()) }
    }

    @Test
    fun `a failure shows as failed and the next request retries`() = runTest {
        coEvery { repository.getToolCallPart(any(), any(), any(), any(), any(), any()) } returns
            Result.Error(message = "404") andThen Result.Success(full("a"))
        val delegate = delegate()

        delegate.request(key("a"))
        advanceUntilIdle()
        assertThat(delegate.statuses.value[key("a")]).isEqualTo(ToolPartStatus.FAILED)

        delegate.request(key("a"))
        advanceUntilIdle()
        assertThat(delegate.statuses.value).isEmpty()
        assertThat(delegate.loaded.value).containsKey(key("a"))
    }

    @Test
    fun `a call no longer in the messages fails rather than fetching a stale key`() = runTest {
        val delegate = delegate()
        val stale = requireNotNull(ToolPartKey.of(preview("a").copy(previewRevision = "older"), null))

        delegate.request(stale)
        advanceUntilIdle()

        assertThat(delegate.statuses.value[stale]).isEqualTo(ToolPartStatus.FAILED)
        coVerify(exactly = 0) { repository.getToolCallPart(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a conversation switch drops everything, including an answer still out`() = runTest {
        val gate = CompletableDeferred<Result<AgentToolCall>>()
        coEvery { repository.getToolCallPart(any(), any(), any(), "a", any(), any()) } returns Result.Success(full("a"))
        coEvery { repository.getToolCallPart(any(), any(), any(), "b", any(), any()) } coAnswers { gate.await() }
        val delegate = delegate()
        val source = MutableStateFlow(conversationId.value to messages.value)
        delegate.start(source)
        advanceUntilIdle()
        delegate.request(key("a"))
        advanceUntilIdle()
        delegate.request(key("b"))
        advanceUntilIdle()
        assertThat(delegate.loaded.value.keys).containsExactly(key("a"))

        conversationId.value = "c2"
        source.value = "c2" to emptyList()
        advanceUntilIdle()
        gate.complete(Result.Success(full("b")))
        advanceUntilIdle()

        assertThat(delegate.loads.value).isEmpty()
    }

    @Test
    fun `an account switch drops everything`() = runTest {
        coEvery { repository.getToolCallPart(any(), any(), any(), any(), any(), any()) } returns Result.Success(full("a"))
        val delegate = delegate()
        delegate.request(key("a"))
        advanceUntilIdle()
        assertThat(delegate.loaded.value).isNotEmpty()

        account.value = "acct-b"
        delegate.request(key("b"))

        assertThat(delegate.loads.value.keys).containsExactly(key("b"))
    }

    @Test
    fun `past the budget the least recently opened part goes, never one an open card holds`() = runTest {
        coEvery { repository.getToolCallPart(any(), any(), any(), "a", any(), any()) } returns Result.Success(full("a", 600))
        coEvery { repository.getToolCallPart(any(), any(), any(), "b", any(), any()) } returns Result.Success(full("b", 600))
        val delegate = delegate(budget = 1000)

        delegate.retain(key("a").identity)
        delegate.request(key("a"))
        advanceUntilIdle()
        delegate.request(key("b"))
        advanceUntilIdle()

        // "a" is older but held open, so "b" is the one past the budget that goes.
        assertThat(delegate.loaded.value.keys).containsExactly(key("a"))

        delegate.release(key("a").identity)
        delegate.request(key("b"))
        advanceUntilIdle()
        assertThat(delegate.loaded.value.keys).containsExactly(key("b"))
    }

    @Test
    fun `a whole-message copy fetches every preview and serialises the full calls`() = runTest {
        coEvery { repository.getToolCallPart(any(), any(), any(), "a", any(), any()) } returns Result.Success(full("a"))
        coEvery { repository.getToolCallPart(any(), any(), any(), "b", any(), any()) } returns Result.Success(full("b"))
        val delegate = delegate()

        val message = requireNotNull(delegate.fullMessage("m1"))

        val calls = message.content!!.mapNotNull { it.toolCall }
        assertThat(calls.none { it.isPreview }).isTrue()
        assertThat(serializeMessageForClipboard(message)).doesNotContain("outputTruncated")
        assertThat(delegate.loaded.value.keys).containsExactly(key("a"), key("b"))
    }

    @Test
    fun `a copy with any failed fetch copies nothing`() = runTest {
        coEvery { repository.getToolCallPart(any(), any(), any(), "a", any(), any()) } returns Result.Success(full("a"))
        coEvery { repository.getToolCallPart(any(), any(), any(), "b", any(), any()) } returns Result.Error(message = "offline")
        val delegate = delegate()

        assertThat(delegate.fullMessage("m1")).isNull()
    }

    @Test
    fun `a copy reuses parts already fetched`() = runTest {
        coEvery { repository.getToolCallPart(any(), any(), any(), "a", any(), any()) } returns Result.Success(full("a"))
        coEvery { repository.getToolCallPart(any(), any(), any(), "b", any(), any()) } returns Result.Success(full("b"))
        val delegate = delegate()
        delegate.request(key("a"))
        advanceUntilIdle()

        delegate.fullMessage("m1")

        coVerify(exactly = 1) { repository.getToolCallPart(any(), any(), any(), "a", any(), any()) }
    }

    @Test
    fun `a reload that previews a call held in full seeds it instead of refetching`() = runTest {
        val source = MutableStateFlow<Pair<String?, List<Message>>>(
            "c1" to listOf(Message(messageId = "m1", conversationId = "c1", content = listOf(part(full("a"))))),
        )
        val delegate = delegate()
        delegate.start(source)
        advanceUntilIdle()

        source.value = "c1" to listOf(Message(messageId = "m1", conversationId = "c1", content = listOf(part(preview("a")))))
        advanceUntilIdle()

        assertThat(delegate.loaded.value[key("a")]?.output).hasLength(5000)
        coVerify(exactly = 0) { repository.getToolCallPart(any(), any(), any(), any(), any(), any()) }
    }
}
