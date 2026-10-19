package com.garfiec.librechat.feature.chat.viewmodel

import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.ChatFontSize
import com.garfiec.librechat.core.data.datastore.ChatHeaderAlignment
import com.garfiec.librechat.core.data.datastore.ChatHeaderContent
import com.garfiec.librechat.core.data.datastore.ContextBarPlacement
import com.garfiec.librechat.core.data.datastore.DuringRunAction
import com.garfiec.librechat.core.data.datastore.StarredModelsDisplay
import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.StreamEvent
import com.garfiec.librechat.core.model.response.ChatResumeResponse
import com.garfiec.librechat.core.model.response.ChatStatusResponse
import com.garfiec.librechat.core.model.response.FilePreviewResponse
import com.garfiec.librechat.feature.chat.util.withPreviews
import com.garfiec.librechat.feature.chat.viewmodel.delegate.PlatformFileHandler
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * A deferred preview resolved by polling must reach the reply it belongs to, however long the
 * poll takes. Driven through the whole ViewModel because both ways to lose it are wiring: a poll
 * writing its result into the streamed attachment list the finalize has already emptied, and the
 * next turn's start cancelling it. Neither is visible from the delegate.
 *
 * The run is opened by resume (the harness of [ChatViewModelDuringRunSendTest], whose KDoc explains
 * why every case cancels the ViewModel scope and drives with [runCurrent]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelAttachmentPreviewTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val fixture = ChatViewModelTestFixture()
    private val fileHandler = mockk<PlatformFileHandler>(relaxed = true)
    private val resumedStream = MutableSharedFlow<StreamEvent>(extraBufferCapacity = 8)

    /** Held open until a test lets the poll answer. */
    private val pollAnswer = CompletableDeferred<Result<FilePreviewResponse>>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fixture.stubDefaults()
        with(fixture) {
            every { configRepository.endpointConfigs } returns MutableStateFlow(mapOf(ENDPOINT to EndpointConfig()))
            every { configRepository.availableModels } returns MutableStateFlow(mapOf(ENDPOINT to listOf(MODEL)))
            every { settingsDataStore.selectedMcpServers } returns flowOf(emptySet())
            every { settingsDataStore.enabledTools } returns flowOf(emptySet())
            every { serverDataStore.currentUrlFlow } returns flowOf("https://example.test")
            every { settingsDataStore.chatFontSize } returns flowOf(ChatFontSize.MEDIUM)
            every { settingsDataStore.starredModelsDisplay } returns flowOf(StarredModelsDisplay.OFF)
            every { settingsDataStore.chatHeaderContent } returns flowOf(ChatHeaderContent.TITLE)
            every { settingsDataStore.chatHeaderAlignment } returns flowOf(ChatHeaderAlignment.LEFT)
            every { settingsDataStore.contextBarPlacement } returns flowOf(ContextBarPlacement.OPTIONS_SHEET)
            every { settingsDataStore.contextGaugeExpanded } returns flowOf(false)
            every { settingsDataStore.duringRunAction } returns flowOf(DuringRunAction.QUEUE)
            every { platformDelegateFactory.createFileHandler(any()) } returns fileHandler
            every { fileHandler.attachedFiles } returns MutableStateFlow(emptyList())
            coEvery { conversationRepository.getConversation(any(), any()) } returns Result.Error(message = "test")
            coEvery { chatRepository.resumeChat(any()) } returns Result.Success(ChatResumeResponse(status = "resuming"))
            coEvery { chatRepository.checkStreamStatus(eq(CONVERSATION_ID), any()) } returns
                ChatStatusResponse(active = true)
            every { chatRepository.resumeStream(any()) } returns resumedStream
            // The next turn's stream, from the queue drain. Never emits, so that run stays open.
            every {
                chatRepository.startChat(
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                    any(),
                )
            } returns MutableSharedFlow()
            coEvery { fileRepository.pollFilePreview(FILE_ID) } coAnswers { pollAnswer.await() }
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a poll that resolves after the finalize still reaches the reply`() = previewTest { vm ->
        resumedStream.emit(attachmentEvent(status = FilePreviewResponse.STATUS_PENDING))
        runCurrent()
        resumedStream.emit(finalWith(pendingAttachment()))
        runCurrent()
        assertThat(vm.uiState.value.isStreaming).isFalse()
        assertThat(vm.uiState.value.streamingAttachments).isEmpty()

        pollAnswer.complete(Result.Success(readyPreview()))
        runCurrent()

        assertThat(renderedReplyAttachment(vm).status).isEqualTo(FilePreviewResponse.STATUS_READY)
        assertThat(renderedReplyAttachment(vm).text).isEqualTo("line 1\nline 2")
    }

    @Test
    fun `a poll survives the next turn starting`() = previewTest { vm ->
        // Queued first, so the clean Final drains it and a second turn starts in the same dispatch.
        vm.onInputChanged("the next turn")
        vm.queueMessage()
        runCurrent()

        resumedStream.emit(attachmentEvent(status = FilePreviewResponse.STATUS_PENDING))
        runCurrent()
        resumedStream.emit(finalWith(pendingAttachment()))
        runCurrent()
        assertThat(vm.uiState.value.isStreaming).isTrue()

        pollAnswer.complete(Result.Success(readyPreview()))
        runCurrent()

        assertThat(renderedReplyAttachment(vm).status).isEqualTo(FilePreviewResponse.STATUS_READY)
    }

    @Test
    fun `a ready event mid-stream outlives the Final's pending snapshot without a poll`() = previewTest { vm ->
        resumedStream.emit(attachmentEvent(status = FilePreviewResponse.STATUS_PENDING))
        resumedStream.emit(attachmentEvent(status = FilePreviewResponse.STATUS_READY, text = "line 1\nline 2"))
        runCurrent()
        // The response's own attachment is the immediate-persist record, still pending.
        resumedStream.emit(finalWith(pendingAttachment()))
        runCurrent()

        assertThat(renderedReplyAttachment(vm).status).isEqualTo(FilePreviewResponse.STATUS_READY)
        coVerify(exactly = 0) { fixture.fileRepository.pollFilePreview(any()) }
    }

    private fun renderedReplyAttachment(vm: ChatViewModel): Attachment {
        val state = vm.uiState.value
        val reply = state.messages.single { it.messageId == REPLY_ID }
        return reply.attachments.orEmpty().withPreviews(state.previewOverrides).single()
    }

    private fun previewTest(body: suspend TestScope.(ChatViewModel) -> Unit) = runTest(testDispatcher) {
        fixture.selectionHandoff.put(conversationId = CONVERSATION_ID, endpoint = ENDPOINT, model = MODEL)
        val vm = fixture.build(defaultDispatcher = testDispatcher, initialConversationId = CONVERSATION_ID)
        runCurrent()
        try {
            assertThat(vm.uiState.value.isStreaming).isTrue()
            body(vm)
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    private fun attachmentEvent(status: String, text: String? = null) = StreamEvent.AttachmentCreated(
        fileId = FILE_ID,
        filename = "run.log",
        type = "text/plain",
        toolCallId = "call-1",
        messageId = REPLY_ID,
        conversationId = CONVERSATION_ID,
        status = status,
        text = text,
    )

    private fun pendingAttachment() = Attachment(
        fileId = FILE_ID,
        filename = "run.log",
        type = "text/plain",
        toolCallId = "call-1",
        messageId = REPLY_ID,
        status = FilePreviewResponse.STATUS_PENDING,
    )

    private fun readyPreview() =
        FilePreviewResponse(fileId = FILE_ID, status = FilePreviewResponse.STATUS_READY, text = "line 1\nline 2")

    private fun finalWith(attachment: Attachment) = StreamEvent.Final(
        requestMessage = null,
        responseMessage = Message(
            messageId = REPLY_ID,
            conversationId = CONVERSATION_ID,
            text = "done",
            isCreatedByUser = false,
            attachments = listOf(attachment),
        ),
        conversation = null,
    )

    private companion object {
        const val CONVERSATION_ID = "conv-1"
        const val ENDPOINT = "anthropic"
        const val MODEL = "claude-haiku-4-5"
        const val FILE_ID = "file-1"
        const val REPLY_ID = "assistant-1"
    }
}
