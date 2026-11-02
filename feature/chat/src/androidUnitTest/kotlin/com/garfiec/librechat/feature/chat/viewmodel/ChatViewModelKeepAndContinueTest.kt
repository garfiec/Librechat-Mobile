package com.garfiec.librechat.feature.chat.viewmodel

import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.LastUsedSelection
import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.PendingAction
import com.garfiec.librechat.core.model.PendingActionPayload
import com.garfiec.librechat.core.model.PendingActionTypes
import com.garfiec.librechat.core.model.Preset
import com.garfiec.librechat.core.model.StreamEvent
import com.garfiec.librechat.core.model.config.ModelSpec
import com.garfiec.librechat.core.model.config.ModelSpecs
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.model.content.AgentToolCall
import com.garfiec.librechat.core.model.content.MessageContentPart
import com.garfiec.librechat.core.model.request.AddedConversation
import com.garfiec.librechat.core.model.request.NO_PARENT
import com.garfiec.librechat.core.model.response.ChatStatusResponse
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

/**
 * Compare's Keep / Keep and continue and the Stopped chip, run through the whole [ChatViewModel]
 * with real delegates and fake repositories.
 *
 * The delegate tests fake the reload, the send-start and the request builder, so a fault in how
 * the ViewModel wires them together passes them. Here the reload is the real conversation load,
 * the Continue is the real `continueGeneration`, and every request is read off [ChatRepository.startChat].
 * Driven with `runCurrent()` and a scope cancel in a `finally`, for the reason
 * [ChatViewModelDuringRunSendTest] gives: a live stream never lets `runTest` go idle.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelKeepAndContinueTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val fixture = ChatViewModelTestFixture()
    private val chatRepository get() = fixture.chatRepository
    private val messageRepository get() = fixture.messageRepository
    private val configRepository get() = fixture.configRepository
    private val fileHandler = mockk<PlatformFileHandler>(relaxed = true)

    /** What Room holds for the conversation; the load observes it. */
    private val room = MutableStateFlow<List<Message>>(emptyList())

    /** The server's copy, which a fetch writes into [room], as the repository's read-through does. */
    private var server: List<Message> = emptyList()

    /** Every `startChat`, in order. */
    private val sends = mutableListOf<Send>()

    /** The streams handed to successive `startChat` calls; a fresh, silent one once these run out. */
    private val streams = ArrayDeque<MutableSharedFlow<StreamEvent>>()

    private val connected = MutableStateFlow(true)
    private val startupConfig = MutableStateFlow<StartupConfig?>(null)
    private val endpointConfigs = MutableStateFlow(
        mapOf(ENDPOINT to EndpointConfig(), SECOND_ENDPOINT to EndpointConfig()),
    )

    private data class Send(
        val endpoint: String,
        val model: String?,
        val userMessageId: String?,
        val responseMessageId: String?,
        val isContinued: Boolean,
        val addedConvo: AddedConversation?,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fixture.stubDefaults()
        every { configRepository.startupConfig } returns startupConfig
        // The selection's copy of the spec config comes through the seeder's combine, which waits
        // on every input.
        every { fixture.settingsDataStore.lastUsedSelection } returns MutableStateFlow(LastUsedSelection.NONE)
        every { configRepository.endpointConfigs } returns endpointConfigs
        every { configRepository.availableModels } returns MutableStateFlow(
            mapOf(ENDPOINT to listOf(MODEL), SECOND_ENDPOINT to listOf(SECOND_MODEL)),
        )
        every { fixture.platformDelegateFactory.createFileHandler(any()) } returns fileHandler
        every { fileHandler.attachedFiles } returns MutableStateFlow(emptyList())
        every { fixture.connectivityObserver.isConnected } returns connected
        coEvery { fixture.conversationRepository.getConversation(any(), any()) } returns Result.Error(message = "test")

        every { messageRepository.observeMessages(CONVERSATION_ID) } returns room
        coEvery { messageRepository.getMessages(CONVERSATION_ID) } coAnswers {
            room.value = server
            Result.Success(server)
        }
        coEvery { chatRepository.checkStreamStatus(CONVERSATION_ID, any()) } returns ChatStatusResponse(active = false)

        every {
            chatRepository.startChat(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(),
            )
        } answers {
            sends += Send(
                endpoint = args[2] as String,
                model = args[6] as String?,
                userMessageId = args[7] as String?,
                responseMessageId = args[11] as String?,
                isContinued = args[15] as Boolean,
                addedConvo = args[18] as AddedConversation?,
            )
            streams.removeFirstOrNull() ?: MutableSharedFlow()
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Keep / Keep and continue in a temporary chat ────────────────────

    @Test
    fun `keep and continue in a temporary chat continues the kept reply`() =
        vmTest(temporary = true) { vm ->
            stopComparisonInTemporaryChat(vm)
            coEvery { messageRepository.branchMessage(CONVERSATION_ID, PARALLEL_ID, LANE_B) } returns
                Result.Success(kept(parent = sends.single().userMessageId!!))

            vm.keepAndContinue(LANE_B)
            runCurrent()

            val continued = sends.last()
            assertThat(sends).hasSize(2)
            assertThat(continued.isContinued).isTrue()
            assertThat(continued.responseMessageId).isEqualTo(KEPT_ID)
            assertThat(continued.addedConvo).isNull()
            // Lane B's answer is continued by lane B's model, not by the chat's (lane A's).
            assertThat(continued.endpoint).isEqualTo(SECOND_ENDPOINT)
            assertThat(continued.model).isEqualTo(SECOND_MODEL)
            assertThat(vm.uiState.value.selectedModel).isEqualTo(SECOND_MODEL)
            assertThat(vm.uiState.value.error).isNull()
            assertThat(vm.uiState.value.comparisonState.isEnabled).isFalse()
            // The temp-chat guard: nothing about this chat reaches Room.
            coVerify(exactly = 0) { messageRepository.cacheMessages(any(), any()) }
            coVerify(exactly = 0) { messageRepository.getMessages(any()) }
        }

    @Test
    fun `keep in a temporary chat shows only the kept reply`() =
        vmTest(temporary = true) { vm ->
            stopComparisonInTemporaryChat(vm)
            val userId = sends.single().userMessageId!!
            coEvery { messageRepository.branchMessage(CONVERSATION_ID, PARALLEL_ID, LANE_B) } returns
                Result.Success(kept(parent = userId))

            vm.branchFromComparison(LANE_B)
            runCurrent()

            val shown = vm.uiState.value.displayMessages.map { it.message.messageId }
            assertThat(shown).containsExactly(userId, KEPT_ID).inOrder()
            assertThat(vm.uiState.value.comparisonState.isEnabled).isFalse()
            assertThat(vm.uiState.value.error).isNull()
        }

    // ── A lane stopped before it streamed text ──────────────────────────

    @Test
    fun `a lane that ran only a tool call before the stop still offers keep`() =
        vmTest(temporary = true) { vm ->
            stopComparisonInTemporaryChat(vm)

            // No text or thinking delta ever named either lane; the final message's parts do.
            val comparison = vm.uiState.value.comparisonState
            assertThat(comparison.primaryAgentId).isEqualTo(LANE_A)
            assertThat(comparison.secondaryAgentId).isEqualTo(LANE_B)
            assertThat(vm.uiState.value.continuableReplyId).isEqualTo(PARALLEL_ID)
        }

    // ── Normal chats: the Continue waits for the reload ──────────────────

    @Test
    fun `keep and continue in a saved chat starts the continue only after the reload lands`() =
        vmTest(seed = listOf(USER, PARALLEL)) { vm ->
            assertThat(vm.uiState.value.comparisonState.parallelMessageId).isEqualTo(PARALLEL_ID)
            val reload = CompletableDeferred<Unit>()
            coEvery { messageRepository.getMessages(CONVERSATION_ID) } coAnswers {
                reload.await()
                room.value = server
                Result.Success(server)
            }
            coEvery { messageRepository.branchMessage(CONVERSATION_ID, PARALLEL_ID, LANE_B) } returns
                Result.Success(kept(parent = USER.messageId))

            vm.keepAndContinue(LANE_B)
            runCurrent()

            // Shown at once, from memory, but not continued: the reload's Room write would land
            // under the stream and rebuild the path without its anchor.
            assertThat(vm.uiState.value.displayMessages.last().message.messageId).isEqualTo(KEPT_ID)
            assertThat(sends).isEmpty()
            // Cached before the reload, so a reload whose fetch fails still settles on the kept reply.
            coVerify { messageRepository.cacheMessages(listOf(kept(parent = USER.messageId)), null) }

            // A slow reload (its fetch retries) is still waited for, not given up on.
            advanceTimeBy(30_000)
            assertThat(vm.uiState.value.error).isNotEqualTo(ChatNotice.CONTINUE_RESPONSE_FAILED.marker())
            server = listOf(USER, PARALLEL, kept(parent = USER.messageId))
            reload.complete(Unit)
            runCurrent()

            assertThat(sends.single().isContinued).isTrue()
            assertThat(sends.single().responseMessageId).isEqualTo(KEPT_ID)
            assertThat(vm.uiState.value.isStreaming).isTrue()
            assertThat(vm.uiState.value.error).isNotEqualTo(ChatNotice.CONTINUE_RESPONSE_FAILED.marker())
        }

    @Test
    fun `continue sends nothing while comparing`() =
        vmTest(seed = listOf(USER, PARALLEL)) { vm ->
            assertThat(vm.uiState.value.comparisonState.isEnabled).isTrue()

            vm.continueGeneration()
            runCurrent()

            assertThat(sends).isEmpty()
        }

    @Test
    fun `rerunning an edited reply in a comparison re-runs both lanes`() =
        vmTest(seed = listOf(USER, PARALLEL)) { vm ->
            vm.startEditing(PARALLEL_ID)
            vm.onEditPartTextChanged(0, "edited answer")
            vm.rerunEdit()
            runCurrent()

            val rerun = sends.single()
            assertThat(rerun.addedConvo?.endpoint).isEqualTo(SECOND_ENDPOINT)
            assertThat(rerun.responseMessageId).isEqualTo(PARALLEL_ID)
            val comparison = vm.uiState.value.comparisonState
            assertThat(comparison.primaryIsStreaming).isTrue()
            assertThat(comparison.secondaryIsStreaming).isTrue()
            assertThat(comparison.parallelMessageId).isNull()
        }

    // ── Lane B's spec on reopen ──────────────────────────────────────────

    @Test
    fun `a reopened comparison finds lane B's spec once the config arrives after the load`() {
        val careful = ModelSpec(
            name = "careful",
            label = "Careful",
            preset = Preset(endpoint = SECOND_ENDPOINT, model = SECOND_MODEL),
        )
        val specLaneB = "${SECOND_ENDPOINT}__${SECOND_MODEL}___Careful____1"
        val parallel = PARALLEL.copy(content = laneParts(laneB = specLaneB))
        vmTest(seed = listOf(USER, parallel)) { vm ->
            assertThat(vm.uiState.value.comparisonState.isEnabled).isTrue()
            assertThat(vm.uiState.value.laneBSpec).isNull()

            startupConfig.value = StartupConfig(modelSpecs = ModelSpecs(list = listOf(careful)))
            runCurrent()
            assertThat(vm.uiState.value.laneBSpec).isEqualTo("careful")

            // The request reads the config through the ViewModel's own lookup, not the selection.
            vm.onInputChanged("next")
            vm.sendMessage()
            runCurrent()
            assertThat(sends.single().addedConvo?.spec).isEqualTo("careful")
        }
    }

    @Test
    fun `a reopened raw lane B gives up a spec once its endpoint's display label arrives`() {
        // A raw `openAI`/`gpt-4o` lane saves the endpoint's display label as its sender, the same
        // sender a spec labelled "GPT-4o" on that pair would save.
        val lookalike = ModelSpec(
            name = "lookalike",
            label = "GPT-4o",
            preset = Preset(endpoint = SECOND_ENDPOINT, model = SECOND_MODEL),
        )
        startupConfig.value = StartupConfig(modelSpecs = ModelSpecs(list = listOf(lookalike)))
        vmTest(seed = listOf(USER, PARALLEL)) { vm ->
            assertThat(vm.uiState.value.comparisonState.isEnabled).isTrue()
            assertThat(vm.uiState.value.laneBSpec).isEqualTo("lookalike")

            endpointConfigs.value = endpointConfigs.value +
                (SECOND_ENDPOINT to EndpointConfig(modelDisplayLabel = "GPT-4o"))
            runCurrent()

            assertThat(vm.uiState.value.laneBSpec).isNull()
            vm.onInputChanged("next")
            vm.sendMessage()
            runCurrent()
            assertThat(sends.single().addedConvo?.spec).isNull()
        }
    }

    @Test
    fun `a reopened lane B waits for its endpoint's config before taking a spec`() {
        // Until the config says what the endpoint displays, the sender may be that label.
        val lookalike = ModelSpec(
            name = "lookalike",
            label = "GPT-4o",
            preset = Preset(endpoint = SECOND_ENDPOINT, model = SECOND_MODEL),
        )
        startupConfig.value = StartupConfig(modelSpecs = ModelSpecs(list = listOf(lookalike)))
        endpointConfigs.value = mapOf(ENDPOINT to EndpointConfig())
        vmTest(seed = listOf(USER, PARALLEL)) { vm ->
            assertThat(vm.uiState.value.comparisonState.isEnabled).isTrue()
            assertThat(vm.uiState.value.laneBSpec).isNull()

            // Loaded, and the endpoint displays no label of its own: the sender is the spec's.
            endpointConfigs.value = endpointConfigs.value + (SECOND_ENDPOINT to EndpointConfig())
            runCurrent()

            assertThat(vm.uiState.value.laneBSpec).isEqualTo("lookalike")
        }
    }

    // ── The Stopped chip waits for the run status ────────────────────────

    @Test
    fun `the chip waits for the run status on a reopened unfinished reply`() {
        val status = CompletableDeferred<ChatStatusResponse>()
        coEvery { chatRepository.checkStreamStatus(CONVERSATION_ID, any()) } coAnswers { status.await() }
        vmTest(seed = listOf(USER, STOPPED)) { vm ->
            assertThat(vm.uiState.value.displayMessages).hasSize(2)
            assertThat(vm.uiState.value.continuableReplyId).isNull()

            status.complete(ChatStatusResponse(active = false))
            runCurrent()

            assertThat(vm.uiState.value.continuableReplyId).isEqualTo(STOPPED.messageId)
        }
    }

    @Test
    fun `the chip stays off a reply paused for approval`() {
        coEvery { chatRepository.checkStreamStatus(CONVERSATION_ID, any()) } returns
            ChatStatusResponse(active = true, pendingAction = approval())
        every { chatRepository.resumeStream(any()) } returns MutableSharedFlow()
        vmTest(seed = listOf(USER, STOPPED)) { vm ->
            assertThat(vm.uiState.value.pendingAction).isNotNull()
            assertThat(vm.uiState.value.continuableReplyId).isNull()
        }
    }

    @Test
    fun `the chip stays off while the status check fails and returns once it answers`() {
        coEvery { chatRepository.checkStreamStatus(CONVERSATION_ID, any()) } throws IllegalStateException("offline")
        connected.value = false
        vmTest(seed = listOf(USER, STOPPED)) { vm ->
            assertThat(vm.uiState.value.displayMessages).hasSize(2)
            assertThat(vm.uiState.value.continuableReplyId).isNull()

            coEvery { chatRepository.checkStreamStatus(CONVERSATION_ID, any()) } returns ChatStatusResponse(active = false)
            connected.value = true
            runCurrent()

            assertThat(vm.uiState.value.continuableReplyId).isEqualTo(STOPPED.messageId)
        }
    }

    @Test
    fun `a status check the server fails while online is asked again`() {
        // A 5xx or a timeout: the device never goes offline, so only the backoff asks again.
        coEvery { chatRepository.checkStreamStatus(CONVERSATION_ID, any()) } throws IllegalStateException("502")
        vmTest(seed = listOf(USER, STOPPED)) { vm ->
            assertThat(vm.uiState.value.continuableReplyId).isNull()

            coEvery { chatRepository.checkStreamStatus(CONVERSATION_ID, any()) } returns ChatStatusResponse(active = false)
            advanceTimeBy(2_001)
            runCurrent()

            assertThat(vm.uiState.value.continuableReplyId).isEqualTo(STOPPED.messageId)
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /**
     * Turns Compare on, sends, and ends the run on a stopped comparison reply whose lane B ran only
     * a tool call. A temporary chat finalizes it in memory, as the server never lists it.
     */
    private fun TestScope.stopComparisonInTemporaryChat(vm: ChatViewModel) {
        val run = MutableSharedFlow<StreamEvent>(extraBufferCapacity = 4)
        streams.addLast(run)
        vm.startComparison(SECOND_ENDPOINT, SECOND_MODEL)
        vm.onInputChanged("Which is better?")
        vm.sendMessage()
        runCurrent()
        val userId = sends.single().userMessageId!!
        assertThat(sends.single().addedConvo).isNotNull()

        run.tryEmit(
            StreamEvent.Final(
                requestMessage = USER.copy(messageId = userId),
                responseMessage = PARALLEL.copy(parentMessageId = userId),
                conversation = null,
            ),
        )
        runCurrent()
        assertThat(vm.uiState.value.isStreaming).isFalse()
        assertThat(vm.uiState.value.comparisonState.parallelMessageId).isEqualTo(PARALLEL_ID)
    }

    private fun vmTest(
        temporary: Boolean = false,
        seed: List<Message> = emptyList(),
        body: suspend TestScope.(ChatViewModel) -> Unit,
    ) = runTest(testDispatcher) {
        server = seed
        fixture.selectionHandoff.put(conversationId = CONVERSATION_ID, choice = ModelChoice.Raw(ENDPOINT, MODEL))
        val vm = fixture.build(
            defaultDispatcher = testDispatcher,
            initialConversationId = CONVERSATION_ID,
            initialIsTemporary = temporary,
        )
        runCurrent()
        try {
            body(vm)
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    private fun kept(parent: String) = Message(
        messageId = KEPT_ID,
        conversationId = CONVERSATION_ID,
        parentMessageId = parent,
        isCreatedByUser = false,
        unfinished = true,
        content = listOf(searchCall(agentId = null)),
    )

    private fun approval() = PendingAction(
        actionId = "act_1",
        conversationId = CONVERSATION_ID,
        payload = PendingActionPayload(type = PendingActionTypes.TOOL_APPROVAL),
    )

    private companion object {
        const val CONVERSATION_ID = "conv-1"
        const val ENDPOINT = "anthropic"
        const val MODEL = "claude-haiku-4-5"
        const val SECOND_ENDPOINT = "openAI"
        const val SECOND_MODEL = "gpt-4o"
        const val LANE_A = "${ENDPOINT}__${MODEL}___Claude"
        const val LANE_B = "${SECOND_ENDPOINT}__${SECOND_MODEL}___GPT-4o____1"
        const val PARALLEL_ID = "reply-parallel"
        const val KEPT_ID = "reply-kept"

        fun searchCall(agentId: String?) = MessageContentPart(
            type = ContentType.TOOL_CALL,
            toolCall = AgentToolCall(name = "web_search", id = "call_1"),
            agentId = agentId,
        )

        /** Lane A wrote some text; lane B was stopped inside its first tool call. */
        fun laneParts(laneB: String = LANE_B): List<MessageContentPart> = listOf(
            MessageContentPart(type = ContentType.TEXT, text = "Partial answer", agentId = LANE_A),
            searchCall(agentId = laneB),
        )

        val USER = Message(
            messageId = "user-1",
            conversationId = CONVERSATION_ID,
            parentMessageId = NO_PARENT,
            isCreatedByUser = true,
            text = "Which is better?",
        )

        val PARALLEL = Message(
            messageId = PARALLEL_ID,
            conversationId = CONVERSATION_ID,
            parentMessageId = USER.messageId,
            isCreatedByUser = false,
            unfinished = true,
            content = laneParts(),
        )

        val STOPPED = Message(
            messageId = "reply-stopped",
            conversationId = CONVERSATION_ID,
            parentMessageId = USER.messageId,
            isCreatedByUser = false,
            unfinished = true,
            text = "Cut off mid",
        )
    }
}
