package com.garfiec.librechat.feature.conversations.export

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.ConversationRepository
import com.garfiec.librechat.core.data.repository.MessageRepository
import com.garfiec.librechat.core.data.repository.ToolCallPreviewImportException
import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.content.AgentToolCall
import com.garfiec.librechat.core.model.content.MessageContentPart
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** An export is full by construction, and shortened tool output never makes it into or out of a file. */
class ConversationExportPreviewTest {

    private val conversationRepository = mockk<ConversationRepository>()
    private val messageRepository = mockk<MessageRepository>()

    private fun message(call: AgentToolCall) = Message(
        messageId = "m1",
        conversationId = "c1",
        content = listOf(MessageContentPart(type = ContentType.TOOL_CALL, toolCall = call)),
    )

    @Test
    fun exportReadsTheFullConversationWithoutTouchingTheCache() = runTest {
        coEvery { conversationRepository.getConversation("c1", null) } returns Result.Success(Conversation(conversationId = "c1"))
        coEvery { messageRepository.fetchMessagesFull("c1") } returns
            Result.Success(listOf(message(AgentToolCall(id = "t", output = "whole output"))))

        val result = ConversationExporter(conversationRepository, messageRepository, UnconfinedTestDispatcher(testScheduler))
            .exportAsJson("c1")

        assertThat((result as Result.Success).data).contains("whole output")
        // A full call's preview fields stay out of the file, as in web's export.
        listOf("outputTruncated", "outputLength", "argsTruncated", "previewRevision").forEach {
            assertThat(result.data).doesNotContain(it)
        }
        // And the importer still reads it back.
        assertThat(ConversationImporter(UnconfinedTestDispatcher(testScheduler)).parseJson(result.data))
            .isInstanceOf(Result.Success::class.java)
        coVerify(exactly = 0) { messageRepository.getMessages(any(), any()) }
    }

    @Test
    fun anExportThatWouldHoldPreviewsFails() = runTest {
        coEvery { conversationRepository.getConversation("c1", null) } returns Result.Success(Conversation(conversationId = "c1"))
        // The full fetch fell back to cached previews (offline).
        coEvery { messageRepository.fetchMessagesFull("c1") } returns
            Result.Success(listOf(message(AgentToolCall(id = "t", output = "head", outputTruncated = true))))

        val result = ConversationExporter(conversationRepository, messageRepository, UnconfinedTestDispatcher(testScheduler))
            .exportAsJson("c1")

        assertThat(result).isInstanceOf(Result.Error::class.java)
        // No server text: the screen shows its generic export failure.
        assertThat((result as Result.Error).message).isNull()
    }

    @Test
    fun theImporterRefusesAFileHoldingPreviews() = runTest {
        val file = """{"exportedAt":1,"version":1,"conversation":{"conversationId":"c1"},"messages":[{"messageId":"m1","conversationId":"c1",
            "content":[{"type":"tool_call","tool_call":{"id":"t","output":"head","argsTruncated":true}}]}]}"""

        val result = ConversationImporter(UnconfinedTestDispatcher(testScheduler)).parseJson(file)

        assertThat((result as Result.Error).exception).isInstanceOf(ToolCallPreviewImportException::class.java)
    }
}
