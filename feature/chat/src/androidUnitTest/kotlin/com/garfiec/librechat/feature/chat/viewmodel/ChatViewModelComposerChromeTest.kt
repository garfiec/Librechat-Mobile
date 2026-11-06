package com.garfiec.librechat.feature.chat.viewmodel

import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.BackendBuildClass
import com.garfiec.librechat.core.common.DetectedBackend
import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.common.ToolConstants
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.DuringRunAction
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.StreamEvent
import com.garfiec.librechat.core.model.mcp.McpDisplayStatus
import com.garfiec.librechat.core.model.response.ChatResumeResponse
import com.garfiec.librechat.core.model.response.ChatStatusResponse
import com.garfiec.librechat.core.model.response.SteerResponse
import com.garfiec.librechat.feature.chat.components.AttachedFile
import com.garfiec.librechat.feature.chat.util.SettingChip
import com.garfiec.librechat.feature.chat.util.SettingChipState
import com.garfiec.librechat.feature.chat.util.ThinkingChip
import com.garfiec.librechat.feature.chat.viewmodel.delegate.PlatformFileHandler
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
 * The composer chrome's ViewModel boundary: a steer's excerpt restored during a queued edit goes to
 * the stashed draft, a failed upload holds every send path (send, queue, a steer with files, saving
 * a queued edit), and a settings chip's switch and Undo go through the "+" sheet's toggles. Driven through the real ViewModel over a live run, on the
 * [ChatViewModelQueuedSkillsTest] harness shape.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelComposerChromeTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val fixture = ChatViewModelTestFixture()
    private val chatRepository get() = fixture.chatRepository
    private val fileHandler = mockk<PlatformFileHandler>(relaxed = true)

    /** The resumed run's SSE events; a test ends the run by emitting its Final. */
    private val resumedStream = MutableSharedFlow<StreamEvent>(extraBufferCapacity = 8)

    /** How many `startChat`s the ViewModel fired. */
    private val sent = mutableListOf<Unit>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fixture.stubDefaults()
        with(fixture) {
            // Steering open: 0.8.8-rc1 clears the version gate outright.
            every { configRepository.detectedBackend } returns
                MutableStateFlow(DetectedBackend("0.8.8-rc1", BackendBuildClass.RC))
            every { configRepository.detectedBackendVersion } returns MutableStateFlow("0.8.8-rc1")
            every { configRepository.endpointConfigs } returns MutableStateFlow(
                mapOf(ENDPOINT to EndpointConfig(), EndpointConstants.AGENTS to EndpointConfig()),
            )
            every { configRepository.availableModels } returns MutableStateFlow(mapOf(ENDPOINT to listOf(MODEL)))
            every { settingsDataStore.selectedMcpServers } returns flowOf(emptySet())
            every { settingsDataStore.enabledTools } returns flowOf(emptySet())
            every { serverDataStore.currentUrlFlow } returns flowOf("https://example.test")
            every { platformDelegateFactory.createFileHandler(any()) } returns fileHandler
            every { fileHandler.attachedFiles } returns MutableStateFlow(emptyList())
            coEvery { conversationRepository.getConversation(any(), any()) } returns Result.Error(message = "test")
            coEvery { chatRepository.resumeChat(any()) } returns Result.Success(ChatResumeResponse(status = "resuming"))
            // The conversation opens onto a live run, held open until a test emits its Final.
            coEvery { chatRepository.checkStreamStatus(eq(CONVERSATION_ID), any()) } returns
                ChatStatusResponse(active = true)
            every { chatRepository.resumeStream(any()) } returns resumedStream
            every {
                chatRepository.startChat(
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                    any(), any(), any(), any(), any(), any(),
                )
            } answers {
                sent += Unit
                MutableSharedFlow()
            }
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Steers [STEERED_QUOTE] (accepted, not yet injected), queues a follow-up, opens it for editing
     * and then lets the run inject the steer without its quotes, which re-stages them.
     */
    private suspend fun TestScope.restageDuringQueuedEdit(vm: ChatViewModel) {
        coEvery { chatRepository.steerChat(any()) } returns
            Result.Success(SteerResponse(status = "queued", steerId = "s1"))
        vm.addPendingQuote(STEERED_QUOTE)
        vm.onInputChanged(TEXT)
        vm.steerMessage()
        runCurrent()
        assertThat(vm.uiState.value.pendingQuotes).isEmpty()
        vm.onInputChanged("Then summarise.")
        vm.queueMessage()
        runCurrent()
        vm.editQueued(vm.uiState.value.messageQueue.single().localId)
        runCurrent()
        assertThat(vm.uiState.value.isEditingQueued).isTrue()

        resumedStream.emit(StreamEvent.SteerApplied(steerId = "s1", text = TEXT, quotes = emptyList()))
        runCurrent()

        // Not onto the queued item's chips, which are on screen during the edit.
        assertThat(vm.uiState.value.pendingQuotes).isEmpty()
    }

    @Test
    fun `a steer's lost excerpt restored during a queued edit comes back with the draft on cancel`() =
        queuedSkillsTest(DuringRunAction.STEER) { vm ->
            restageDuringQueuedEdit(vm)

            vm.cancelQueuedEdit()
            runCurrent()

            assertThat(vm.uiState.value.pendingQuotes).containsExactly(STEERED_QUOTE)
            assertThat(vm.uiState.value.messageQueue.single().quotes).isEmpty()
            assertThat(vm.uiState.value.draftRestore).isEqualTo(DraftRestore.NONE)
        }

    @Test
    fun `a steer's lost excerpt restored during a queued edit stays off the saved item`() =
        queuedSkillsTest(DuringRunAction.STEER) { vm ->
            restageDuringQueuedEdit(vm)

            vm.onInputChanged("Then summarise briefly.")
            vm.commitQueuedEdit()
            runCurrent()

            val saved = vm.uiState.value.messageQueue.single()
            assertThat(saved.text).isEqualTo("Then summarise briefly.")
            assertThat(saved.quotes).isEmpty()
            assertThat(vm.uiState.value.pendingQuotes).containsExactly(STEERED_QUOTE)
        }

    private val failedUpload = AttachedFile(uri = "f", name = "report.pdf", uploadFailed = true)

    /**
     * Puts [failedUpload] in the tray. The relaxed handler mock would answer false to
     * `hasFailedUploads` whatever the tray holds, so it answers from the tray, as the interface's
     * default does; a path that skipped the gate would then send or queue regardless.
     */
    private fun trayWithFailedUpload() {
        val tray = MutableStateFlow(listOf(failedUpload))
        every { fileHandler.attachedFiles } returns tray
        every { fileHandler.hasFailedUploads() } answers { tray.value.any { it.uploadFailed } }
    }

    @Test
    fun `a failed upload holds a send`() = queuedSkillsTest(DuringRunAction.QUEUE) { vm ->
        resumedStream.emit(cleanFinal())
        runCurrent()
        assertThat(vm.uiState.value.isStreaming).isFalse()
        val sends = sent.size
        trayWithFailedUpload()

        vm.onInputChanged(TEXT)
        vm.sendMessage()
        runCurrent()

        assertThat(sent).hasSize(sends)
        assertThat(vm.uiState.value.inputText).isEqualTo(TEXT)
    }

    @Test
    fun `a failed upload holds a queued follow-up`() = queuedSkillsTest(DuringRunAction.QUEUE) { vm ->
        trayWithFailedUpload()

        vm.onInputChanged(TEXT)
        vm.queueMessage()
        runCurrent()

        assertThat(vm.uiState.value.messageQueue).isEmpty()
        assertThat(vm.uiState.value.inputText).isEqualTo(TEXT)
    }

    @Test
    fun `a failed upload holds a steer with files, which would have gone to the queue`() =
        queuedSkillsTest(DuringRunAction.STEER) { vm ->
            trayWithFailedUpload()

            vm.onInputChanged(TEXT)
            vm.steerMessage()
            runCurrent()

            assertThat(vm.uiState.value.messageQueue).isEmpty()
            assertThat(vm.uiState.value.pendingSteers).isEmpty()
            coVerify(exactly = 0) { chatRepository.steerChat(any()) }
        }

    @Test
    fun `a failed upload holds saving a queued edit and keeps the edit open`() =
        queuedSkillsTest(DuringRunAction.QUEUE) { vm ->
            vm.onInputChanged(TEXT)
            vm.queueMessage()
            runCurrent()
            val queued = vm.uiState.value.messageQueue.single()
            vm.editQueued(queued.localId)
            runCurrent()
            // A file added during the edit failed to upload.
            trayWithFailedUpload()

            vm.onInputChanged("$TEXT Briefly.")
            vm.commitQueuedEdit()
            runCurrent()

            assertThat(vm.uiState.value.messageQueue.single()).isEqualTo(queued)
            assertThat(vm.uiState.value.isEditingQueued).isTrue()
            assertThat(vm.uiState.value.inputText).isEqualTo("$TEXT Briefly.")
        }

    private val memoryChip = SettingChip.Tool(ToolConstants.MEMORY, SettingChipState.ON, pinned = false)

    @Test
    fun `a settings chip's undo turns the tool it switched off back on`() =
        queuedSkillsTest(DuringRunAction.QUEUE) { vm ->
            vm.toggleTool(ToolConstants.MEMORY)
            assertThat(vm.uiState.value.effectiveEnabledTools).contains(ToolConstants.MEMORY)

            vm.toggleSettingChip(memoryChip)
            assertThat(vm.uiState.value.effectiveEnabledTools).doesNotContain(ToolConstants.MEMORY)

            vm.turnSettingChipBackOn(memoryChip)
            runCurrent()
            assertThat(vm.uiState.value.effectiveEnabledTools).contains(ToolConstants.MEMORY)
            // Memory is global: the Undo writes the stored set back too.
            coVerify(exactly = 2) { fixture.settingsDataStore.setEnabledTools(setOf(ToolConstants.MEMORY)) }
        }

    @Test
    fun `a settings chip's undo leaves a tool the sheet already turned back on`() =
        queuedSkillsTest(DuringRunAction.QUEUE) { vm ->
            vm.toggleTool(ToolConstants.MEMORY)
            vm.toggleSettingChip(memoryChip)
            // The "+" sheet's switch, before the snackbar's Undo.
            vm.toggleTool(ToolConstants.MEMORY)

            vm.turnSettingChipBackOn(memoryChip)
            runCurrent()

            // Turning it off again here would switch Memory off in every chat, unasked.
            assertThat(vm.uiState.value.effectiveEnabledTools).contains(ToolConstants.MEMORY)
        }

    @Test
    fun `an MCP settings chip switches its server and its undo brings it back`() =
        queuedSkillsTest(DuringRunAction.QUEUE) { vm ->
            val chip = SettingChip.Mcp("linear", "Linear", SettingChipState.ON, McpDisplayStatus.CONNECTED)
            vm.toggleMcpServer("linear")
            assertThat(vm.uiState.value.effectiveMcpServerNames).containsExactly("linear")

            vm.toggleSettingChip(chip)
            assertThat(vm.uiState.value.effectiveMcpServerNames).isEmpty()
            assertThat(vm.uiState.value.effectiveEnabledTools).isEmpty()

            vm.turnSettingChipBackOn(chip)
            assertThat(vm.uiState.value.effectiveMcpServerNames).containsExactly("linear")
        }

    @Test
    fun `the Thinking chip switches nothing`() = queuedSkillsTest(DuringRunAction.QUEUE) { vm ->
        val chip = SettingChip.Thinking(
            ThinkingChip(key = "reasoning_effort", options = listOf("", "low"), configured = "", pending = null, unsetIsOff = false),
        )
        val before = vm.uiState.value.selection

        vm.toggleSettingChip(chip)
        vm.turnSettingChipBackOn(chip)

        assertThat(vm.uiState.value.selection).isEqualTo(before)
    }

    private fun cleanFinal() = StreamEvent.Final(
        requestMessage = null,
        responseMessage = Message(messageId = "assistant-1", conversationId = CONVERSATION_ID, text = "done"),
        conversation = null,
    )

    private fun queuedSkillsTest(
        preference: DuringRunAction,
        body: suspend TestScope.(ChatViewModel) -> Unit,
    ) = runTest(testDispatcher) {
        every { fixture.settingsDataStore.duringRunAction } returns flowOf(preference)
        fixture.selectionHandoff.put(conversationId = CONVERSATION_ID, choice = ModelChoice.Raw(ENDPOINT, MODEL))
        val vm = fixture.build(defaultDispatcher = testDispatcher, initialConversationId = CONVERSATION_ID)
        runCurrent()
        try {
            assertThat(vm.uiState.value.isStreaming).isTrue()
            body(vm)
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    private companion object {
        const val CONVERSATION_ID = "conv-1"
        const val ENDPOINT = "anthropic"
        const val MODEL = "claude-haiku-4-5"
        const val TEXT = "Answer only in French."
        const val STEERED_QUOTE = "the selected excerpt"
    }
}
