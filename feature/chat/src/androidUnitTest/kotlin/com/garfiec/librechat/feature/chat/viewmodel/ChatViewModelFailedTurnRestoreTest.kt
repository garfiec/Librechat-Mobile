package com.garfiec.librechat.feature.chat.viewmodel

import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.StreamEvent
import com.garfiec.librechat.feature.chat.util.AbortFrameFixtures
import com.garfiec.librechat.feature.chat.viewmodel.delegate.PlatformFileHandler
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
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
    ): ChatViewModel {
        every {
            fixture.chatRepository.startChat(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(),
            )
        } returns flowOf(*events)
        fixture.selectionHandoff.put(conversationId = CONVERSATION_ID, endpoint = ENDPOINT, model = MODEL)
        val vm = fixture.build(defaultDispatcher = testDispatcher, initialConversationId = CONVERSATION_ID)
        advanceUntilIdle()
        // The post-error reload: Room then holds whatever the server kept.
        coEvery { fixture.messageRepository.getMessages(CONVERSATION_ID) } coAnswers {
            stored.value = serverAfterFailure
            Result.Success(serverAfterFailure)
        }
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

    private companion object {
        const val CONVERSATION_ID = "conv-1"
        const val ENDPOINT = "anthropic"
        const val MODEL = "claude-haiku-4-5"
        const val TYPED = "Twenty sentences, please"
        const val NO_PARENT = "00000000-0000-0000-0000-000000000000"
    }
}
