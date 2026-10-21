package com.garfiec.librechat.feature.chat.viewmodel

import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.StreamEvent
import com.garfiec.librechat.core.model.error.StreamErrorType
import com.garfiec.librechat.feature.chat.util.AbortFrameFixtures
import com.garfiec.librechat.feature.chat.viewmodel.delegate.PlatformFileHandler
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * A send that fails after the server's `created` milestone (#405). The server emits `created`
 * before it builds the prompt and saves the user message only afterwards, so a failure in between
 * ends a created turn the server never recorded. The post-error reload drops the optimistic
 * bubble and the composer is already cleared, so only the restore keeps the user's text.
 *
 * Also the un-send for a turn that ends before `created` (a failure or an early abort), which
 * `StreamingManagerDelegate` decides on and the ViewModel performs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelFailedTurnRestoreTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fixture = ChatViewModelTestFixture()
    private val fileHandler = mockk<PlatformFileHandler>(relaxed = true)
    private val stored = MutableStateFlow<List<Message>>(emptyList())

    private val user = Message(
        messageId = "user-1",
        conversationId = CONVERSATION_ID,
        parentMessageId = NO_PARENT,
        text = "Say one word",
        isCreatedByUser = true,
    )
    private val reply = Message(
        messageId = "assistant-1",
        conversationId = CONVERSATION_ID,
        parentMessageId = "user-1",
        text = "Kappa",
        isCreatedByUser = false,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fixture.stubDefaults()
        with(fixture) {
            every { configRepository.endpointConfigs } returns MutableStateFlow(mapOf(ENDPOINT to EndpointConfig()))
            every { configRepository.availableModels } returns MutableStateFlow(mapOf(ENDPOINT to listOf(MODEL)))
            every { settingsDataStore.selectedMcpServers } returns flowOf(emptySet())
            every { settingsDataStore.enabledTools } returns flowOf(emptySet())
            coEvery { conversationRepository.getConversation(any(), any()) } returns Result.Error(message = "test")
            // buildSendSpec reads the attachment list; the relaxed factory's stub fails the List cast.
            every { platformDelegateFactory.createFileHandler(any()) } returns fileHandler
            every { fileHandler.attachedFiles } returns MutableStateFlow(emptyList())
            every { messageRepository.observeMessages(CONVERSATION_ID) } returns stored
            every { connectivityObserver.isConnected } returns MutableStateFlow(false)
        }
        stored.value = listOf(user, reply)
        coEvery { fixture.messageRepository.getMessages(CONVERSATION_ID) } returns
            Result.Success(listOf(user, reply))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun kotlinx.coroutines.test.TestScope.sendFailingAfterCreated(
        error: StreamEvent.Error,
        serverAfterFailure: List<Message>,
    ): ChatViewModel = sendStreaming(
        StreamEvent.Created(
            conversationId = CONVERSATION_ID,
            messageId = "server-user-2",
            parentMessageId = "assistant-1",
        ),
        error,
        serverAfterFailure = serverAfterFailure,
    )

    private fun kotlinx.coroutines.test.TestScope.sendStreaming(
        vararg events: StreamEvent,
        serverAfterFailure: List<Message> = listOf(user, reply),
    ): ChatViewModel = send(flowOf(*events), serverAfterFailure)

    /**
     * A send the server's middleware refused: the start POST itself throws, the way `ChatApi`
     * raises a `denyRequest` body. The stream never emits, so this reaches the delegate's
     * flow-exception path rather than the in-band error the other cases use.
     */
    private fun kotlinx.coroutines.test.TestScope.sendRefused(
        refusalText: String,
        serverAfterFailure: List<Message>,
        conversationId: String? = CONVERSATION_ID,
        temporary: Boolean = false,
    ): ChatViewModel = send(
        flow { throw ApiException(statusCode = 200, message = "refused", body = refusalText) },
        serverAfterFailure,
        conversationId,
        temporary,
    )

    private fun kotlinx.coroutines.test.TestScope.send(
        stream: Flow<StreamEvent>,
        serverAfterFailure: List<Message>,
        conversationId: String? = CONVERSATION_ID,
        temporary: Boolean = false,
    ): ChatViewModel {
        every {
            fixture.chatRepository.startChat(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(),
            )
        } returns stream
        conversationId?.let { fixture.selectionHandoff.put(conversationId = it, choice = ModelChoice.Raw(ENDPOINT, MODEL)) }
        val vm = fixture.build(
            defaultDispatcher = testDispatcher,
            initialConversationId = conversationId,
            initialIsTemporary = temporary,
        )
        advanceUntilIdle()
        // The post-error reload: Room then holds whatever the server kept.
        coEvery { fixture.messageRepository.getMessages(CONVERSATION_ID) } coAnswers {
            stored.value = serverAfterFailure
            Result.Success(serverAfterFailure)
        }
        // A new chat has no handoff to take its selection from.
        if (conversationId == null) vm.onModelSelected(ENDPOINT, MODEL)
        vm.onInputChanged(TYPED)
        vm.sendMessage()
        advanceUntilIdle()
        return vm
    }

    @Test
    fun `a created turn the server never saved hands its text back to the composer`() = runTest(testDispatcher) {
        val vm = sendFailingAfterCreated(
            StreamEvent.Error("An error occurred while processing the request: 401"),
            serverAfterFailure = listOf(user, reply),
        )
        try {
            val state = vm.uiState.value
            assertThat(state.isStreaming).isFalse()
            assertThat(state.error).isNotNull()
            assertThat(state.inputText).isEqualTo(TYPED)
            assertThat(state.displayMessages.map { it.message.text }).doesNotContain(TYPED)
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    @Test
    fun `a created turn the server did save stays in the thread and out of the composer`() =
        runTest(testDispatcher) {
            // Saved under the server's own id (rc3+ does not adopt the client's), matched on content.
            val saved = Message(
                messageId = "server-user-2",
                conversationId = CONVERSATION_ID,
                parentMessageId = "assistant-1",
                text = TYPED,
                isCreatedByUser = true,
            )
            val vm = sendFailingAfterCreated(
                StreamEvent.Error("An error occurred while processing the request: 401"),
                serverAfterFailure = listOf(user, reply, saved),
            )
            try {
                val state = vm.uiState.value
                assertThat(state.inputText).isEmpty()
                assertThat(state.displayMessages.map { it.message.text }).contains(TYPED)
            } finally {
                vm.viewModelScope.cancel()
            }
        }

    @Test
    fun `a network error leaves the composer alone - the run may still be live`() = runTest(testDispatcher) {
        val vm = sendFailingAfterCreated(
            StreamEvent.Error("Connection lost", isNetworkError = true),
            serverAfterFailure = listOf(user, reply),
        )
        try {
            assertThat(vm.uiState.value.inputText).isEmpty()
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    @Test
    fun `a turn that fails before created is un-sent and its text handed back`() = runTest(testDispatcher) {
        val vm = sendStreaming(StreamEvent.Error("Request failed (HTTP 302)"))
        try {
            val state = vm.uiState.value
            assertThat(state.isStreaming).isFalse()
            assertThat(state.error).isNotNull()
            assertThat(state.inputText).isEqualTo(TYPED)
            assertThat(state.displayMessages.map { it.message.text }).doesNotContain(TYPED)
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    @Test
    fun `an early abort restores the optimistic message's own text, not the frame's`() = runTest(testDispatcher) {
        val vm = sendStreaming(AbortFrameFixtures.earlyAbortFrame(userText = "frame text"))
        try {
            val state = vm.uiState.value
            assertThat(state.isStreaming).isFalse()
            assertThat(state.inputText).isEqualTo(TYPED)
            assertThat(state.displayMessages.map { it.message.text }).doesNotContain(TYPED)
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    @Test
    fun `a refusal the server saved stays in the thread under its error, composer empty`() =
        runTest(testDispatcher) {
            // denyRequest persists the refused message, then the error turn carrying the payload.
            val saved = Message(
                messageId = "refused-user",
                conversationId = CONVERSATION_ID,
                parentMessageId = "assistant-1",
                text = TYPED,
                isCreatedByUser = true,
            )
            val errorTurn = Message(
                messageId = "refused-error",
                conversationId = CONVERSATION_ID,
                parentMessageId = "refused-user",
                text = MESSAGE_LIMIT,
                isCreatedByUser = false,
                error = true,
            )
            val vm = sendRefused(MESSAGE_LIMIT, serverAfterFailure = listOf(user, reply, saved, errorTurn))
            try {
                val state = vm.uiState.value
                assertThat(state.isStreaming).isFalse()
                assertThat(state.error).isEqualTo(StreamErrorType.MESSAGE_LIMIT.marker)
                assertThat(state.inputText).isEmpty()
                assertThat(state.displayMessages.map { it.message.text }).contains(TYPED)
            } finally {
                vm.viewModelScope.cancel()
            }
        }

    @Test
    fun `a refusal the server did not save hands the text back`() = runTest(testDispatcher) {
        // A conversation id with no real parent: denyRequest's save gate is closed.
        val vm = sendRefused(MESSAGE_LIMIT, serverAfterFailure = listOf(user, reply))
        try {
            val state = vm.uiState.value
            assertThat(state.error).isEqualTo(StreamErrorType.MESSAGE_LIMIT.marker)
            assertThat(state.inputText).isEqualTo(TYPED)
            assertThat(state.displayMessages.map { it.message.text }).doesNotContain(TYPED)
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    @Test
    fun `a message limit that says how long to wait is told to the user`() = runTest(testDispatcher) {
        // What a v0.8.8 limiter actually sends: getRateLimitReset's fields spread into the payload.
        val vm = sendRefused(MESSAGE_LIMIT_WITH_RETRY, serverAfterFailure = emptyList(), conversationId = null)
        try {
            assertThat(vm.uiState.value.error).isEqualTo(ChatNotice.MESSAGE_LIMIT_RETRY.marker(45L))
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    @Test
    fun `a refused new chat is un-sent and its text handed back`() = runTest(testDispatcher) {
        val vm = sendRefused(BAN, serverAfterFailure = emptyList(), conversationId = null)
        try {
            val state = vm.uiState.value
            assertThat(state.isStreaming).isFalse()
            assertThat(state.error).isEqualTo(StreamErrorType.BAN.marker)
            assertThat(state.inputText).isEqualTo(TYPED)
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    @Test
    fun `a refused temporary chat is un-sent - it never reloads to show the server's copy`() =
        runTest(testDispatcher) {
            val vm = sendRefused(MODERATION, serverAfterFailure = listOf(user, reply), temporary = true)
            try {
                val state = vm.uiState.value
                assertThat(state.error).isEqualTo(StreamErrorType.MODERATION.marker)
                assertThat(state.inputText).isEqualTo(TYPED)
                assertThat(state.displayMessages.map { it.message.text }).doesNotContain(TYPED)
            } finally {
                vm.viewModelScope.cancel()
            }
        }

    @Test
    fun `a refusal naming an unknown code is a localizable notice, not English or JSON`() =
        runTest(testDispatcher) {
            val vm = sendRefused(UNKNOWN_REFUSAL, serverAfterFailure = emptyList(), conversationId = null)
            try {
                assertThat(vm.uiState.value.error).isEqualTo(ChatNotice.SEND_REFUSED.marker())
            } finally {
                vm.viewModelScope.cancel()
            }
        }

    private companion object {
        const val MESSAGE_LIMIT = """{"type":"message_limit","max":40,"limiter":"ip","windowInMinutes":1}"""
        const val MESSAGE_LIMIT_WITH_RETRY =
            """{"type":"message_limit","max":40,"limiter":"ip","windowInMinutes":1,"resetAt":1,"retryAfterSeconds":45}"""
        const val BAN = """{"type":"ban"}"""
        const val MODERATION = """{"type":"moderation"}"""
        const val UNKNOWN_REFUSAL = """{"type":"some_future_violation"}"""
        const val CONVERSATION_ID = "conv-1"
        const val ENDPOINT = "anthropic"
        const val MODEL = "claude-haiku-4-5"
        const val TYPED = "Twenty sentences, please"
        const val NO_PARENT = "00000000-0000-0000-0000-000000000000"
    }
}
