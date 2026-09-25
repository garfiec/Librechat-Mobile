package com.garfiec.librechat.feature.chat.viewmodel

import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.BackendBuildClass
import com.garfiec.librechat.core.common.DetectedBackend
import com.garfiec.librechat.core.data.datastore.ChatFontSize
import com.garfiec.librechat.core.data.datastore.ChatHeaderAlignment
import com.garfiec.librechat.core.data.datastore.ChatHeaderContent
import com.garfiec.librechat.core.data.datastore.ContextBarPlacement
import com.garfiec.librechat.core.data.datastore.DuringRunAction
import com.garfiec.librechat.core.data.datastore.StarredModelsDisplay
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.StreamEvent
import com.garfiec.librechat.core.model.queuedturn.AgentQueuedTurnReceipt
import com.garfiec.librechat.core.model.queuedturn.EnqueueQueuedTurnRequest
import com.garfiec.librechat.core.model.queuedturn.QueuedTurnOutcome
import com.garfiec.librechat.core.model.queuedturn.QueuedTurnStatus
import com.garfiec.librechat.core.model.response.ChatStatusResponse
import com.garfiec.librechat.feature.chat.viewmodel.delegate.PlatformFileHandler
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
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
 * Where the local queue and the server queue meet.
 *
 * Every assertion here is about the composition rather than one delegate, because that is where
 * this feature can go wrong silently: the enqueue can post a correct-looking request built from
 * the wrong anchor, or a row can become server-owned while the drain still believes it owns it.
 * Both compile, and both pass a delegate-level test.
 *
 * The stream is opened by resume rather than by a send, and the tests drive with `runCurrent`,
 * for the reasons set out in `ChatViewModelDuringRunSendTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelQueuedTurnTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val fixture = ChatViewModelTestFixture()
    private val chatRepository get() = fixture.chatRepository
    private val configRepository get() = fixture.configRepository
    private val conversationRepository get() = fixture.conversationRepository
    private val queuedTurnRepository get() = fixture.queuedTurnRepository
    private val serverDataStore get() = fixture.serverDataStore
    private val settingsDataStore get() = fixture.settingsDataStore
    private val platformDelegateFactory get() = fixture.platformDelegateFactory
    private val selectionHandoff get() = fixture.selectionHandoff
    private val fileHandler = mockk<PlatformFileHandler>(relaxed = true)

    private val resumedStream = MutableSharedFlow<StreamEvent>(extraBufferCapacity = 8)

    /** The fake server's queue. Written by the enqueue stub, read by the list stub. */
    private val serverRows = mutableListOf<AgentQueuedTurnReceipt>()

    /** Holds an enqueue POST open so a row can be observed while it is still `Sending`. */
    private val enqueueGate = CompletableDeferred<Unit>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fixture.stubDefaults()

        every { configRepository.detectedBackend } returns
            MutableStateFlow(DetectedBackend("0.8.8-rc3", BackendBuildClass.RC))
        every { configRepository.detectedBackendVersion } returns MutableStateFlow("0.8.8-rc3")
        every { configRepository.endpointConfigs } returns
            MutableStateFlow(mapOf(ENDPOINT to EndpointConfig()))
        every { configRepository.availableModels } returns MutableStateFlow(mapOf(ENDPOINT to listOf(AGENT_ID)))
        every { settingsDataStore.selectedMcpServers } returns flowOf(emptySet())
        every { settingsDataStore.enabledTools } returns flowOf(emptySet())
        every { settingsDataStore.duringRunAction } returns flowOf(DuringRunAction.QUEUE)
        every { serverDataStore.currentUrlFlow } returns flowOf("https://example.test")
        every { settingsDataStore.chatFontSize } returns flowOf(ChatFontSize.MEDIUM)
        every { settingsDataStore.starredModelsDisplay } returns flowOf(StarredModelsDisplay.OFF)
        every { settingsDataStore.chatHeaderContent } returns flowOf(ChatHeaderContent.TITLE)
        every { settingsDataStore.chatHeaderAlignment } returns flowOf(ChatHeaderAlignment.LEFT)
        every { settingsDataStore.contextBarPlacement } returns flowOf(ContextBarPlacement.OPTIONS_SHEET)
        every { settingsDataStore.contextGaugeExpanded } returns flowOf(false)
        every { platformDelegateFactory.createFileHandler(any()) } returns fileHandler
        every { fileHandler.attachedFiles } returns MutableStateFlow(emptyList())
        coEvery { conversationRepository.getConversation(any(), any()) } returns
            com.garfiec.librechat.core.common.result.Result.Error(message = "test")

        // A real branch, so the anchor assertion distinguishes the visible LEAF from the root —
        // with a one-message tree, taking the wrong end of the path would read identically.
        every { fixture.messageRepository.observeMessages(CONVERSATION_ID) } returns flowOf(
            listOf(
                Message(messageId = "user-0", conversationId = CONVERSATION_ID, text = "first", isCreatedByUser = true),
                Message(
                    messageId = "assistant-0",
                    conversationId = CONVERSATION_ID,
                    parentMessageId = "user-0",
                    text = "reply",
                    isCreatedByUser = false,
                ),
                Message(
                    messageId = USER_MESSAGE_ID,
                    conversationId = CONVERSATION_ID,
                    parentMessageId = "assistant-0",
                    text = "the turn in flight",
                    isCreatedByUser = true,
                ),
            ),
        )

        // `createdAt` is the run's generation epoch — the boundary a queued turn follows. Without
        // it nothing can become server-owned, so every assertion below would pass vacuously.
        coEvery { chatRepository.checkStreamStatus(eq(CONVERSATION_ID), any()) } returns
            ChatStatusResponse(active = true, createdAt = EPOCH)
        every { chatRepository.resumeStream(any()) } returns resumedStream

        // A CONSISTENT fake server: what the enqueue accepts is what the list then reports. A stub
        // that answers an enqueue and then reports no rows is not a server any of this has to
        // survive, and assertions built on one pass for the wrong reason — the projection is
        // authoritative, so an empty snapshot legitimately retires a row it does not mention.
        coEvery { queuedTurnRepository.isUnsupported() } returns false
        coEvery { queuedTurnRepository.list(any(), any()) } answers {
            QueuedTurnOutcome.Committed(serverRows.toList())
        }
        coEvery { queuedTurnRepository.enqueue(any()) } answers {
            val receipt = receiptFor(firstArg())
            serverRows.add(receipt)
            QueuedTurnOutcome.Committed(receipt)
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a follow-up queued during an agent run is handed to the server`() = queuedTurnTest { vm ->
        assertThat(vm.uiState.value.isStreaming).isTrue()

        vm.onInputChanged(TEXT)
        vm.queueMessage()
        runCurrent()

        val request = slot<EnqueueQueuedTurnRequest>()
        coVerify(exactly = 1) { queuedTurnRepository.enqueue(capture(request)) }
        assertThat(request.captured.conversationId).isEqualTo(CONVERSATION_ID)
        assertThat(request.captured.text).isEqualTo(TEXT)
        // The anchor is the running turn's USER message, not the reply — the reply has no server
        // id yet. The server treats it as a branch anchor and walks forward to the newest
        // assistant descendant, which is how a queue of several chains correctly.
        assertThat(request.captured.parentMessageId).isEqualTo(USER_MESSAGE_ID)
        assertThat(request.captured.expectedPredecessorCreatedAt).isEqualTo(EPOCH)
    }

    @Test
    fun `the row is server-owned and does not drain when the run ends`() = queuedTurnTest { vm ->
        vm.onInputChanged(TEXT)
        vm.queueMessage()
        runCurrent()

        val queued = vm.uiState.value.messageQueue.single()
        assertThat(queued.server).isNotNull()
        assertThat(queued.server?.status).isEqualTo(QueuedTurnServerState.Status.Queued)
        assertThat(queued.clientRequestId).isNotNull()

        // The run finishes. A legacy row would be sent from here; a server-owned one must not be,
        // because there is no server-side guard against the turn being run twice.
        resumedStream.emit(StreamEvent.Final())
        runCurrent()

        coVerify(exactly = 0) {
            chatRepository.startChat(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(),
            )
        }
        assertThat(vm.uiState.value.messageQueue).hasSize(1)
    }

    @Test
    fun `the idempotency key is minted per turn, not taken from the local id`() = queuedTurnTest { vm ->
        // localId survives an edit, so reusing it as clientRequestId would address the row with
        // different text on the retry — a 409 the user would see as a follow-up that vanished.
        vm.onInputChanged(TEXT)
        vm.queueMessage()
        runCurrent()

        val queued = vm.uiState.value.messageQueue.single()
        assertThat(queued.clientRequestId).isNotEqualTo(queued.localId)
    }

    @Test
    fun `a server without the routes leaves the follow-up on the legacy drain`() = queuedTurnTest(
        arrange = {
            coEvery { queuedTurnRepository.enqueue(any()) } returns QueuedTurnOutcome.Unsupported
        },
    ) { vm ->
        vm.onInputChanged(TEXT)
        vm.queueMessage()
        runCurrent()

        // Every trace of server ownership is stripped, so the row behaves exactly as it did
        // before this feature existed.
        val queued = vm.uiState.value.messageQueue.single()
        assertThat(queued.server).isNull()
        assertThat(queued.clientRequestId).isNull()
        assertThat(queued.parentMessageId).isNull()
    }

    /**
     * rc1 stamps `generationCreatedAt`, so every other precondition for server ownership holds
     * there, but it has no queued-turns route: its chat router takes the POST as `/:endpoint` and
     * fails it without a 404, so the fallback above never sees that server. Nothing may reach it —
     * not the enqueue, and not the reconcile poll that starts on opening the conversation.
     */
    @Test
    fun `an rc1 server is never sent a queued-turn request`() = queuedTurnTest(
        arrange = {
            every { configRepository.detectedBackend } returns
                MutableStateFlow(DetectedBackend("0.8.8-rc1", BackendBuildClass.RC))
            every { configRepository.detectedBackendVersion } returns MutableStateFlow("0.8.8-rc1")
        },
    ) { vm ->
        assertThat(vm.uiState.value.isStreaming).isTrue()

        vm.onInputChanged(TEXT)
        vm.queueMessage()
        runCurrent()

        assertThat(vm.uiState.value.messageQueue.single().server).isNull()
        coVerify(exactly = 0) { queuedTurnRepository.enqueue(any()) }
        coVerify(exactly = 0) { queuedTurnRepository.list(any(), any()) }
    }

    @Test
    fun `an unanswered enqueue is held, never handed back`() = queuedTurnTest(
        arrange = {
            coEvery { queuedTurnRepository.enqueue(any()) } returns
                QueuedTurnOutcome.Indeterminate(statusCode = null, code = null, message = "dropped")
        },
    ) { vm ->
        vm.onInputChanged(TEXT)
        vm.queueMessage()
        runCurrent()

        // The POST may have landed. Handing the row back to the local drain here is exactly how
        // the same words get submitted twice.
        val queued = vm.uiState.value.messageQueue.single()
        assertThat(queued.server?.status).isEqualTo(QueuedTurnServerState.Status.Uncertain)
        assertThat(queued.server?.uncertainSince).isNotNull()
    }

    @Test
    fun `a definite refusal is held for the user rather than resent`() = queuedTurnTest(
        arrange = {
            coEvery { queuedTurnRepository.enqueue(any()) } returns
                QueuedTurnOutcome.Rejected(statusCode = 429, code = "QUEUED_TURN_QUEUE_FULL", message = "full")
        },
    ) { vm ->
        vm.onInputChanged(TEXT)
        vm.queueMessage()
        runCurrent()

        val queued = vm.uiState.value.messageQueue.single()
        assertThat(queued.server?.status).isEqualTo(QueuedTurnServerState.Status.Rejected)
        assertThat(queued.server?.errorCode).isEqualTo("QUEUED_TURN_QUEUE_FULL")
    }

    @Test
    fun `a queue held on the server is rediscovered with no local row to start from`() =
        queuedTurnTest(
            arrange = {
                // What a restart looks like: the process that enqueued this is gone, and the
                // memory-only queue with it. The list route answers with every live row for the
                // conversation, not just the ids asked for, which is the only way back.
                serverRows.add(
                    AgentQueuedTurnReceipt(
                        queuedTurnId = "qt-orphan",
                        clientRequestId = "req-orphan",
                        conversationId = CONVERSATION_ID,
                        parentMessageId = USER_MESSAGE_ID,
                        text = "left over from last time",
                        status = QueuedTurnStatus.QUEUED,
                        revision = 3,
                    ),
                )
            },
        ) { vm ->
            runCurrent()

            val recovered = vm.uiState.value.messageQueue.single()
            assertThat(recovered.text).isEqualTo("left over from last time")
            assertThat(recovered.clientRequestId).isEqualTo("req-orphan")
            assertThat(recovered.server?.id).isEqualTo("qt-orphan")
        }

    @Test
    fun `the second queued item follows the same anchor as the first`() = queuedTurnTest { vm ->
        // Its real parent is the reply to the item ahead of it, which does not exist yet. The
        // server resolves that itself by walking forward from the anchor, so both turns capture
        // the same one — the client never needs an id it cannot have.
        vm.onInputChanged("first follow-up")
        vm.queueMessage()
        runCurrent()
        vm.onInputChanged("second follow-up")
        vm.queueMessage()
        runCurrent()

        val requests = mutableListOf<EnqueueQueuedTurnRequest>()
        coVerify(exactly = 2) { queuedTurnRepository.enqueue(capture(requests)) }
        assertThat(requests.map { it.parentMessageId })
            .containsExactly(USER_MESSAGE_ID, USER_MESSAGE_ID)
        assertThat(requests.map { it.clientRequestId }.toSet()).hasSize(2)
    }

    @Test
    fun `a row whose POST has not answered cannot be cancelled`() = queuedTurnTest(
        arrange = {
            // Held open, so the row stays `Sending` — no queuedTurnId, and the request may
            // already have landed.
            coEvery { queuedTurnRepository.enqueue(any()) } coAnswers {
                enqueueGate.await()
                val receipt = receiptFor(firstArg())
                serverRows.add(receipt)
                QueuedTurnOutcome.Committed(receipt)
            }
        },
    ) { vm ->
        vm.onInputChanged(TEXT)
        vm.queueMessage()
        runCurrent()
        val queued = vm.uiState.value.messageQueue.single()
        assertThat(queued.server?.status).isEqualTo(QueuedTurnServerState.Status.Sending)

        vm.cancelQueued(queued.localId)
        runCurrent()

        // Dropping it locally here is how the server runs a turn nothing on screen knows about.
        assertThat(vm.uiState.value.messageQueue).hasSize(1)

        enqueueGate.complete(Unit)
        runCurrent()
        assertThat(vm.uiState.value.messageQueue.single().server?.status)
            .isEqualTo(QueuedTurnServerState.Status.Queued)
    }

    @Test
    fun `cancelling one queued row leaves the others alone`() = queuedTurnTest { vm ->
        vm.onInputChanged("first follow-up")
        vm.queueMessage()
        runCurrent()
        vm.onInputChanged("second follow-up")
        vm.queueMessage()
        runCurrent()
        assertThat(vm.uiState.value.messageQueue).hasSize(2)

        val first = vm.uiState.value.messageQueue.first()
        serverRows.removeAll { it.clientRequestId == first.clientRequestId }
        coEvery { queuedTurnRepository.cancel(first.server!!.id!!) } returns
            QueuedTurnOutcome.Committed(
                AgentQueuedTurnReceipt(
                    queuedTurnId = first.server!!.id!!,
                    clientRequestId = first.clientRequestId!!,
                    text = first.text,
                    status = QueuedTurnStatus.CANCELLED,
                ),
            )

        vm.cancelQueued(first.localId)
        runCurrent()

        // A cancel's receipt speaks for ONE row. Applied as a full snapshot it would retire every
        // other server-owned row it does not mention, unblocking the drain for a couple of
        // seconds against a boundary the server still owns.
        assertThat(vm.uiState.value.messageQueue.map { it.text }).containsExactly("second follow-up")
    }

    @Test
    fun `a refused row is withdrawn once when it is edited`() = queuedTurnTest(
        arrange = {
            coEvery { queuedTurnRepository.enqueue(any()) } returns
                QueuedTurnOutcome.Rejected(statusCode = 429, code = "QUEUED_TURN_QUEUE_FULL", message = "full")
        },
    ) { vm ->
        vm.onInputChanged(TEXT)
        vm.queueMessage()
        runCurrent()
        val rejected = vm.uiState.value.messageQueue.single()

        vm.editQueued(rejected.localId)
        runCurrent()
        assertThat(vm.uiState.value.isEditingQueued).isTrue()
        // Taken OUT for editing. A refused row has no queuedTurnId, so nothing removed it for us.
        assertThat(vm.uiState.value.messageQueue).isEmpty()

        vm.onInputChanged("edited follow-up")
        vm.commitQueuedEdit()
        runCurrent()

        val edited = vm.uiState.value.messageQueue.single()
        assertThat(edited.text).isEqualTo("edited follow-up")
        assertThat(edited.server).isNull()
    }

    /**
     * Editing a server-owned row withdraws it first, and that DELETE is a round trip. The guard on
     * [ChatViewModel.editQueued] runs before it, so a second tap in that window opens a competing
     * session — and whichever row loses the race has already been pulled out of the queue with
     * nothing left that will put it back.
     */
    @Test
    fun `a second edit tap during a withdrawal cannot strand the first row`() = queuedTurnTest { vm ->
        vm.onInputChanged("first follow-up")
        vm.queueMessage()
        runCurrent()
        vm.onInputChanged("second follow-up")
        vm.queueMessage()
        runCurrent()
        val (first, second) = vm.uiState.value.messageQueue
        assertThat(vm.uiState.value.messageQueue).hasSize(2)

        // Both withdrawals are held open, so the two taps overlap the way real taps do.
        val cancelGate = CompletableDeferred<Unit>()
        coEvery { queuedTurnRepository.cancel(any()) } coAnswers {
            cancelGate.await()
            val id = firstArg<String>()
            val row = listOf(first, second).first { it.server?.id == id }
            serverRows.removeAll { it.clientRequestId == row.clientRequestId }
            QueuedTurnOutcome.Committed(
                AgentQueuedTurnReceipt(
                    queuedTurnId = id,
                    clientRequestId = row.clientRequestId!!,
                    text = row.text,
                    status = QueuedTurnStatus.CANCELLED,
                ),
            )
        }

        vm.editQueued(first.localId)
        runCurrent()
        vm.editQueued(second.localId)
        runCurrent()
        cancelGate.complete(Unit)
        runCurrent()

        // Exactly one session, and every row is either in that session or still in the queue.
        val session = vm.uiState.value.editingQueuedItem
        assertThat(session).isNotNull()
        val accountedFor = vm.uiState.value.messageQueue.map { it.text } + session!!.original.text
        assertThat(accountedFor).containsExactly("first follow-up", "second follow-up")
    }

    /**
     * The same window, entered from the ×. `cancelQueued` READ the withdrawal fence and never
     * raised it, so its own DELETE left the window open: a second × on the same row, or a
     * tap-to-edit behind it, started a second withdrawal of a row the first had already taken,
     * and whichever landed second reported a refusal over a cancel that actually succeeded.
     *
     * The fence is global, so it also refuses a tap on a different row — which is why the refusal
     * has to say something rather than returning silently.
     */
    @Test
    fun `a second cancel tap during a withdrawal does not start a second delete`() = queuedTurnTest { vm ->
        vm.onInputChanged("first follow-up")
        vm.queueMessage()
        runCurrent()
        vm.onInputChanged("second follow-up")
        vm.queueMessage()
        runCurrent()
        val (first, second) = vm.uiState.value.messageQueue

        val cancelGate = CompletableDeferred<Unit>()
        coEvery { queuedTurnRepository.cancel(any()) } coAnswers {
            cancelGate.await()
            val id = firstArg<String>()
            val row = listOf(first, second).first { it.server?.id == id }
            serverRows.removeAll { it.clientRequestId == row.clientRequestId }
            QueuedTurnOutcome.Committed(
                AgentQueuedTurnReceipt(
                    queuedTurnId = id,
                    clientRequestId = row.clientRequestId!!,
                    text = row.text,
                    status = QueuedTurnStatus.CANCELLED,
                ),
            )
        }

        vm.cancelQueued(first.localId)
        runCurrent()
        // Same row again, then a different one, then a tap-to-edit: all inside the DELETE's window.
        vm.cancelQueued(first.localId)
        vm.cancelQueued(second.localId)
        vm.editQueued(second.localId)
        runCurrent()

        coVerify(exactly = 1) { queuedTurnRepository.cancel(any()) }
        assertThat(vm.uiState.value.error).isNotNull()
        assertThat(vm.uiState.value.editingQueuedItem).isNull()

        cancelGate.complete(Unit)
        runCurrent()

        // The one withdrawal that was allowed still completed, and the fence let go after it.
        assertThat(vm.uiState.value.messageQueue.map { it.text }).containsExactly("second follow-up")
    }

    /**
     * The same window, entered from a LEGACY row. A legacy edit opens its session synchronously, so
     * it never reaches the server-owned branch — and the withdrawal that is still in flight lands
     * on top of it, leaving the legacy row out of the queue with no session to put it back.
     */
    @Test
    fun `a legacy edit tap during a withdrawal cannot strand either row`() = queuedTurnTest { vm ->
        vm.onInputChanged("first follow-up")
        vm.queueMessage()
        runCurrent()
        // A server that takes the first turn but not the second leaves one row of each kind.
        coEvery { queuedTurnRepository.enqueue(match { it.text == "second follow-up" }) } returns
            QueuedTurnOutcome.Unsupported
        vm.onInputChanged("second follow-up")
        vm.queueMessage()
        runCurrent()
        val (first, second) = vm.uiState.value.messageQueue
        assertThat(first.server).isNotNull()
        assertThat(second.server).isNull()

        val cancelGate = CompletableDeferred<Unit>()
        coEvery { queuedTurnRepository.cancel(any()) } coAnswers {
            cancelGate.await()
            serverRows.removeAll { it.clientRequestId == first.clientRequestId }
            QueuedTurnOutcome.Committed(
                AgentQueuedTurnReceipt(
                    queuedTurnId = firstArg(),
                    clientRequestId = first.clientRequestId!!,
                    text = first.text,
                    status = QueuedTurnStatus.CANCELLED,
                ),
            )
        }

        vm.editQueued(first.localId)
        runCurrent()
        vm.editQueued(second.localId)
        runCurrent()
        cancelGate.complete(Unit)
        runCurrent()

        val session = vm.uiState.value.editingQueuedItem
        assertThat(session).isNotNull()
        val accountedFor = vm.uiState.value.messageQueue.map { it.text } + session!!.original.text
        assertThat(accountedFor).containsExactly("first follow-up", "second follow-up")
    }

    private fun receiptFor(request: EnqueueQueuedTurnRequest) = AgentQueuedTurnReceipt(
        queuedTurnId = "qt-1",
        clientRequestId = request.clientRequestId,
        conversationId = request.conversationId,
        parentMessageId = request.parentMessageId,
        text = request.text,
        status = QueuedTurnStatus.QUEUED,
        revision = 1,
        position = 1,
        expectedPredecessorCreatedAt = request.expectedPredecessorCreatedAt,
    )

    private fun queuedTurnTest(
        arrange: () -> Unit = {},
        body: suspend TestScope.(ChatViewModel) -> Unit,
    ) = runTest(testDispatcher) {
        arrange()
        // Seeds the running turn's user message into the tree. It is the anchor the enqueue has
        // to capture, so without it there is nothing for the assertions to be about.
        selectionHandoff.put(
            conversationId = CONVERSATION_ID,
            endpoint = ENDPOINT,
            model = AGENT_ID,
            optimisticUserMessage = Message(
                messageId = USER_MESSAGE_ID,
                conversationId = CONVERSATION_ID,
                text = "the turn in flight",
                isCreatedByUser = true,
            ),
        )
        val vm = fixture.build(
            defaultDispatcher = testDispatcher,
            initialConversationId = CONVERSATION_ID,
        )
        runCurrent()
        try {
            body(vm)
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    private companion object {
        const val CONVERSATION_ID = "conv-1"
        const val ENDPOINT = "agents"
        const val AGENT_ID = "agent_abc"
        const val USER_MESSAGE_ID = "user-1"
        const val TEXT = "and then summarise it"
        const val EPOCH = 1_758_000_000_000L
    }
}
