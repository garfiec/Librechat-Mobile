package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.repository.FileRepository
import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.response.FilePreviewResponse
import com.garfiec.librechat.feature.chat.util.MessageNode
import com.garfiec.librechat.feature.chat.util.withPreviews
import com.garfiec.librechat.feature.chat.viewmodel.AttachmentPreviewHandle
import com.garfiec.librechat.feature.chat.viewmodel.ChatStateHandle
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The deferred-preview lifecycle for generated files: the stream's upsert-by-file_id with its
 * no-regress guard, and the results learned afterwards, which live in `previewOverrides` rather
 * than in any message. The wiring across finalize and the next turn is covered end to end by
 * `ChatViewModelAttachmentPreviewTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AttachmentPreviewDelegateTest {

    private val fileRepository = mockk<FileRepository>()
    private val state = MutableStateFlow(ChatUiState())

    private fun TestScope.delegate() =
        AttachmentPreviewDelegate(AttachmentPreviewHandle(ChatStateHandle(state, this)), fileRepository)

    private val docMime = "application/vnd.librechat.docx-preview"

    private fun att(status: String, text: String? = null, fileId: String = "f1") =
        Attachment(fileId = fileId, filename = "r.docx", type = docMime, status = status, text = text)

    private fun preview(status: String, text: String? = null) =
        FilePreviewResponse(fileId = "f1", status = status, text = text)

    // ─── upsert ────────────────────────────────────────────────────

    @Test
    fun `appends a new record then merges over by file_id`() = runTest {
        val d = delegate()
        val out1 = d.upsertByFileId(emptyList(), att("pending"))
        assertThat(out1).hasSize(1)
        val out2 = d.upsertByFileId(out1, att("ready", text = "<html/>"))
        assertThat(out2).hasSize(1)
        assertThat(out2[0].status).isEqualTo("ready")
        assertThat(out2[0].text).isEqualTo("<html/>")
    }

    @Test
    fun `ready is NOT regressed by a late pending for the same file_id`() = runTest {
        val d = delegate()
        d.onAttachment(att("pending"))
        d.onAttachment(att("ready", text = "<html/>"))
        d.onAttachment(att("pending"))

        val streamed = state.value.streamingAttachments.single()
        assertThat(streamed.status).isEqualTo("ready")
        assertThat(streamed.text).isEqualTo("<html/>")
        assertThat(state.value.previewOverrides["f1"]?.status).isEqualTo("ready")
    }

    @Test
    fun `failed is NOT regressed by a late pending`() = runTest {
        val d = delegate()
        val out = d.upsertByFileId(listOf(att("failed")), att("pending"))
        assertThat(out[0].status).isEqualTo("failed")
    }

    @Test
    fun `distinct file_ids each get their own record`() = runTest {
        val d = delegate()
        d.onAttachment(att("pending", fileId = "f1"))
        d.onAttachment(att("pending", fileId = "f2"))
        assertThat(state.value.streamingAttachments.map { it.fileId }).containsExactly("f1", "f2").inOrder()
    }

    // ─── polling ───────────────────────────────────────────────────

    @Test
    fun `pending then ready by poll`() = runTest {
        coEvery { fileRepository.pollFilePreview("f1") } returns Result.Success(preview("ready", text = "doc"))
        val d = delegate()
        d.onAttachment(att("pending"))

        d.onStreamEnded()
        advanceUntilIdle()

        val override = state.value.previewOverrides.getValue("f1")
        assertThat(override.status).isEqualTo("ready")
        assertThat(override.text).isEqualTo("doc")
    }

    @Test
    fun `pending then failed by poll`() = runTest {
        coEvery { fileRepository.pollFilePreview("f1") } returns
            Result.Success(FilePreviewResponse(fileId = "f1", status = "failed", previewError = "timeout"))
        val d = delegate()
        d.onAttachment(att("pending"))

        d.onStreamEnded()
        advanceUntilIdle()

        assertThat(state.value.previewOverrides.getValue("f1").previewError).isEqualTo("timeout")
    }

    @Test
    fun `a poll that gave up on errors shows the file as failed`() = runTest {
        // FileRepositoryPreviewPollTest covers the five-errors stop itself; this is what it leaves.
        coEvery { fileRepository.pollFilePreview("f1") } returns Result.Error(message = "offline")
        val d = delegate()
        d.onAttachment(att("pending"))

        d.onStreamEnded()
        advanceUntilIdle()

        assertThat(state.value.previewOverrides.getValue("f1").status).isEqualTo("failed")
    }

    @Test
    fun `a budget spent while pending shows the file as failed`() = runTest {
        coEvery { fileRepository.pollFilePreview("f1") } returns Result.Success(preview("pending"))
        val d = delegate()
        d.onAttachment(att("pending"))

        d.onStreamEnded()
        advanceUntilIdle()

        assertThat(state.value.previewOverrides.getValue("f1").status).isEqualTo("failed")
    }

    @Test
    fun `a poll never replaces a ready result that arrived while it ran`() = runTest {
        val answer = CompletableDeferred<Result<FilePreviewResponse>>()
        coEvery { fileRepository.pollFilePreview("f1") } coAnswers { answer.await() }
        val d = delegate()
        d.onAttachment(att("pending"))
        d.onStreamEnded()
        advanceUntilIdle()

        // A second SSE update from a stream that is somehow still talking, then the poll's answer.
        d.onAttachment(att("ready", text = "from sse"))
        answer.complete(Result.Success(preview("failed")))
        advanceUntilIdle()

        assertThat(state.value.previewOverrides.getValue("f1").text).isEqualTo("from sse")
    }

    @Test
    fun `a result survives the streamed list being emptied`() = runTest {
        val answer = CompletableDeferred<Result<FilePreviewResponse>>()
        coEvery { fileRepository.pollFilePreview("f1") } coAnswers { answer.await() }
        val d = delegate()
        d.onAttachment(att("pending"))
        d.onStreamEnded()
        advanceUntilIdle()

        // The finalize, as finalizeChatDisplay writes it.
        state.value = state.value.copy(content = state.value.content.copy(streamingAttachments = emptyList()))
        answer.complete(Result.Success(preview("ready", text = "doc")))
        advanceUntilIdle()

        val persisted = listOf(att("pending")).withPreviews(state.value.previewOverrides).single()
        assertThat(persisted.status).isEqualTo("ready")
        assertThat(persisted.text).isEqualTo("doc")
    }

    @Test
    fun `a persisted pending attachment is picked up once the load settles`() = runTest {
        coEvery { fileRepository.pollFilePreview("f1") } returns Result.Success(preview("ready", text = "doc"))
        val d = delegate()

        d.onMessagesSettled(listOf(node(att("pending")), node(att("ready", fileId = "f2"))))
        advanceUntilIdle()

        assertThat(state.value.previewOverrides.keys).containsExactly("f1")
        coVerify(exactly = 0) { fileRepository.pollFilePreview("f2") }
    }

    @Test
    fun `a resolved file is not polled again by the next settled emission`() = runTest {
        coEvery { fileRepository.pollFilePreview("f1") } returns Result.Success(preview("ready", text = "doc"))
        val d = delegate()
        val messages = listOf(node(att("pending")))

        d.onMessagesSettled(messages)
        advanceUntilIdle()
        // Room still says pending: nothing is ever written back to the message.
        d.onMessagesSettled(messages)
        advanceUntilIdle()

        coVerify(exactly = 1) { fileRepository.pollFilePreview("f1") }
    }

    @Test
    fun `a fresh pending for a file this reply resolved drops the stale result`() = runTest {
        val d = delegate()
        d.onAttachment(att("pending").copy(messageId = "m1"))
        d.onAttachment(att("ready", text = "v1").copy(messageId = "m1"))
        assertThat(state.value.previewOverrides).containsKey("m1:f1")
        // The stream's copy is the terminal one, so retire it as the finalize would.
        state.value = state.value.copy(content = state.value.content.copy(streamingAttachments = emptyList()))

        // The session rewrites the same file within the same reply.
        d.onAttachment(att("pending").copy(messageId = "m1"))

        assertThat(state.value.previewOverrides).doesNotContainKey("m1:f1")
    }

    @Test
    fun `a later turn rewriting a file leaves the earlier turn's result on the earlier turn`() = runTest {
        coEvery { fileRepository.pollFilePreview("f1") } returns Result.Success(preview("ready", text = "v1"))
        val d = delegate()
        val turn1 = att("pending").copy(messageId = "m1")
        d.onMessagesSettled(listOf(node(turn1)))
        advanceUntilIdle()

        // Turn 2 rewrites the same file_id in a stateful session, then resolves it.
        d.onAttachment(att("pending").copy(messageId = "m2"))
        assertThat(listOf(turn1).withPreviews(state.value.previewOverrides).single().text).isEqualTo("v1")
        d.onAttachment(att("ready", text = "v2").copy(messageId = "m2"))

        assertThat(listOf(turn1).withPreviews(state.value.previewOverrides).single().text).isEqualTo("v1")
        val turn2 = att("pending").copy(messageId = "m2")
        assertThat(listOf(turn2).withPreviews(state.value.previewOverrides).single().text).isEqualTo("v2")
    }

    @Test
    fun `reset cancels polls and forgets every result`() = runTest {
        val answer = CompletableDeferred<Result<FilePreviewResponse>>()
        coEvery { fileRepository.pollFilePreview("f1") } coAnswers { answer.await() }
        coEvery { fileRepository.pollFilePreview("f2") } returns Result.Success(preview("ready"))
        val d = delegate()
        d.onMessagesSettled(listOf(node(att("pending")), node(att("pending", fileId = "f2"))))
        advanceUntilIdle()

        d.reset()
        answer.complete(Result.Success(preview("ready")))
        advanceUntilIdle()

        assertThat(state.value.previewOverrides).isEmpty()
    }

    private fun node(attachment: Attachment) = MessageNode(
        message = Message(messageId = "m-${attachment.fileId}", conversationId = "c", text = "", attachments = listOf(attachment)),
        children = emptyList(),
        siblingIndex = 0,
        siblingCount = 1,
    )
}
