package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.MessageRepository
import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.content.AgentToolCall
import com.garfiec.librechat.core.model.content.MessageContentPart
import com.garfiec.librechat.core.model.content.SubagentAttachmentCall
import com.garfiec.librechat.core.model.response.SubagentAttachmentCallsEntry
import com.garfiec.librechat.core.model.response.SubagentAttachmentFile
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** The `/subagent-calls` lookup: asked once per file set, kept in memory, retried on reconnect, dropped on a switch. */
@OptIn(ExperimentalCoroutinesApi::class)
class SubagentHintsDelegateTest {

    private val repository = mockk<MessageRepository>()
    private val account = MutableStateFlow<String?>("acct-a")
    private val connected = MutableStateFlow(true)

    private val message = Message(
        messageId = "m1",
        conversationId = "c1",
        content = listOf(
            MessageContentPart(
                type = ContentType.TOOL_CALL,
                toolCall = AgentToolCall(id = "sub_a", name = "subagent", subagentContentOmitted = true, subagentContentParts = 2),
            ),
        ),
        attachments = listOf(Attachment(fileId = "f1", filename = "a.csv", toolCallId = "late_child")),
    )
    private val files = listOf(SubagentAttachmentFile("late_child"))
    private val answer =
        listOf(SubagentAttachmentCallsEntry("sub_a", calls = listOf(SubagentAttachmentCall("late_child"))))

    private fun TestScope.delegate() = SubagentHintsDelegate(
        scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job()),
        messageRepository = repository,
        currentAccountId = { account.value },
        defaultDispatcher = StandardTestDispatcher(testScheduler),
    )

    @Test
    fun `a previewed subagent message with an unplaced file is asked about once`() = runTest {
        coEvery { repository.getSubagentAttachmentCalls("c1", "m1", files) } returns Result.Success(answer)
        val source = MutableStateFlow<Pair<String?, List<Message>>>("c1" to listOf(message))
        val delegate = delegate()

        delegate.start(source, connected)
        advanceUntilIdle()
        source.value = "c1" to listOf(message.copy(text = "re-emitted"))
        advanceUntilIdle()

        assertThat(delegate.hints.value).containsExactly("m1", answer)
        coVerify(exactly = 1) { repository.getSubagentAttachmentCalls(any(), any(), any()) }
    }

    @Test
    fun `a failed lookup is asked again when connectivity returns, a bounded number of times`() = runTest {
        coEvery { repository.getSubagentAttachmentCalls(any(), any(), any()) } returns Result.Error(message = "offline")
        val delegate = delegate()
        delegate.start(MutableStateFlow("c1" to listOf(message)), connected)
        advanceUntilIdle()

        repeat(SubagentHintsDelegate.MAX_ATTEMPTS + 2) {
            connected.value = false
            advanceUntilIdle()
            connected.value = true
            advanceUntilIdle()
        }

        coVerify(exactly = SubagentHintsDelegate.MAX_ATTEMPTS) { repository.getSubagentAttachmentCalls(any(), any(), any()) }
        assertThat(delegate.hints.value).isEmpty()
    }

    @Test
    fun `a conversation switch drops the answers`() = runTest {
        coEvery { repository.getSubagentAttachmentCalls(any(), any(), any()) } returns Result.Success(answer)
        val source = MutableStateFlow<Pair<String?, List<Message>>>("c1" to listOf(message))
        val delegate = delegate()
        delegate.start(source, connected)
        advanceUntilIdle()
        assertThat(delegate.hints.value).isNotEmpty()

        source.value = "c2" to emptyList()
        advanceUntilIdle()

        assertThat(delegate.hints.value).isEmpty()
    }
}
