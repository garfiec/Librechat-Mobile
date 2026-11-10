package com.garfiec.librechat.feature.chat.viewmodel

import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.BackendBuildClass
import com.garfiec.librechat.core.common.DetectedBackend
import com.garfiec.librechat.core.common.ToolConstants
import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.common.identity.AccountState
import com.garfiec.librechat.core.common.identity.ActiveAccountProvider
import com.garfiec.librechat.core.common.identity.InMemoryActiveAccountProvider
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.ChatFontSize
import com.garfiec.librechat.core.data.datastore.ChatHeaderAlignment
import com.garfiec.librechat.core.data.datastore.ChatHeaderContent
import com.garfiec.librechat.core.data.datastore.ContextBarPlacement
import com.garfiec.librechat.core.data.datastore.DuringRunAction
import com.garfiec.librechat.core.data.datastore.LastUsedSelection
import com.garfiec.librechat.core.data.datastore.StarredModelsDisplay
import com.garfiec.librechat.core.data.repository.CodeDecisionMove
import com.garfiec.librechat.core.data.repository.CodeEnvironmentRepository
import com.garfiec.librechat.core.data.repository.SavedCodeDecision
import com.garfiec.librechat.core.model.Agent
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.PendingSteer
import com.garfiec.librechat.core.model.Preset
import com.garfiec.librechat.core.model.StreamEvent
import com.garfiec.librechat.core.model.code.CodeApprovalMode
import com.garfiec.librechat.core.model.code.CodeEnvironmentMode
import com.garfiec.librechat.core.model.code.CodeEnvironmentStatus
import com.garfiec.librechat.core.model.code.CodeWorkspaceDescriptor
import com.garfiec.librechat.core.model.code.CodeWorkspaceSelection
import com.garfiec.librechat.core.model.code.PublicCodeEnvironment
import com.garfiec.librechat.core.model.code.StatefulCodeSessionsConfig
import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.core.model.config.ModelSpecs
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.model.queuedturn.AgentQueuedTurnReceipt
import com.garfiec.librechat.core.model.queuedturn.EnqueueQueuedTurnRequest
import com.garfiec.librechat.core.model.queuedturn.QUEUED_TURN_RECONCILIATION_MS
import com.garfiec.librechat.core.model.queuedturn.QueuedTurnFailure
import com.garfiec.librechat.core.model.queuedturn.QueuedTurnFileRef
import com.garfiec.librechat.core.model.queuedturn.QueuedTurnOutcome
import com.garfiec.librechat.core.model.queuedturn.QueuedTurnStatus
import com.garfiec.librechat.core.model.response.ChatAbortResponse
import com.garfiec.librechat.core.model.response.ChatStatusResponse
import com.garfiec.librechat.core.model.response.SteerResponse
import com.garfiec.librechat.feature.chat.util.DraftItem
import com.garfiec.librechat.feature.chat.util.restore
import com.garfiec.librechat.feature.chat.viewmodel.delegate.PlatformFileHandler
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

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
@Suppress("LargeClass") // debt: 1017-line file
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
    fun `a queued turn takes the skill chips onto its enqueue claim`() = queuedTurnTest(
        arrange = {
            // An agents server that advertises skills: a persisted agent's run honours the
            // claim's manualSkills (upstream host.ts copies them into the run's body).
            every { configRepository.endpointConfigs } returns MutableStateFlow(
                mapOf(ENDPOINT to EndpointConfig(capabilities = listOf(ToolConstants.SKILLS))),
            )
        },
    ) { vm ->
        vm.addPendingManualSkill("pdf-tools")
        vm.onInputChanged(TEXT)
        vm.queueMessage()
        runCurrent()

        val request = slot<EnqueueQueuedTurnRequest>()
        coVerify(exactly = 1) { queuedTurnRepository.enqueue(capture(request)) }
        assertThat(request.captured.manualSkills).containsExactly("pdf-tools")
        assertThat(vm.uiState.value.pendingManualSkills).isEmpty()
        // Reconciled from the receipt, the row still holds the picks an edit would restore.
        assertThat(vm.uiState.value.messageQueue.single().manualSkills).containsExactly("pdf-tools")
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
     * Below v0.8.9-rc1 the server's queued turn carries no spec, so `enforce` refuses it and the
     * follow-up stays local (`QueueOrchestrationDelegate.serverOwnedSpec`).
     */
    @Test
    fun `under enforce the follow-up stays local before rc1`() = queuedTurnTest(
        choice = HELPER_SPEC_CHOICE,
        arrange = { arrangeEnforcedSpecs(helperSpec()) },
    ) { vm ->
        assertThat(vm.uiState.value.isStreaming).isTrue()

        vm.onInputChanged(TEXT)
        vm.queueMessage()
        runCurrent()

        coVerify(exactly = 0) { queuedTurnRepository.enqueue(any()) }
        val queued = vm.uiState.value.messageQueue.single()
        assertThat(queued.server).isNull()
        assertThat(queued.text).isEqualTo(TEXT)
    }

    /** rc1 restores the conversation's agents spec for a queued turn (`restoreAgentTriggerModelSpec`). */
    @Test
    fun `under enforce an agents spec's follow-up goes to the server on rc1`() = queuedTurnTest(
        choice = HELPER_SPEC_CHOICE,
        arrange = {
            arrangeBackend("0.8.9-rc1")
            arrangeEnforcedSpecs(helperSpec())
        },
    ) { vm ->
        vm.onInputChanged(TEXT)
        vm.queueMessage()
        runCurrent()

        coVerify(exactly = 1) { queuedTurnRepository.enqueue(any()) }
        assertThat(vm.uiState.value.messageQueue.single().server).isNotNull()
    }

    /** The server restores a spec only when its name is unique, so a shared name stays local. */
    @Test
    fun `under enforce a spec whose name is shared stays local on rc1`() = queuedTurnTest(
        choice = HELPER_SPEC_CHOICE,
        arrange = {
            arrangeBackend("0.8.9-rc1")
            arrangeEnforcedSpecs(helperSpec(), helperSpec().copy(label = "Other"))
        },
    ) { vm ->
        vm.onInputChanged(TEXT)
        vm.queueMessage()
        runCurrent()

        coVerify(exactly = 0) { queuedTurnRepository.enqueue(any()) }
        assertThat(vm.uiState.value.messageQueue.single().server).isNull()
    }

    private fun helperSpec() = ModelSpec(name = "helper", preset = Preset(endpoint = ENDPOINT, agentId = AGENT_ID))

    private fun arrangeEnforcedSpecs(vararg specs: ModelSpec) {
        // The spec config reaches the selection through the seeder's combine, which waits on this.
        every { settingsDataStore.lastUsedSelection } returns flowOf(LastUsedSelection.NONE)
        every { configRepository.startupConfig } returns MutableStateFlow(
            StartupConfig(modelSpecs = ModelSpecs(list = specs.toList(), enforce = true)),
        )
    }

    private fun arrangeBackend(version: String) {
        every { configRepository.detectedBackend } returns
            MutableStateFlow(DetectedBackend(version, BackendBuildClass.RC))
        every { configRepository.detectedBackendVersion } returns MutableStateFlow(version)
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

    /** A chat on a coding agent with an attached machine, so every turn carries an approval mode. */
    private fun arrangeCodeChat(serverVersion: String, buildClass: BackendBuildClass) {
        val machine = PublicCodeEnvironment(id = "mbp", name = "mbp", type = "attached")
        every { configRepository.detectedBackend } returns MutableStateFlow(DetectedBackend(serverVersion, buildClass))
        every { configRepository.detectedBackendVersion } returns MutableStateFlow(serverVersion)
        every { configRepository.startupConfig } returns
            MutableStateFlow(StartupConfig(codeEnvironmentDecisionVersion = 1))
        every { configRepository.endpointConfigs } returns MutableStateFlow(
            mapOf(
                ENDPOINT to EndpointConfig(
                    capabilities = listOf("execute_code", "stateful_code_sessions"),
                    statefulCodeSessions = StatefulCodeSessionsConfig(
                        environments = listOf(machine),
                        approvalsEnabled = true,
                        approvalModes = listOf(CodeApprovalMode.ASK),
                    ),
                ),
            ),
        )
        coEvery { fixture.agentRepository.getAgents(any()) } returns Result.Success(
            listOf(
                Agent(
                    id = AGENT_ID,
                    name = "Dev",
                    tools = listOf("execute_code"),
                    statefulCodeSessions = true,
                    codeEnvironmentId = machine.id,
                    codeWorkspaceId = "repo",
                ),
            ),
        )
        val selection = CodeWorkspaceSelection(machine.id, "repo")
        fixture.codeEnvironmentRepository = object : CodeEnvironmentRepository {
            override suspend fun status(environmentId: String): Result<CodeEnvironmentStatus> =
                Result.Success(CodeEnvironmentStatus(machine.id, "ready", listOf(CodeWorkspaceDescriptor(id = "repo"))))
            override suspend fun savedDecision(conversationId: String): Result<SavedCodeDecision> =
                Result.Success(SavedCodeDecision(mode = CodeEnvironmentMode.ATTACHED, workspaces = listOf(selection)))
            override suspend fun moveDecision(
                conversationId: String,
                from: List<CodeWorkspaceSelection>,
                to: List<CodeWorkspaceSelection>,
            ): CodeDecisionMove = CodeDecisionMove.Failed(unknownOutcome = false)
        }
    }

    @Test
    fun `a queued Ask turn reaches the server queue carrying its mode`() = queuedTurnTest(
        arrange = { arrangeCodeChat("0.8.8", BackendBuildClass.OFFICIAL) },
    ) { vm ->
        vm.onInputChanged(TEXT)
        vm.queueMessage()
        runCurrent()

        val request = slot<EnqueueQueuedTurnRequest>()
        coVerify(exactly = 1) { queuedTurnRepository.enqueue(capture(request)) }
        assertThat(request.captured.codeApprovalMode).isEqualTo(CodeApprovalMode.ASK)
    }

    @Test
    fun `a turn with a mode stays local on a server without the v2 queue`() = queuedTurnTest(
        arrange = { arrangeCodeChat("0.8.8-rc3", BackendBuildClass.RC) },
    ) { vm ->
        vm.onInputChanged(TEXT)
        vm.queueMessage()
        runCurrent()

        coVerify(exactly = 0) { queuedTurnRepository.enqueue(any()) }
        val queued = vm.uiState.value.messageQueue.single()
        assertThat(queued.server).isNull()
        assertThat(queued.codeWorkspace?.approvalMode).isEqualTo(CodeApprovalMode.ASK)
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
                        files = listOf(QueuedTurnFileRef(fileId = "file-1", filename = "chart.png", type = "image/png")),
                    ),
                )
            },
        ) { vm ->
            runCurrent()

            val recovered = vm.uiState.value.messageQueue.single()
            assertThat(recovered.text).isEqualTo("left over from last time")
            assertThat(recovered.clientRequestId).isEqualTo("req-orphan")
            assertThat(recovered.server?.id).isEqualTo("qt-orphan")
            // An edit loads the row into the composer; without its files it is resent without them.
            assertThat(recovered.attachments.map { it.fileId }).containsExactly("file-1")
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
        // Still queued while it is edited; only the chips hide it.
        assertThat(vm.uiState.value.messageQueue.single().localId).isEqualTo(rejected.localId)
        assertThat(vm.uiState.value.displayedMessageQueue).isEmpty()

        vm.onInputChanged("edited follow-up")
        vm.commitQueuedEdit()
        runCurrent()

        // Offered back under a fresh idempotency key, since the run is still live; this stub's
        // server refuses it again.
        val requests = mutableListOf<EnqueueQueuedTurnRequest>()
        coVerify(exactly = 2) { queuedTurnRepository.enqueue(capture(requests)) }
        assertThat(requests.map { it.text }).containsExactly(TEXT, "edited follow-up").inOrder()
        assertThat(requests.map { it.clientRequestId }.toSet()).hasSize(2)
        coVerify(exactly = 0) { queuedTurnRepository.cancel(any()) }
        assertThat(vm.uiState.value.messageQueue.single().text).isEqualTo("edited follow-up")
    }

    /**
     * In a run this client started, the branch tail is the optimistic user message. The server
     * mints its own id for that message, so a queued turn anchored to the optimistic one is
     * dead-lettered `PARENT_NOT_FOUND` at the run end and never sent. The anchor is the id from
     * the server's `created` frame, and until that frame arrives the follow-up stays local.
     */
    @Test
    fun `a follow-up to a run this client started anchors to the server's user message`() = queuedTurnTest { vm ->
        resumedStream.emit(StreamEvent.Final())
        runCurrent()
        assertThat(vm.uiState.value.isStreaming).isFalse()
        val sendStream = MutableSharedFlow<StreamEvent>(extraBufferCapacity = 8)
        every {
            chatRepository.startChat(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(),
            )
        } returns sendStream
        vm.onInputChanged("a new turn")
        vm.sendMessage()
        runCurrent()
        assertThat(vm.uiState.value.isStreaming).isTrue()
        val optimisticId = vm.uiState.value.displayMessages.last().message.messageId
        // The start POST's own frame: an epoch, but no message.
        sendStream.emit(StreamEvent.Created(CONVERSATION_ID, messageId = "", parentMessageId = "", generationCreatedAt = EPOCH + 1))
        runCurrent()

        vm.onInputChanged("too early")
        vm.queueMessage()
        runCurrent()
        coVerify(exactly = 0) { queuedTurnRepository.enqueue(any()) }
        assertThat(vm.uiState.value.messageQueue.single().server).isNull()

        sendStream.emit(StreamEvent.Created(CONVERSATION_ID, messageId = "server-user-1", parentMessageId = "assistant-1"))
        runCurrent()
        vm.onInputChanged(TEXT)
        vm.queueMessage()
        runCurrent()

        val request = slot<EnqueueQueuedTurnRequest>()
        coVerify(exactly = 1) { queuedTurnRepository.enqueue(capture(request)) }
        assertThat(request.captured.parentMessageId).isEqualTo("server-user-1")
        assertThat(request.captured.parentMessageId).isNotEqualTo(optimisticId)
    }

    /** A server without queued turns: every follow-up stays in the local queue. */
    private fun localQueueOnly() {
        coEvery { queuedTurnRepository.enqueue(any()) } returns QueuedTurnOutcome.Unsupported
    }

    private fun TestScope.reopen(
        activeAccountProvider: ActiveAccountProvider =
            InMemoryActiveAccountProvider(AccountState.Resolved(AccountId("srv:user-1"))),
    ): ChatViewModel {
        // This fixture's conversation read fails, so the chat's agent comes from the handoff the
        // first ViewModel consumed.
        selectionHandoff.put(conversationId = CONVERSATION_ID, choice = ModelChoice.Raw(ENDPOINT, AGENT_ID))
        val reopened = fixture.build(
            defaultDispatcher = testDispatcher,
            initialConversationId = CONVERSATION_ID,
            activeAccountProvider = activeAccountProvider,
        )
        runCurrent()
        return reopened
    }

    /**
     * Leaving a chat replaces its entry and clears its ViewModel. A follow-up the server does not
     * hold comes back on return, and the run it was waiting on sends it when it ends.
     */
    @Test
    fun `a locally queued follow-up survives leaving the chat mid-run`() = queuedTurnTest(
        arrange = ::localQueueOnly,
    ) { vm ->
        vm.onInputChanged("local follow-up")
        vm.queueMessage()
        runCurrent()
        assertThat(vm.uiState.value.messageQueue.single().server).isNull()

        vm.viewModelScope.cancel()
        val reopened = reopen()
        try {
            val sent = captureSentTexts()
            assertThat(reopened.uiState.value.messageQueue.map { it.text }).containsExactly("local follow-up")
            // The run is still going, so its end drains the queue as it would have.
            assertThat(reopened.uiState.value.isQueuePaused).isFalse()
            assertThat(reopened.uiState.value.isStreaming).isTrue()
            assertThat(sent).isEmpty()

            resumedStream.emit(StreamEvent.Final())
            // The run-end drain waits for the reply to settle into the tree; this fixture's tree
            // never changes, so it goes on the settle timeout.
            advanceTimeBy(9_000)
            runCurrent()

            assertThat(reopened.uiState.value.messageQueue).isEmpty()
            assertThat(sent).containsExactly("local follow-up")
        } finally {
            reopened.viewModelScope.cancel()
        }
    }

    /**
     * Back after the run ended (or was stopped): nothing says the queue may go, so it waits behind
     * "Send queued" rather than sending on its own or vanishing.
     */
    @Test
    fun `a restored queue with no run to wait on is held for the user`() = queuedTurnTest(
        arrange = ::localQueueOnly,
    ) { vm ->
        vm.onInputChanged("local follow-up")
        vm.queueMessage()
        runCurrent()
        vm.viewModelScope.cancel()
        coEvery { chatRepository.checkStreamStatus(eq(CONVERSATION_ID), any()) } returns
            ChatStatusResponse(active = false)

        val reopened = reopen()
        try {
            val sent = captureSentTexts()
            assertThat(reopened.uiState.value.messageQueue.map { it.text }).containsExactly("local follow-up")
            assertThat(reopened.uiState.value.pausedQueueCount).isEqualTo(1)
            assertThat(sent).isEmpty()
        } finally {
            reopened.viewModelScope.cancel()
        }
    }

    @Test
    fun `a restored queue never reaches another account`() = queuedTurnTest(
        arrange = ::localQueueOnly,
    ) { vm ->
        vm.onInputChanged("local follow-up")
        vm.queueMessage()
        runCurrent()
        vm.viewModelScope.cancel()

        val other = reopen(InMemoryActiveAccountProvider(AccountState.Resolved(AccountId("srv:user-2"))))
        try {
            assertThat(other.uiState.value.messageQueue).isEmpty()
        } finally {
            other.viewModelScope.cancel()
        }
    }

    /** The text is also kept in the draft store, the one copy that outlives the process. */
    @Test
    fun `a local queue's text is kept in the draft store`() = queuedTurnTest(
        arrange = ::localQueueOnly,
    ) { vm ->
        vm.onInputChanged("local follow-up")
        vm.queueMessage()
        runCurrent()

        coVerify { fixture.draftRepository.saveDraft("queued:$CONVERSATION_ID", "local follow-up") }
    }

    /** After process death there is no stash; the kept text goes back into the composer. */
    @Test
    fun `queued text that outlived the process is put back in the composer`() = queuedTurnTest(
        arrange = {
            localQueueOnly()
            // The relaxed config mock fails these with an empty message, which lands on the same
            // error channel and would mask the notice under test.
            coEvery { configRepository.fetchEndpoints() } returns Result.Success(mapOf(ENDPOINT to EndpointConfig()))
            coEvery { configRepository.fetchModels() } returns Result.Success(mapOf(ENDPOINT to listOf(AGENT_ID)))
            coEvery { fixture.draftRepository.awaitDraft(CONVERSATION_ID) } returns "half-typed"
            coEvery { fixture.draftRepository.awaitDraft("queued:$CONVERSATION_ID") } returns "lost follow-up"
        },
    ) { vm ->
        assertThat(vm.uiState.value.inputText).isEqualTo("half-typed\n\nlost follow-up")
        assertThat(vm.uiState.value.error).isEqualTo(ChatNotice.QUEUE_RESTORED_TO_COMPOSER.marker())
        coVerify { fixture.draftRepository.deleteDraft("queued:$CONVERSATION_ID") }
        coVerify { fixture.draftRepository.saveDraft(CONVERSATION_ID, "half-typed\n\nlost follow-up") }
    }

    /** Opening the edit touches nothing on the server: the row stays queued there and here. */
    @Test
    fun `editing a server-owned row leaves it queued`() = queuedTurnTest { vm ->
        vm.onInputChanged(TEXT)
        vm.queueMessage()
        runCurrent()
        val queued = vm.uiState.value.messageQueue.single()
        vm.onInputChanged("pre-edit draft")

        vm.editQueued(queued.localId)
        runCurrent()

        assertThat(vm.uiState.value.isEditingQueued).isTrue()
        assertThat(vm.uiState.value.inputText).isEqualTo(TEXT)
        coVerify(exactly = 0) { queuedTurnRepository.cancel(any()) }
        assertThat(serverRows.map { it.text }).containsExactly(TEXT)
        assertThat(vm.uiState.value.messageQueue.map { it.localId }).containsExactly(queued.localId)
    }

    /**
     * Leaving the chat mid-edit clears this ViewModel (a chat opened from the drawer replaces the
     * entry). The queued row must still be there when the chat is opened again, unchanged, and the
     * unsaved edit is dropped as a cancel would drop it.
     */
    @Test
    fun `a row being edited survives leaving the chat`() = queuedTurnTest { vm ->
        vm.onInputChanged(TEXT)
        vm.queueMessage()
        runCurrent()
        val queued = vm.uiState.value.messageQueue.single()
        // A consistent server: a withdrawal really removes the row.
        coEvery { queuedTurnRepository.cancel(any()) } answers {
            serverRows.clear()
            QueuedTurnOutcome.Committed(
                AgentQueuedTurnReceipt(
                    queuedTurnId = firstArg(),
                    clientRequestId = queued.clientRequestId!!,
                    text = queued.text,
                    status = QueuedTurnStatus.CANCELLED,
                ),
            )
        }
        vm.editQueued(queued.localId)
        runCurrent()
        vm.onInputChanged("half-typed edit")

        vm.viewModelScope.cancel()
        val reopened = fixture.build(defaultDispatcher = testDispatcher, initialConversationId = CONVERSATION_ID)
        try {
            runCurrent()

            coVerify(exactly = 0) { queuedTurnRepository.cancel(any()) }
            assertThat(reopened.uiState.value.isEditingQueued).isFalse()
            assertThat(reopened.uiState.value.messageQueue.map { it.text }).containsExactly(TEXT)
        } finally {
            reopened.viewModelScope.cancel()
        }
    }

    @Test
    fun `cancelling an edit leaves the server row untouched`() = queuedTurnTest { vm ->
        vm.onInputChanged(TEXT)
        vm.queueMessage()
        runCurrent()
        val queued = vm.uiState.value.messageQueue.single()
        vm.onInputChanged("pre-edit draft")
        vm.editQueued(queued.localId)
        runCurrent()
        vm.onInputChanged("discarded edit")

        vm.cancelQueuedEdit()
        runCurrent()

        coVerify(exactly = 0) { queuedTurnRepository.cancel(any()) }
        coVerify(exactly = 1) { queuedTurnRepository.enqueue(any()) }
        assertThat(vm.uiState.value.messageQueue.single()).isEqualTo(queued)
        assertThat(vm.uiState.value.inputText).isEqualTo("pre-edit draft")
    }

    /** An Update that changes nothing the server holds keeps its copy and its place. */
    @Test
    fun `an unchanged edit commits without touching the server`() = queuedTurnTest { vm ->
        vm.onInputChanged(TEXT)
        vm.queueMessage()
        runCurrent()
        val queued = vm.uiState.value.messageQueue.single()
        vm.editQueued(queued.localId)
        runCurrent()

        vm.commitQueuedEdit()
        runCurrent()

        coVerify(exactly = 0) { queuedTurnRepository.cancel(any()) }
        coVerify(exactly = 1) { queuedTurnRepository.enqueue(any()) }
        assertThat(vm.uiState.value.isEditingQueued).isFalse()
        assertThat(vm.uiState.value.messageQueue.single().clientRequestId).isEqualTo(queued.clientRequestId)
    }

    /**
     * The server holds the row's excerpts, so an edit that only swaps them withdraws the row and
     * claims again with the ones on screen; otherwise the server would run the old ones.
     */
    @Test
    fun `a quote-only edit of a server-owned row claims again with the new quotes`() = queuedTurnTest { vm ->
        vm.addPendingQuote("old excerpt")
        vm.onInputChanged(TEXT)
        vm.queueMessage()
        runCurrent()
        val queued = vm.uiState.value.messageQueue.single()
        assertThat(queued.server).isNotNull()
        assertThat(queued.quotes).containsExactly("old excerpt")
        coEvery { queuedTurnRepository.cancel(queued.server!!.id!!) } answers {
            serverRows.clear()
            QueuedTurnOutcome.Committed(
                AgentQueuedTurnReceipt(
                    queuedTurnId = queued.server!!.id!!,
                    clientRequestId = queued.clientRequestId!!,
                    text = queued.text,
                    status = QueuedTurnStatus.CANCELLED,
                ),
            )
        }
        vm.editQueued(queued.localId)
        runCurrent()
        assertThat(vm.uiState.value.pendingQuotes).containsExactly("old excerpt")

        vm.removeDraftItem(DraftItem.Quote(0, vm.uiState.value.pendingQuotes[0]))
        vm.addPendingQuote("new excerpt")
        vm.commitQueuedEdit()
        runCurrent()

        coVerify(exactly = 1) { queuedTurnRepository.cancel(any()) }
        val requests = mutableListOf<EnqueueQueuedTurnRequest>()
        coVerify(exactly = 2) { queuedTurnRepository.enqueue(capture(requests)) }
        assertThat(requests[0].quotes).containsExactly("old excerpt")
        assertThat(requests[1].text).isEqualTo(TEXT)
        assertThat(requests[1].quotes).containsExactly("new excerpt")
        assertThat(vm.uiState.value.isEditingQueued).isFalse()
        assertThat(vm.uiState.value.messageQueue.single().quotes).containsExactly("new excerpt")
    }

    @Test
    fun `an edit that keeps a server-owned row's quotes touches nothing`() = queuedTurnTest { vm ->
        vm.addPendingQuote("kept excerpt")
        vm.onInputChanged(TEXT)
        vm.queueMessage()
        runCurrent()
        val queued = vm.uiState.value.messageQueue.single()
        vm.editQueued(queued.localId)
        runCurrent()

        vm.commitQueuedEdit()
        runCurrent()

        coVerify(exactly = 0) { queuedTurnRepository.cancel(any()) }
        coVerify(exactly = 1) { queuedTurnRepository.enqueue(any()) }
        assertThat(vm.uiState.value.isEditingQueued).isFalse()
        assertThat(vm.uiState.value.messageQueue.single().clientRequestId).isEqualTo(queued.clientRequestId)
        assertThat(vm.uiState.value.messageQueue.single().quotes).containsExactly("kept excerpt")
    }

    @Test
    fun `an edit emptied to nothing withdraws the row`() = queuedTurnTest { vm ->
        vm.onInputChanged(TEXT)
        vm.queueMessage()
        runCurrent()
        val queued = vm.uiState.value.messageQueue.single()
        coEvery { queuedTurnRepository.cancel(queued.server!!.id!!) } answers {
            serverRows.clear()
            QueuedTurnOutcome.Committed(
                AgentQueuedTurnReceipt(
                    queuedTurnId = queued.server!!.id!!,
                    clientRequestId = queued.clientRequestId!!,
                    text = queued.text,
                    status = QueuedTurnStatus.CANCELLED,
                ),
            )
        }
        vm.editQueued(queued.localId)
        runCurrent()
        vm.onInputChanged("")

        vm.commitQueuedEdit()
        runCurrent()

        coVerify(exactly = 1) { queuedTurnRepository.cancel(any()) }
        assertThat(vm.uiState.value.messageQueue).isEmpty()
        assertThat(vm.uiState.value.isEditingQueued).isFalse()
    }

    /**
     * The row stays on the server during the edit, so the server can admit it at the run end
     * before the edit is committed. The edit must then not be offered back: the words would run
     * twice.
     */
    @Test
    fun `an edit of a row the server already ran is not sent again`() = queuedTurnTest { vm ->
        vm.onInputChanged(TEXT)
        vm.queueMessage()
        runCurrent()
        val queued = vm.uiState.value.messageQueue.single()
        vm.onInputChanged("pre-edit draft")
        vm.editQueued(queued.localId)
        runCurrent()
        vm.onInputChanged("edited after it ran")

        // Admitted: the server's list no longer has it, and the next poll retires the row. The
        // edit ends there, so the editing bar does not stay up over a message that already went.
        serverRows.clear()
        advanceTimeBy(3_000)
        runCurrent()
        assertThat(vm.uiState.value.messageQueue).isEmpty()
        assertThat(vm.uiState.value.isEditingQueued).isFalse()
        assertThat(vm.uiState.value.inputText).isEqualTo("pre-edit draft")

        vm.commitQueuedEdit()
        runCurrent()

        coVerify(exactly = 1) { queuedTurnRepository.enqueue(any()) }
        coVerify(exactly = 0) { queuedTurnRepository.cancel(any()) }
        assertThat(vm.uiState.value.isEditingQueued).isFalse()
        assertThat(vm.uiState.value.messageQueue).isEmpty()
        assertThat(vm.uiState.value.inputText).isEqualTo("pre-edit draft")
        assertThat(vm.uiState.value.error).isEqualTo(ChatNotice.QUEUE_EDIT_OVERTAKEN.marker())
    }

    /**
     * The same race, closed between two polls: the server ran the original and let it go, so the
     * withdrawal is answered "not found". That answer counts as withdrawn for the ×, but for an
     * edit it means the words already went.
     */
    @Test
    fun `an edit whose original the server no longer has is not sent again`() = queuedTurnTest { vm ->
        vm.onInputChanged(TEXT)
        vm.queueMessage()
        runCurrent()
        val queued = vm.uiState.value.messageQueue.single()
        coEvery { queuedTurnRepository.cancel(any()) } answers {
            serverRows.clear()
            QueuedTurnOutcome.Rejected(statusCode = 404, code = "QUEUED_TURN_NOT_FOUND", message = "gone")
        }
        vm.editQueued(queued.localId)
        runCurrent()
        vm.onInputChanged("edited after it ran")

        vm.commitQueuedEdit()
        runCurrent()

        coVerify(exactly = 1) { queuedTurnRepository.cancel(any()) }
        coVerify(exactly = 1) { queuedTurnRepository.enqueue(any()) }
        assertThat(vm.uiState.value.isEditingQueued).isFalse()
        assertThat(vm.uiState.value.messageQueue).isEmpty()
        assertThat(vm.uiState.value.error).isEqualTo(ChatNotice.QUEUE_EDIT_OVERTAKEN.marker())
    }

    /**
     * Left local, an edited row is overtaken: the follow-up queued after it is server-owned, the
     * server admits it at the run end, and the drain refuses the edited one until that has run.
     */
    @Test
    fun `an edited row goes back to the server ahead of a later follow-up`() = queuedTurnTest { vm ->
        vm.onInputChanged("first")
        vm.queueMessage()
        runCurrent()
        val first = vm.uiState.value.messageQueue.single()
        coEvery { queuedTurnRepository.cancel(first.server!!.id!!) } answers {
            serverRows.removeAll { it.clientRequestId == first.clientRequestId }
            QueuedTurnOutcome.Committed(
                AgentQueuedTurnReceipt(
                    queuedTurnId = first.server!!.id!!,
                    clientRequestId = first.clientRequestId!!,
                    text = first.text,
                    status = QueuedTurnStatus.CANCELLED,
                ),
            )
        }

        vm.editQueued(first.localId)
        runCurrent()
        vm.onInputChanged("first, fixed")
        vm.commitQueuedEdit()
        runCurrent()
        vm.onInputChanged("second")
        vm.queueMessage()
        runCurrent()

        val requests = mutableListOf<EnqueueQueuedTurnRequest>()
        coVerify(exactly = 3) { queuedTurnRepository.enqueue(capture(requests)) }
        assertThat(requests.map { it.text }).containsExactly("first", "first, fixed", "second").inOrder()
        assertThat(vm.uiState.value.messageQueue.map { it.server != null }).containsExactly(true, true)
    }

    /**
     * A withdrawn row is rebuilt locally and claimed again, and the second claim's picks are the
     * chips on screen during the edit, not the draft's staged ones and not the first claim's.
     */
    @Test
    fun `a withdrawn queued edit claims again with the chips picked during it`() = queuedTurnTest(
        arrange = {
            every { configRepository.endpointConfigs } returns MutableStateFlow(
                mapOf(ENDPOINT to EndpointConfig(capabilities = listOf(ToolConstants.SKILLS))),
            )
        },
    ) { vm ->
        vm.addPendingManualSkill("pdf-tools")
        vm.onInputChanged(TEXT)
        vm.queueMessage()
        runCurrent()
        val queued = vm.uiState.value.messageQueue.single()
        assertThat(queued.server).isNotNull()
        coEvery { queuedTurnRepository.cancel(queued.server!!.id!!) } answers {
            serverRows.removeAll { it.clientRequestId == queued.clientRequestId }
            QueuedTurnOutcome.Committed(
                AgentQueuedTurnReceipt(
                    queuedTurnId = queued.server!!.id!!,
                    clientRequestId = queued.clientRequestId!!,
                    text = queued.text,
                    status = QueuedTurnStatus.CANCELLED,
                ),
            )
        }
        // A chip staged for the next new message, before the edit starts.
        vm.addPendingManualSkill("draft-skill")

        vm.editQueued(queued.localId)
        runCurrent()
        // Withdrawn only when the edit is committed.
        coVerify(exactly = 0) { queuedTurnRepository.cancel(any()) }
        assertThat(vm.uiState.value.isEditingQueued).isTrue()
        assertThat(vm.uiState.value.pendingManualSkills).containsExactly("pdf-tools")

        vm.addPendingManualSkill("translator")
        vm.removeDraftItem(DraftItem.Skill("pdf-tools"))
        vm.commitQueuedEdit()
        runCurrent()
        coVerify(exactly = 1) { queuedTurnRepository.cancel(any()) }

        val requests = mutableListOf<EnqueueQueuedTurnRequest>()
        coVerify(exactly = 2) { queuedTurnRepository.enqueue(capture(requests)) }
        assertThat(requests[0].manualSkills).containsExactly("pdf-tools")
        assertThat(requests[1].manualSkills).containsExactly("translator")
        assertThat(requests[1].clientRequestId).isNotEqualTo(requests[0].clientRequestId)
        assertThat(vm.uiState.value.messageQueue.single().manualSkills).containsExactly("translator")
        // The draft gets its own chip back, and nothing picked for the edit leaks onto it.
        assertThat(vm.uiState.value.pendingManualSkills).containsExactly("draft-skill")
    }

    /**
     * Committing an edit of a server-owned row withdraws the original first, and that DELETE is a
     * round trip with the session still open. A second Update, a Cancel, or a tap on another row in
     * that window must neither start a second DELETE nor end the session the withdrawal finishes.
     */
    @Test
    fun `taps during an edit's withdrawal cannot strand a row`() = queuedTurnTest { vm ->
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
        vm.onInputChanged("first, fixed")
        vm.commitQueuedEdit()
        runCurrent()
        vm.commitQueuedEdit()
        vm.cancelQueuedEdit()
        vm.editQueued(second.localId)
        runCurrent()
        assertThat(vm.uiState.value.editingQueuedItem?.original?.localId).isEqualTo(first.localId)
        cancelGate.complete(Unit)
        runCurrent()

        coVerify(exactly = 1) { queuedTurnRepository.cancel(any()) }
        assertThat(vm.uiState.value.editingQueuedItem).isNull()
        assertThat(vm.uiState.value.messageQueue.map { it.text })
            .containsExactly("first, fixed", "second follow-up")
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
        assertThat(vm.uiState.value.error).isEqualTo(ChatNotice.QUEUE_WITHDRAW_BUSY.marker())
        assertThat(vm.uiState.value.editingQueuedItem).isNull()

        cancelGate.complete(Unit)
        runCurrent()

        // The one withdrawal that was allowed still completed, and the fence let go after it.
        assertThat(vm.uiState.value.messageQueue.map { it.text }).containsExactly("second follow-up")
    }

    /**
     * The same window, entered from a LEGACY row while the × withdraws a server row. A legacy edit
     * opens its session synchronously, and the withdrawal landing under it would shift the queue
     * the session points into, so the tap is refused until the withdrawal is done.
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

        vm.cancelQueued(first.localId)
        runCurrent()
        vm.editQueued(second.localId)
        runCurrent()
        assertThat(vm.uiState.value.editingQueuedItem).isNull()
        cancelGate.complete(Unit)
        runCurrent()

        assertThat(vm.uiState.value.messageQueue.map { it.text }).containsExactly("second follow-up")
    }

    /**
     * The run end refused to drain because a server row was in the queue. Withdrawing that row is
     * the only thing left that can release the local row behind it, and "Send queued" is not on
     * screen to do it by hand: it renders only for a paused queue.
     */
    @Test
    fun `withdrawing the last server row sends the local row behind it`() = queuedTurnTest(
        arrange = {
            coEvery { queuedTurnRepository.enqueue(match { it.text == "refused" }) } returns
                QueuedTurnOutcome.Rejected(statusCode = 429, code = "QUEUED_TURN_QUEUE_FULL", message = "full")
            coEvery { queuedTurnRepository.enqueue(match { it.text == "local" }) } returns
                QueuedTurnOutcome.Unsupported
        },
    ) { vm ->
        val sent = captureSentTexts()
        vm.onInputChanged("refused")
        vm.queueMessage()
        runCurrent()
        vm.onInputChanged("local")
        vm.queueMessage()
        runCurrent()
        assertThat(vm.uiState.value.messageQueue.map { it.server == null }).containsExactly(false, true)

        resumedStream.emit(StreamEvent.Final())
        runCurrent()
        assertThat(sent).isEmpty()

        vm.cancelQueued(vm.uiState.value.messageQueue.first().localId)
        runCurrent()

        assertThat(sent).containsExactly("local")
    }

    /** The same stall reached without a tap: the enqueue's answer lands after the run end. */
    @Test
    fun `a late answer that hands the row back to the local drain sends it`() = queuedTurnTest(
        arrange = {
            coEvery { queuedTurnRepository.enqueue(any()) } coAnswers {
                enqueueGate.await()
                QueuedTurnOutcome.Unsupported
            }
        },
    ) { vm ->
        val sent = captureSentTexts()
        vm.onInputChanged(TEXT)
        vm.queueMessage()
        runCurrent()

        resumedStream.emit(StreamEvent.Final())
        runCurrent()
        assertThat(sent).isEmpty()

        enqueueGate.complete(Unit)
        runCurrent()

        assertThat(sent).containsExactly(TEXT)
    }

    /**
     * A dropped socket says nothing about the run: it carries on server-side and may still inject
     * the steer. Handed to the server queue as well, the same words would be delivered twice with
     * nothing the user could do to stop it.
     */
    @Test
    fun `a steer reclaimed after a dropped socket stays local`() = queuedTurnTest(
        arrange = { stubAcceptedSteer() },
    ) { vm ->
        assertThat(vm.uiState.value.canSteerNow).isTrue()
        vm.onInputChanged(TEXT)
        vm.steerMessage()
        runCurrent()

        resumedStream.emit(StreamEvent.Error("connection reset", isNetworkError = true))
        runCurrent()

        coVerify(exactly = 0) { queuedTurnRepository.enqueue(any()) }
        val reclaimed = vm.uiState.value.messageQueue.single()
        assertThat(reclaimed.text).isEqualTo(TEXT)
        assertThat(reclaimed.server).isNull()
        assertThat(vm.uiState.value.isQueuePaused).isTrue()
    }

    /** The server dead-claims every turn queued behind a stopped run, so it would never run it. */
    @Test
    fun `a steer the stopped run hands back is not given to the server`() = queuedTurnTest(
        arrange = {
            stubAcceptedSteer()
            coEvery { chatRepository.abortChat(CONVERSATION_ID, any(), any()) } answers {
                thirdArg<(List<PendingSteer>) -> Unit>()(listOf(PendingSteer(steerId = "steer-1", text = TEXT)))
                Result.Success(ChatAbortResponse())
            }
        },
    ) { vm ->
        vm.onInputChanged(TEXT)
        vm.steerMessage()
        runCurrent()

        vm.stopGeneration()
        runCurrent()

        coVerify(exactly = 0) { queuedTurnRepository.enqueue(any()) }
        assertThat(vm.uiState.value.messageQueue.single().server).isNull()
    }

    @Test
    fun `a steer the aborted final hands back is not given to the server`() = queuedTurnTest(
        arrange = {
            stubAcceptedSteer()
            coEvery { chatRepository.abortChat(CONVERSATION_ID, any(), any()) } returns
                Result.Success(ChatAbortResponse())
        },
    ) { vm ->
        vm.onInputChanged(TEXT)
        vm.steerMessage()
        runCurrent()
        vm.stopGeneration()
        runCurrent()

        resumedStream.emit(
            StreamEvent.Final(aborted = true, pendingSteers = listOf(PendingSteer(steerId = "steer-1", text = TEXT))),
        )
        runCurrent()

        coVerify(exactly = 0) { queuedTurnRepository.enqueue(any()) }
        assertThat(vm.uiState.value.messageQueue.single().server).isNull()
    }

    /**
     * "Send queued" after a Stop. The follow-up was server-owned, and the server dead-claimed it
     * behind the aborted run; the tap is the review it asks for, so the tap has to send it.
     */
    @Test
    fun `Send queued sends a turn the stopped run dead-claimed`() = queuedTurnTest(
        arrange = {
            coEvery { chatRepository.abortChat(CONVERSATION_ID, any(), any()) } returns
                Result.Success(ChatAbortResponse())
            coEvery { queuedTurnRepository.cancel(any()) } answers {
                val cancelled = serverRows.single().copy(status = QueuedTurnStatus.CANCELLED)
                serverRows.clear()
                QueuedTurnOutcome.Committed(cancelled)
            }
        },
    ) { vm ->
        val sent = captureSentTexts()
        deadClaimAfterStop(vm)

        vm.sendQueuedNow()
        runCurrent()

        assertThat(sent).containsExactly(TEXT)
        // The list route returns dead rows until they are cancelled; one left there comes back
        // as an orphan on the next read, and the next "Send queued" sends it again.
        coVerify(exactly = 1) { queuedTurnRepository.cancel("qt-1") }
        assertThat(serverRows).isEmpty()
    }

    /** A dead row the server would not withdraw stays refused, so it cannot be sent twice. */
    @Test
    fun `Send queued holds a dead-claimed turn the server would not withdraw`() = queuedTurnTest(
        arrange = {
            coEvery { chatRepository.abortChat(CONVERSATION_ID, any(), any()) } returns
                Result.Success(ChatAbortResponse())
            coEvery { queuedTurnRepository.cancel(any()) } returns
                QueuedTurnOutcome.Indeterminate(statusCode = 503, code = null, message = "unavailable")
        },
    ) { vm ->
        val sent = captureSentTexts()
        deadClaimAfterStop(vm)

        vm.sendQueuedNow()
        runCurrent()

        assertThat(sent).isEmpty()
        assertThat(vm.uiState.value.messageQueue.single().server?.status)
            .isEqualTo(QueuedTurnServerState.Status.Rejected)
        assertThat(vm.uiState.value.pausedQueueCount).isEqualTo(1)
    }

    /**
     * An unanswered enqueue whose reconciliation window ran out never gets an id, and refuses
     * every drain. Upstream's × dismisses it; the edit stays refused, since it would resend words
     * the server may already hold.
     */
    @Test
    fun `an unconfirmed delivery can be dismissed but not edited`() = queuedTurnTest(
        arrange = {
            coEvery { queuedTurnRepository.enqueue(any()) } returns
                QueuedTurnOutcome.Indeterminate(statusCode = null, code = null, message = "dropped")
        },
    ) { vm ->
        // The expiry reads the wall clock; tie it to virtual time so the window can run out.
        val start = Clock.System.now()
        mockkObject(Clock.System)
        every { Clock.System.now() } answers {
            start + testScheduler.currentTime.milliseconds
        }
        try {
            vm.onInputChanged(TEXT)
            vm.queueMessage()
            runCurrent()
            advanceTimeBy(QUEUED_TURN_RECONCILIATION_MS + 3_000)
            runCurrent()
            val row = vm.uiState.value.messageQueue.single()
            assertThat(row.server?.reconciliationExpired).isTrue()

            vm.editQueued(row.localId)
            runCurrent()
            assertThat(vm.uiState.value.messageQueue).hasSize(1)
            assertThat(vm.uiState.value.isEditingQueued).isFalse()
            assertThat(vm.uiState.value.error).isEqualTo(ChatNotice.QUEUE_WITHDRAW_REFUSED.marker())

            vm.cancelQueued(row.localId)
            runCurrent()
            assertThat(vm.uiState.value.messageQueue).isEmpty()
        } finally {
            unmockkObject(Clock.System)
        }
    }

    /** Tapped before the dead claim is seen, the control must stay on screen for a second try. */
    @Test
    fun `Send queued keeps its control while the server still holds the row`() = queuedTurnTest(
        arrange = {
            coEvery { chatRepository.abortChat(CONVERSATION_ID, any(), any()) } returns
                Result.Success(ChatAbortResponse())
        },
    ) { vm ->
        val sent = captureSentTexts()
        vm.onInputChanged(TEXT)
        vm.queueMessage()
        runCurrent()
        vm.stopGeneration()
        runCurrent()
        resumedStream.emit(StreamEvent.Final(aborted = true))
        runCurrent()

        vm.sendQueuedNow()
        runCurrent()

        assertThat(sent).isEmpty()
        assertThat(vm.uiState.value.pausedQueueCount).isEqualTo(1)
        assertThat(vm.uiState.value.error).isEqualTo(ChatNotice.QUEUE_SERVER_HOLDS.marker())
    }

    /** Queues a server-owned follow-up, stops the run, and has the server dead-claim the row. */
    private suspend fun TestScope.deadClaimAfterStop(vm: ChatViewModel) {
        vm.onInputChanged(TEXT)
        vm.queueMessage()
        runCurrent()

        vm.stopGeneration()
        runCurrent()
        resumedStream.emit(StreamEvent.Final(aborted = true))
        runCurrent()
        serverRows.replaceAll {
            it.copy(
                status = QueuedTurnStatus.DEAD,
                failure = QueuedTurnFailure(code = "PREDECESSOR_ABORTED", message = "aborted"),
            )
        }
        advanceTimeBy(3_000)
        runCurrent()
        assertThat(vm.uiState.value.messageQueue.single().server?.status)
            .isEqualTo(QueuedTurnServerState.Status.Rejected)
        assertThat(vm.uiState.value.pausedQueueCount).isEqualTo(1)
    }

    private fun stubAcceptedSteer() {
        coEvery { chatRepository.steerChat(any()) } returns
            Result.Success(SteerResponse(status = "queued", steerId = "steer-1"))
    }

    /** Records the text of every turn the ViewModel sends. */
    private fun captureSentTexts(): MutableList<String> {
        val sent = mutableListOf<String>()
        every {
            chatRepository.startChat(
                capture(sent), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(),
            )
        } returns MutableSharedFlow()
        return sent
    }

    /**
     * The whole point of the attach: the run this client was on FINISHES normally, then the server
     * starts the queued turn. The delegate is not fresh here — its session has ended with the
     * previous turn's Final — which is the state every real admission arrives in. The attach used
     * to read that ended session as "stale" after its status check and drop itself, so the queued
     * turn ran to completion server-side against an idle screen.
     */
    @Test
    fun `a turn the server admits after the previous run finished is attached and shown`() =
        queuedTurnTest { vm ->
            vm.onInputChanged(TEXT)
            vm.queueMessage()
            runCurrent()
            assertThat(vm.uiState.value.messageQueue.single().server).isNotNull()

            // The previous run finishes normally.
            resumedStream.emit(StreamEvent.Final())
            runCurrent()
            assertThat(vm.uiState.value.isStreaming).isFalse()

            // The server starts the queued turn: a new generation, now live.
            coEvery { chatRepository.checkStreamStatus(eq(CONVERSATION_ID), any()) } returns
                ChatStatusResponse(active = true, createdAt = EPOCH + 1)
            serverRows[0] = serverRows[0].copy(status = QueuedTurnStatus.ADMITTED)
            advanceTimeBy(3_000)
            runCurrent()

            // Attached: a second resume, and the screen is streaming the queued turn.
            coVerify(exactly = 2) { chatRepository.resumeStream(CONVERSATION_ID) }
            assertThat(vm.uiState.value.isStreaming).isTrue()

            // Its reply reaches the displayed messages.
            val request = Message(
                messageId = "user-queued",
                conversationId = CONVERSATION_ID,
                parentMessageId = USER_MESSAGE_ID,
                text = TEXT,
                isCreatedByUser = true,
            )
            val reply = Message(
                messageId = "assistant-queued",
                conversationId = CONVERSATION_ID,
                parentMessageId = "user-queued",
                text = "Here is the summary.",
                isCreatedByUser = false,
            )
            resumedStream.emit(StreamEvent.ContentDelta(chunk = "Here is "))
            resumedStream.emit(StreamEvent.Final(requestMessage = request, responseMessage = reply))
            runCurrent()

            assertThat(vm.uiState.value.displayMessages.map { it.message.messageId })
                .contains("assistant-queued")
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
        quotes = request.quotes,
        manualSkills = request.manualSkills,
    )

    private fun queuedTurnTest(
        choice: ModelChoice = ModelChoice.Raw(ENDPOINT, AGENT_ID),
        arrange: () -> Unit = {},
        body: suspend TestScope.(ChatViewModel) -> Unit,
    ) = runTest(testDispatcher) {
        arrange()
        // Seeds the running turn's user message into the tree. It is the anchor the enqueue has
        // to capture, so without it there is nothing for the assertions to be about.
        selectionHandoff.put(
            conversationId = CONVERSATION_ID,
            choice = choice,
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
        val HELPER_SPEC_CHOICE = ModelChoice.restore(ENDPOINT, AGENT_ID, "helper")
    }
}
