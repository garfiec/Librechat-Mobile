package com.garfiec.librechat.feature.chat.viewmodel

import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.ChatFontSize
import com.garfiec.librechat.core.data.datastore.ChatHeaderAlignment
import com.garfiec.librechat.core.data.datastore.ChatHeaderContent
import com.garfiec.librechat.core.data.datastore.ContextBarPlacement
import com.garfiec.librechat.core.data.datastore.StarredModelsDisplay
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.StreamEvent
import com.garfiec.librechat.core.model.response.ChatAbortResponse
import com.garfiec.librechat.core.model.response.ChatStatusResponse
import com.garfiec.librechat.feature.chat.viewmodel.delegate.PlatformFileHandler
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * A reply that finished on the server while this client was not attached must still reach the
 * screen once the stream ends — the resume answers 404 because the job is gone, and the SSE client
 * ends such a flow with no event at all.
 *
 * Asserted on what is DISPLAYED, not on whether a fetch was made: a refetch can happen while the
 * chat still shows the user's message alone. The message store here
 * behaves like the real one — `getMessages` writes what the server returned into the flow
 * `observeMessages` reads, as the repository's Room upsert does — so a refetch that is made but
 * never rendered fails the test. And like the real server it re-mints the user message's id —
 * it persists under its own id, not the one the client sent — which is what a same-id fake hides.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelHandoffResumeTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fixture = ChatViewModelTestFixture()
    private val fileHandler = mockk<PlatformFileHandler>(relaxed = true)

    /** Stands in for Room: what the last server read wrote. */
    private val stored = MutableStateFlow<List<Message>>(emptyList())

    /** What the server holds; changes when the reply is persisted. */
    private var onServer: List<Message> = emptyList()

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
            every { serverDataStore.currentUrlFlow } returns flowOf("https://example.test")
            every { settingsDataStore.chatFontSize } returns flowOf(ChatFontSize.MEDIUM)
            every { settingsDataStore.starredModelsDisplay } returns flowOf(StarredModelsDisplay.OFF)
            every { settingsDataStore.chatHeaderContent } returns flowOf(ChatHeaderContent.TITLE)
            every { settingsDataStore.chatHeaderAlignment } returns flowOf(ChatHeaderAlignment.LEFT)
            every { settingsDataStore.contextBarPlacement } returns flowOf(ContextBarPlacement.OPTIONS_SHEET)
            every { settingsDataStore.contextGaugeExpanded } returns flowOf(false)
            coEvery { conversationRepository.getConversation(any(), any()) } returns Result.Error(message = "test")
            // buildSendSpec reads the attachment list; the relaxed factory's stub fails the List cast.
            every { platformDelegateFactory.createFileHandler(any()) } returns fileHandler
            every { fileHandler.attachedFiles } returns MutableStateFlow(emptyList())
            every { messageRepository.observeMessages(CONVERSATION_ID) } returns stored
            coEvery { messageRepository.getMessages(CONVERSATION_ID) } coAnswers {
                stored.value = onServer
                Result.Success(onServer)
            }
        }
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.displayedIds(vm: ChatViewModel) =
        vm.uiState.value.displayMessages.map { it.message.messageId }

    /**
     * New-chat handoff: the landing screen hands the run to the conversation's screen, which reads
     * the conversation (nothing persisted yet — the server saves the request with the reply), sees
     * the run still active, and resumes. The run finishes in that gap, so the resume 404s.
     */
    @Test
    fun `a handed-off run that finished before the resume shows its reply`() = runTest(testDispatcher) {
        coEvery { fixture.chatRepository.checkStreamStatus(eq(CONVERSATION_ID), any()) } coAnswers {
            // Between the status read and the stream GET, the run completes and is persisted.
            onServer = listOf(user, reply)
            ChatStatusResponse(active = true)
        }
        every { fixture.chatRepository.resumeStream(CONVERSATION_ID) } returns emptyFlow()
        fixture.selectionHandoff.put(
            conversationId = CONVERSATION_ID,
            endpoint = ENDPOINT,
            model = MODEL,
            // The id the client minted; the server persists the same message as "user-1".
            optimisticUserMessage = user.copy(messageId = "client-minted-1", conversationId = ""),
        )
        val vm = fixture.build(defaultDispatcher = testDispatcher, initialConversationId = CONVERSATION_ID)
        try {
            advanceUntilIdle()

            assertThat(displayedIds(vm)).containsExactly("user-1", "assistant-1").inOrder()
            assertThat(vm.uiState.value.isStreaming).isFalse()
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    /**
     * Existing conversation, network drop mid-reply: the SSE client retries on its own, the
     * reconnect's resume 404s because the run finished while offline, and the send's flow ends with
     * neither Final nor Error. The partial must give way to the persisted reply.
     */
    @Test
    fun `a reply that finished during a network drop replaces the frozen partial`() = runTest(testDispatcher) {
        onServer = listOf(user, reply)
        stored.value = listOf(user, reply)
        coEvery { fixture.chatRepository.checkStreamStatus(eq(CONVERSATION_ID), any()) } returns
            ChatStatusResponse(active = false)
        // The send streams a partial, then ends cleanly: the SSE client's reconnect got a 404.
        every {
            fixture.chatRepository.startChat(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(),
            )
        } returns flowOf(StreamEvent.ContentDelta(chunk = "Alpine meadows on "))
        fixture.selectionHandoff.put(conversationId = CONVERSATION_ID, endpoint = ENDPOINT, model = MODEL)
        val vm = fixture.build(defaultDispatcher = testDispatcher, initialConversationId = CONVERSATION_ID)
        try {
            advanceUntilIdle()
            assertThat(displayedIds(vm)).containsExactly("user-1", "assistant-1").inOrder()

            // While offline, the server persists the new turn: the user's message under the id the
            // client minted, and the full reply under it.
            coEvery { fixture.messageRepository.getMessages(CONVERSATION_ID) } coAnswers {
                val sent = vm.uiState.value.messages.last { it.isCreatedByUser }
                // Persisted under the server's own id, not the one the client minted.
                val persisted = sent.copy(messageId = "server-user-2", conversationId = CONVERSATION_ID)
                onServer = listOf(
                    user,
                    reply,
                    persisted,
                    Message(
                        messageId = "assistant-2",
                        conversationId = CONVERSATION_ID,
                        parentMessageId = persisted.messageId,
                        text = "Alpine meadows on mountain slopes display wildflowers.",
                        isCreatedByUser = false,
                    ),
                )
                stored.value = onServer
                Result.Success(onServer)
            }
            vm.onInputChanged("Twenty sentences, please")
            vm.sendMessage()
            // Bounded, not advanceUntilIdle: a stream that never ends keeps the streaming updater
            // ticking, and an unbounded advance would hang instead of failing.
            advanceTimeBy(SETTLE_MS)
            runCurrent()

            assertThat(vm.uiState.value.isStreaming).isFalse()
            // The whole path, not just its tail: no client-minted copy of the sent message left
            // beside the server's.
            assertThat(displayedIds(vm))
                .containsExactly("user-1", "assistant-1", "server-user-2", "assistant-2").inOrder()
            assertThat(vm.uiState.value.streamingContent).isEmpty()
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    /**
     * A Stop is pending when the flow ends cleanly: the abort owns that ending (the aborted frame,
     * else the watchdog), and nothing reloads on an abort path — the server emits the aborted
     * frame before persisting, so a refetch races the save.
     */
    @Test
    fun `a stream that ends cleanly while a stop is pending does not reload`() = runTest(testDispatcher) {
        onServer = listOf(user, reply)
        stored.value = listOf(user, reply)
        coEvery { fixture.chatRepository.checkStreamStatus(eq(CONVERSATION_ID), any()) } returns
            ChatStatusResponse(active = false)
        coEvery { fixture.chatRepository.abortChat(CONVERSATION_ID, any(), any()) } returns
            Result.Success(ChatAbortResponse())
        val socketClosed = CompletableDeferred<Unit>()
        every {
            fixture.chatRepository.startChat(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(),
            )
        } returns flow {
            emit(StreamEvent.ContentDelta(chunk = "Alpine meadows on "))
            socketClosed.await()
        }
        fixture.selectionHandoff.put(conversationId = CONVERSATION_ID, endpoint = ENDPOINT, model = MODEL)
        val vm = fixture.build(defaultDispatcher = testDispatcher, initialConversationId = CONVERSATION_ID)
        try {
            advanceUntilIdle()
            var fetchesAfterSend = 0
            coEvery { fixture.messageRepository.getMessages(CONVERSATION_ID) } coAnswers {
                fetchesAfterSend++
                Result.Success(onServer)
            }
            vm.onInputChanged("Twenty sentences, please")
            vm.sendMessage()
            advanceTimeBy(STEP_MS)
            vm.stopGeneration()
            advanceTimeBy(STEP_MS)
            // The acked abort's frame never arrives; the stream just closes.
            socketClosed.complete(Unit)
            advanceTimeBy(STEP_MS)
            runCurrent()

            assertThat(fetchesAfterSend).isEqualTo(0)
            assertThat(vm.uiState.value.isStreaming).isTrue()

            // The watchdog ends it, still without a reload.
            advanceTimeBy(SETTLE_MS * 4)
            runCurrent()
            assertThat(vm.uiState.value.isStreaming).isFalse()
            assertThat(fetchesAfterSend).isEqualTo(0)
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    /**
     * The same, on a new chat — where no Room observer is running yet, so a fallback reload after
     * the send would fire here: a clean end with a Stop pending would refetch the conversation
     * while the abort is in flight, racing the save the aborted frame precedes.
     */
    @Test
    fun `a new chat whose stream ends cleanly while a stop is pending does not reload`() = runTest(testDispatcher) {
        coEvery { fixture.chatRepository.abortChat(any(), any(), any()) } returns
            Result.Success(ChatAbortResponse())
        var fetches = 0
        coEvery { fixture.messageRepository.getMessages(NEW_CONVERSATION_ID) } coAnswers {
            fetches++
            Result.Success(emptyList())
        }
        val socketClosed = CompletableDeferred<Unit>()
        every {
            fixture.chatRepository.startChat(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(),
            )
        } returns flow {
            emit(StreamEvent.Created(conversationId = NEW_CONVERSATION_ID, messageId = "m1", parentMessageId = NO_PARENT))
            emit(StreamEvent.ContentDelta(chunk = "Alpine meadows on "))
            socketClosed.await()
        }
        val vm = fixture.build(defaultDispatcher = testDispatcher)
        try {
            advanceUntilIdle()
            vm.onModelSelected(ENDPOINT, MODEL)
            advanceUntilIdle()
            vm.onInputChanged("Twenty sentences, please")
            vm.sendMessage()
            advanceTimeBy(STEP_MS)
            assertThat(vm.uiState.value.isStreaming).isTrue()
            vm.stopGeneration()
            advanceTimeBy(STEP_MS)
            socketClosed.complete(Unit)
            advanceTimeBy(STEP_MS)
            runCurrent()

            assertThat(fetches).isEqualTo(0)
            // Past the watchdog, which ends it locally — still without a reload.
            advanceTimeBy(SETTLE_MS * 4)
            runCurrent()
            assertThat(vm.uiState.value.isStreaming).isFalse()
            assertThat(fetches).isEqualTo(0)
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    private companion object {
        const val CONVERSATION_ID = "conv-1"
        const val NEW_CONVERSATION_ID = "conv-new"
        const val ENDPOINT = "anthropic"
        const val MODEL = "claude-haiku-4-5"
        const val SETTLE_MS = 5_000L
        const val STEP_MS = 1_000L
        const val NO_PARENT = "00000000-0000-0000-0000-000000000000"
    }
}
