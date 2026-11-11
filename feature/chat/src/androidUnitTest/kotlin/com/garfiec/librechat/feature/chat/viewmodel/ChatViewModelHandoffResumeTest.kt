package com.garfiec.librechat.feature.chat.viewmodel

import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.ChatFontSize
import com.garfiec.librechat.core.data.datastore.ChatHeaderAlignment
import com.garfiec.librechat.core.data.datastore.ChatHeaderContent
import com.garfiec.librechat.core.data.datastore.ContextBarPlacement
import com.garfiec.librechat.core.data.datastore.StarredModelsDisplay
import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.Conversation
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.StreamEvent
import com.garfiec.librechat.core.model.content.MessageContentPart
import com.garfiec.librechat.core.model.response.ChatAbortResponse
import com.garfiec.librechat.core.model.response.ChatStatusResponse
import com.garfiec.librechat.core.model.response.StatusResumeState
import com.garfiec.librechat.core.model.response.StatusResumeUserMessage
import com.garfiec.librechat.feature.chat.viewmodel.delegate.PlatformFileHandler
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
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
            coEvery { messageRepository.getMessages(CONVERSATION_ID, any()) } coAnswers {
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
            choice = ModelChoice.Raw(ENDPOINT, MODEL),
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

    @Test
    fun `a handed-off first turn sent without a workspace says so on the chat it opens`() = runTest(testDispatcher) {
        coEvery { fixture.chatRepository.checkStreamStatus(eq(CONVERSATION_ID), any()) } coAnswers {
            onServer = listOf(user, reply)
            ChatStatusResponse(active = true)
        }
        every { fixture.chatRepository.resumeStream(CONVERSATION_ID) } returns emptyFlow()
        fixture.selectionHandoff.put(
            conversationId = CONVERSATION_ID,
            choice = ModelChoice.Raw(ENDPOINT, MODEL),
            optimisticUserMessage = user.copy(messageId = "client-minted-1", conversationId = ""),
            sentWithoutWorkspaceNotice = true,
        )
        // Relaxed, these answer an error that would stand in for the notice.
        coEvery { fixture.configRepository.fetchEndpoints() } returns Result.Success(mapOf(ENDPOINT to EndpointConfig()))
        coEvery { fixture.configRepository.fetchModels() } returns Result.Success(mapOf(ENDPOINT to listOf(MODEL)))
        val vm = fixture.build(defaultDispatcher = testDispatcher, initialConversationId = CONVERSATION_ID)
        try {
            advanceUntilIdle()

            assertThat(vm.uiState.value.error).isEqualTo(ChatNotice.CODE_WORKSPACE_SENT_WITHOUT.marker())
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    /**
     * New-chat handoff where the landing screen's stream dropped mid-reply: the server saved what
     * had streamed as an `unfinished` partial. The resumed stream finalizes the full reply, but the
     * open's message fetch was still out and answers with that partial (the server emits the final
     * frame before it saves the reply). The partial must not replace the finished reply, which
     * would show it cut off, as stopped, until the chat is reopened.
     */
    @Test
    fun `a fetch that answers after the resumed reply finalized cannot put its partial back`() =
        runTest(testDispatcher) {
            val partial = reply.copy(text = "Kap", unfinished = true)
            val fetchAnswers = CompletableDeferred<Unit>()
            coEvery { fixture.messageRepository.getMessages(CONVERSATION_ID, any()) } coAnswers {
                fetchAnswers.await()
                stored.value = listOf(user, partial)
                Result.Success(listOf(user, partial))
            }
            coEvery { fixture.messageRepository.cacheMessages(any(), any()) } coAnswers {
                val written = firstArg<List<Message>>().associateBy { it.messageId }
                stored.value = stored.value.map { written[it.messageId] ?: it } +
                    written.values.filter { w -> stored.value.none { it.messageId == w.messageId } }
            }
            coEvery { fixture.chatRepository.checkStreamStatus(eq(CONVERSATION_ID), any()) } returns
                ChatStatusResponse(active = true)
            every { fixture.chatRepository.resumeStream(CONVERSATION_ID) } returns
                flowOf(StreamEvent.Final(requestMessage = user, responseMessage = reply))
            fixture.selectionHandoff.put(
                conversationId = CONVERSATION_ID,
                choice = ModelChoice.Raw(ENDPOINT, MODEL),
                optimisticUserMessage = user.copy(messageId = "client-minted-1", conversationId = ""),
            )
            val vm = fixture.build(defaultDispatcher = testDispatcher, initialConversationId = CONVERSATION_ID)
            try {
                advanceUntilIdle()
                assertThat(vm.uiState.value.isStreaming).isFalse()

                fetchAnswers.complete(Unit)
                advanceUntilIdle()

                val shown = vm.uiState.value.displayMessages.last().message
                assertThat(shown.text).isEqualTo("Kappa")
                assertThat(shown.unfinished).isFalse()
                assertThat(vm.uiState.value.continuableReplyId).isNull()
            } finally {
                vm.viewModelScope.cancel()
            }
        }

    /**
     * New-chat handoff whose reply arrives through the resumed stream: the title event lands
     * mid-reply, then the final frame carries the conversation with the placeholder title (the
     * row as the reply's write returned it), and the row cached at `created` still has the
     * placeholder too. Neither may take the generated title off the top bar or out of Room.
     */
    @Test
    fun `a resumed final with the placeholder title keeps the generated title`() = runTest(testDispatcher) {
        val saved = mutableListOf<Conversation>()
        // Stands in for the conversation row: cached at `created`, before the title existed.
        coEvery { fixture.conversationRepository.getConversation(CONVERSATION_ID, any()) } coAnswers {
            Result.Success(saved.lastOrNull() ?: Conversation(conversationId = CONVERSATION_ID, title = "New Chat"))
        }
        coEvery { fixture.conversationRepository.saveConversation(any(), any()) } coAnswers {
            saved += firstArg<Conversation>()
        }
        coEvery { fixture.chatRepository.checkStreamStatus(eq(CONVERSATION_ID), any()) } returns
            ChatStatusResponse(active = true)
        every { fixture.chatRepository.resumeStream(CONVERSATION_ID) } returns flowOf(
            StreamEvent.TitleUpdate(conversationId = CONVERSATION_ID, title = GENERATED_TITLE),
            StreamEvent.Final(
                conversation = Conversation(conversationId = CONVERSATION_ID, title = "New Chat"),
                requestMessage = user,
                responseMessage = reply,
            ),
        )
        fixture.selectionHandoff.put(
            conversationId = CONVERSATION_ID,
            choice = ModelChoice.Raw(ENDPOINT, MODEL),
            optimisticUserMessage = user.copy(messageId = "client-minted-1", conversationId = ""),
        )
        val vm = fixture.build(defaultDispatcher = testDispatcher, initialConversationId = CONVERSATION_ID)
        try {
            advanceUntilIdle()

            assertThat(vm.uiState.value.isStreaming).isFalse()
            assertThat(vm.uiState.value.conversationTitle).isEqualTo(GENERATED_TITLE)
            assertThat(saved.map { it.title }).containsExactly(GENERATED_TITLE)
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    /**
     * The open's status check times out (the server stalled) while the handed-off run is alive and
     * waiting: the check is retried and the run attached, rather than the chat showing only the
     * user's message.
     */
    @Test
    fun `a status check that times out on open is retried, and the live run attached`() =
        runTest(testDispatcher) {
            var checks = 0
            coEvery { fixture.chatRepository.checkStreamStatus(eq(CONVERSATION_ID), any()) } coAnswers {
                checks++
                if (checks == 1) throw java.io.IOException("Request timeout has expired")
                ChatStatusResponse(active = true)
            }
            every { fixture.chatRepository.resumeStream(CONVERSATION_ID) } returns
                flowOf(StreamEvent.Final(requestMessage = user, responseMessage = reply))
            fixture.selectionHandoff.put(
                conversationId = CONVERSATION_ID,
                choice = ModelChoice.Raw(ENDPOINT, MODEL),
                optimisticUserMessage = user.copy(messageId = "client-minted-1", conversationId = ""),
            )
            val vm = fixture.build(defaultDispatcher = testDispatcher, initialConversationId = CONVERSATION_ID)
            try {
                advanceTimeBy(SETTLE_MS)
                runCurrent()

                assertThat(checks).isEqualTo(2)
                assertThat(displayedIds(vm)).containsExactly("user-1", "assistant-1").inOrder()
            } finally {
                vm.viewModelScope.cancel()
            }
        }

    /** A status check the server answered (a server without the route) is not asked again. */
    @Test
    fun `a status check the server refused on open is not retried`() = runTest(testDispatcher) {
        var checks = 0
        coEvery { fixture.chatRepository.checkStreamStatus(eq(CONVERSATION_ID), any()) } coAnswers {
            checks++
            throw com.garfiec.librechat.core.common.result.ApiException(statusCode = 404, message = "Not found")
        }
        val vm = fixture.build(defaultDispatcher = testDispatcher, initialConversationId = CONVERSATION_ID)
        try {
            advanceTimeBy(SETTLE_MS * 4)
            runCurrent()

            assertThat(checks).isEqualTo(1)
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    /**
     * Reopening a chat whose reply is still running. Leaving it dropped the last subscriber, so the
     * server saved what had streamed as an `unfinished` partial under the reply's id; the open reads
     * that partial and attaches to the run, whose bubble streams the same reply. It must show once,
     * also after a later read writes the partial again (the open's fetch, or the prefetcher).
     */
    @Test
    fun `a reopened chat shows its running reply once, not its saved partial beside it`() =
        runTest(testDispatcher) {
            val partial = reply.copy(text = "Kap", unfinished = true)
            stored.value = listOf(user, partial)
            val fetchAnswers = CompletableDeferred<Unit>()
            coEvery { fixture.messageRepository.getMessages(CONVERSATION_ID, any()) } coAnswers {
                fetchAnswers.await()
                stored.value = listOf(user, partial.copy(text = "Kapp"))
                Result.Success(stored.value)
            }
            coEvery { fixture.chatRepository.checkStreamStatus(eq(CONVERSATION_ID), any()) } returns
                ChatStatusResponse(
                    active = true,
                    resumeState = StatusResumeState(
                        userMessage = StatusResumeUserMessage(messageId = "user-1"),
                        responseMessageId = "assistant-1",
                    ),
                )
            every { fixture.chatRepository.resumeStream(CONVERSATION_ID) } returns flow {
                emit(StreamEvent.Sync(aggregatedContent = listOf(MessageContentPart(type = ContentType.TEXT, text = "Kapp"))))
                awaitCancellation()
            }
            val vm = fixture.build(defaultDispatcher = testDispatcher, initialConversationId = CONVERSATION_ID)
            try {
                advanceTimeBy(SETTLE_MS)
                runCurrent()

                assertThat(vm.uiState.value.isStreaming).isTrue()
                assertThat(displayedIds(vm)).containsExactly("user-1")
                assertThat(vm.uiState.value.continuableReplyId).isNull()

                fetchAnswers.complete(Unit)
                advanceTimeBy(SETTLE_MS)
                runCurrent()

                assertThat(vm.uiState.value.isStreaming).isTrue()
                assertThat(displayedIds(vm)).containsExactly("user-1")
            } finally {
                vm.viewModelScope.cancel()
            }
        }

    /**
     * A cold start: the app was killed mid-run on a chat's first turn, and the prefetcher cached
     * what the server held before the chat was reopened. The server saved the request under an id of
     * its own, so the partial it wrote when the kill dropped the subscriber hangs from an id the tree
     * does not hold and reads as a root of its own. The cache emits before the status check answers,
     * and the partial must still not show beside the live reply, nor take the path over from the
     * request.
     */
    @Test
    fun `a cold-start reopen whose partial hangs from an unsaved request id shows the running reply once`() =
        runTest(testDispatcher) {
            val partial = reply.copy(text = "Kap", unfinished = true, parentMessageId = "client-sent-1")
            stored.value = listOf(user, partial)
            onServer = listOf(user, partial)
            val statusAnswers = CompletableDeferred<Unit>()
            coEvery { fixture.chatRepository.checkStreamStatus(eq(CONVERSATION_ID), any()) } coAnswers {
                statusAnswers.await()
                ChatStatusResponse(
                    active = true,
                    resumeState = StatusResumeState(
                        userMessage = StatusResumeUserMessage(messageId = "client-sent-1"),
                        responseMessageId = "assistant-1",
                    ),
                )
            }
            every { fixture.chatRepository.resumeStream(CONVERSATION_ID) } returns flow {
                emit(StreamEvent.Sync(aggregatedContent = listOf(MessageContentPart(type = ContentType.TEXT, text = "Kapp"))))
                awaitCancellation()
            }
            val vm = fixture.build(defaultDispatcher = testDispatcher, initialConversationId = CONVERSATION_ID)
            try {
                advanceTimeBy(STEP_MS)
                runCurrent()
                assertThat(vm.uiState.value.isStreaming).isFalse()
                assertThat(displayedIds(vm)).isNotEmpty()

                statusAnswers.complete(Unit)
                advanceTimeBy(SETTLE_MS)
                runCurrent()

                assertThat(vm.uiState.value.isStreaming).isTrue()
                assertThat(displayedIds(vm)).containsExactly("user-1")

                // A later read (the open's revalidate, or the prefetcher) writes the partial again.
                stored.value = listOf(user, partial.copy(text = "Kapp"))
                advanceTimeBy(SETTLE_MS)
                runCurrent()
                assertThat(displayedIds(vm)).containsExactly("user-1")
            } finally {
                vm.viewModelScope.cancel()
            }
        }

    /** The same on a server whose status carries no snapshot: the partial at the tail is the reply. */
    @Test
    fun `a reopened chat without the status snapshot still hides the running reply's partial`() =
        runTest(testDispatcher) {
            val partial = reply.copy(text = "Kap", unfinished = true)
            stored.value = listOf(user, partial)
            onServer = listOf(user, partial)
            coEvery { fixture.chatRepository.checkStreamStatus(eq(CONVERSATION_ID), any()) } returns
                ChatStatusResponse(active = true)
            every { fixture.chatRepository.resumeStream(CONVERSATION_ID) } returns flow {
                emit(StreamEvent.Sync(aggregatedContent = listOf(MessageContentPart(type = ContentType.TEXT, text = "Kapp"))))
                awaitCancellation()
            }
            val vm = fixture.build(defaultDispatcher = testDispatcher, initialConversationId = CONVERSATION_ID)
            try {
                advanceTimeBy(SETTLE_MS)
                runCurrent()

                assertThat(vm.uiState.value.isStreaming).isTrue()
                assertThat(displayedIds(vm)).containsExactly("user-1")
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
        fixture.selectionHandoff.put(conversationId = CONVERSATION_ID, choice = ModelChoice.Raw(ENDPOINT, MODEL))
        val vm = fixture.build(defaultDispatcher = testDispatcher, initialConversationId = CONVERSATION_ID)
        try {
            advanceUntilIdle()
            assertThat(displayedIds(vm)).containsExactly("user-1", "assistant-1").inOrder()

            // While offline, the server persists the new turn: the user's message under the id the
            // client minted, and the full reply under it.
            coEvery { fixture.messageRepository.getMessages(CONVERSATION_ID, any()) } coAnswers {
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
        fixture.selectionHandoff.put(conversationId = CONVERSATION_ID, choice = ModelChoice.Raw(ENDPOINT, MODEL))
        val vm = fixture.build(defaultDispatcher = testDispatcher, initialConversationId = CONVERSATION_ID)
        try {
            advanceUntilIdle()
            var fetchesAfterSend = 0
            coEvery { fixture.messageRepository.getMessages(CONVERSATION_ID, any()) } coAnswers {
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
        coEvery { fixture.messageRepository.getMessages(NEW_CONVERSATION_ID, any()) } coAnswers {
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

    /**
     * The chat is open with no run, and a reply is started from another device. The server keeps
     * that reply off the message list until it ends, so a pull-to-refresh must attach to the run.
     */
    @Test
    fun `a refresh attaches to a run another device started`() = runTest(testDispatcher) {
        var runStarted = false
        coEvery { fixture.chatRepository.checkStreamStatus(eq(CONVERSATION_ID), any()) } coAnswers {
            ChatStatusResponse(active = runStarted)
        }
        coEvery { fixture.messageRepository.refreshMessages(CONVERSATION_ID, any(), any()) } coAnswers {
            stored.value = onServer
            Result.Success(onServer)
        }
        every { fixture.chatRepository.resumeStream(CONVERSATION_ID) } returns flow {
            emit(StreamEvent.ContentDelta(chunk = "Kap"))
            awaitCancellation()
        }
        onServer = listOf(user)
        val vm = fixture.build(defaultDispatcher = testDispatcher, initialConversationId = CONVERSATION_ID)
        try {
            advanceUntilIdle()
            assertThat(vm.uiState.value.isStreaming).isFalse()

            runStarted = true
            vm.refreshMessages()
            advanceTimeBy(STEP_MS)
            runCurrent()

            assertThat(vm.uiState.value.isStreaming).isTrue()
            assertThat(vm.uiState.value.streamingContent).isEqualTo("Kap")
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    @Test
    fun `a refresh during a reply already on screen does not attach to it again`() = runTest(testDispatcher) {
        coEvery { fixture.chatRepository.checkStreamStatus(eq(CONVERSATION_ID), any()) } returns
            ChatStatusResponse(active = true)
        coEvery { fixture.messageRepository.refreshMessages(CONVERSATION_ID, any(), any()) } returns
            Result.Success(listOf(user))
        every { fixture.chatRepository.resumeStream(CONVERSATION_ID) } returns flow {
            emit(StreamEvent.ContentDelta(chunk = "Kap"))
            awaitCancellation()
        }
        onServer = listOf(user)
        val vm = fixture.build(defaultDispatcher = testDispatcher, initialConversationId = CONVERSATION_ID)
        try {
            advanceTimeBy(STEP_MS)
            runCurrent()
            assertThat(vm.uiState.value.isStreaming).isTrue()

            vm.refreshMessages()
            advanceTimeBy(STEP_MS)
            runCurrent()

            verify(exactly = 1) { fixture.chatRepository.resumeStream(CONVERSATION_ID) }
            assertThat(vm.uiState.value.streamingContent).isEqualTo("Kap")
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
        const val GENERATED_TITLE = "The Slow Burning Final Smoke"
        const val NO_PARENT = "00000000-0000-0000-0000-000000000000"
    }
}
