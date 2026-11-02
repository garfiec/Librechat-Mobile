package com.garfiec.librechat.feature.chat.viewmodel

import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.BackendBuildClass
import com.garfiec.librechat.core.common.DetectedBackend
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.ChatFontSize
import com.garfiec.librechat.core.data.datastore.ChatHeaderAlignment
import com.garfiec.librechat.core.data.datastore.ChatHeaderContent
import com.garfiec.librechat.core.data.datastore.ContextBarPlacement
import com.garfiec.librechat.core.data.datastore.DuringRunAction
import com.garfiec.librechat.core.data.datastore.StarredModelsDisplay
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.PendingSteer
import com.garfiec.librechat.core.model.StreamEvent
import com.garfiec.librechat.core.model.response.ChatResumeResponse
import com.garfiec.librechat.core.model.response.ChatStatusResponse
import com.garfiec.librechat.core.model.response.SteerResponse
import com.garfiec.librechat.feature.chat.viewmodel.delegate.PlatformFileHandler
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
 * An MCP App's approved `ui/message` ([ChatViewModel.sendAppMessage]): refused while a reply runs,
 * and otherwise sent as a turn of its own text, the composer's draft untouched. Runs on
 * [ChatViewModelDuringRunSendTest]'s harness (a conversation opened onto a live run), for the same
 * reasons that class gives.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelAppMessageTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private val fixture = ChatViewModelTestFixture()
    private val agentRepository get() = fixture.agentRepository
    private val chatRepository get() = fixture.chatRepository
    private val messageRepository get() = fixture.messageRepository
    private val configRepository get() = fixture.configRepository
    private val conversationRepository get() = fixture.conversationRepository
    private val favoritesRepository get() = fixture.favoritesRepository
    private val keyRepository get() = fixture.keyRepository
    private val roleRepository get() = fixture.roleRepository
    private val serverDataStore get() = fixture.serverDataStore
    private val settingsDataStore get() = fixture.settingsDataStore
    private val platformDelegateFactory get() = fixture.platformDelegateFactory
    private val serverFileSelectionHandoff get() = fixture.serverFileSelectionHandoff
    private val selectionHandoff get() = fixture.selectionHandoff
    private val fileHandler = mockk<PlatformFileHandler>(relaxed = true)

    /** The resumed run's SSE events. Hot, so a test can end the run by emitting an error. */
    private val resumedStream = MutableSharedFlow<StreamEvent>(extraBufferCapacity = 8)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fixture.stubDefaults()

        // Open the steering gate: 0.8.8-rc1 clears the version comparison outright, so the test
        // does not depend on the dev-build date gate or on the generated commit map.
        every { configRepository.detectedBackend } returns
            MutableStateFlow(DetectedBackend("0.8.8-rc1", BackendBuildClass.RC))
        every { configRepository.detectedBackendVersion } returns MutableStateFlow("0.8.8-rc1")

        // Collection-typed flows read by init-time delegates: relaxed mockk hands back a bare
        // Object for the erased element type, which those delegates cast to Map/List.
        // A real endpoint + model, because a queue drain goes through `runWhenSendReady`, which
        // REFUSES (and re-queues) when nothing is selected — so without these the second turn in
        // the turn-identity test below would never start.
        every { configRepository.endpointConfigs } returns MutableStateFlow(mapOf(ENDPOINT to EndpointConfig()))
        every { configRepository.availableModels } returns MutableStateFlow(mapOf(ENDPOINT to listOf(MODEL)))
        every { settingsDataStore.selectedMcpServers } returns flowOf(emptySet())
        every { settingsDataStore.enabledTools } returns flowOf(emptySet())

        // Every source of the `uiState` combine. A relaxed mock hands back a flow that never
        // emits, and one silent source stalls the whole combine — `uiState.value` would then sit
        // on `stateIn`'s `ChatUiState()` placeholder forever and every assertion below would read
        // defaults rather than this ViewModel's state.
        every { serverDataStore.currentUrlFlow } returns flowOf("https://example.test")
        every { settingsDataStore.chatFontSize } returns flowOf(ChatFontSize.MEDIUM)
        every { settingsDataStore.starredModelsDisplay } returns flowOf(StarredModelsDisplay.OFF)
        every { settingsDataStore.chatHeaderContent } returns flowOf(ChatHeaderContent.TITLE)
        every { settingsDataStore.chatHeaderAlignment } returns flowOf(ChatHeaderAlignment.LEFT)
        every { settingsDataStore.contextBarPlacement } returns flowOf(ContextBarPlacement.OPTIONS_SHEET)
        every { settingsDataStore.contextGaugeExpanded } returns flowOf(false)

        // buildSendSpec reads the attachment list; the relaxed factory would hand back an
        // untyped stub that fails the List cast.
        every { platformDelegateFactory.createFileHandler(any()) } returns fileHandler
        every { fileHandler.attachedFiles } returns MutableStateFlow(emptyList())

        coEvery { conversationRepository.getConversation(any(), any()) } returns Result.Error(message = "test")
        coEvery { chatRepository.steerChat(any()) } returns
            Result.Success(SteerResponse(status = "queued", steerId = "steer-1"))
        coEvery { chatRepository.resumeChat(any()) } returns
            Result.Success(ChatResumeResponse(status = "resuming"))

        // The conversation opens onto a live run: `resumeActiveStreamIfNeeded` sees active=true and
        // flips `isStreaming` on. `resumedStream` is a hot flow the test drives: left alone the run
        // stays open for the whole test, and emitting an error ends it.
        coEvery { chatRepository.checkStreamStatus(eq(CONVERSATION_ID), any()) } returns
            ChatStatusResponse(active = true)
        every { chatRepository.resumeStream(any()) } returns resumedStream
        // The next turn's stream (a queue drain sends through `startChat`). Never emits, so the
        // second run just stays open.
        every {
            chatRepository.startChat(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(),
            )
        } returns MutableSharedFlow()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `an MCP App message is refused while a reply runs and leaves the draft alone`() =
        duringRunTest(DuringRunAction.QUEUE) { vm ->
            vm.onInputChanged("my draft")
            assertThat(vm.sendAppMessage("Plan a hike")).isFalse()
            runCurrent()
            assertThat(vm.uiState.value.inputText).isEqualTo("my draft")
            assertThat(vm.uiState.value.messageQueue).isEmpty()
        }

    @Test
    fun `an MCP App message sends only its text once the run is over, keeping the draft`() =
        duringRunTest(DuringRunAction.QUEUE) { vm ->
            resumedStream.emit(cleanFinal())
            runCurrent()
            assertThat(vm.uiState.value.isStreaming).isFalse()
            vm.onInputChanged("my draft")

            assertThat(vm.sendAppMessage("Plan a hike")).isTrue()
            runCurrent()

            assertThat(vm.uiState.value.inputText).isEqualTo("my draft")
            assertThat(vm.uiState.value.isStreaming).isTrue()
            verify(exactly = 1) {
                chatRepository.startChat(
                    match { it == "Plan a hike" }, any(), any(), any(), any(), any(), any(), any(), any(), any(),
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                    any(),
                )
            }
        }

    /** The run finishing cleanly, handing back [pendingSteers] it never injected. */
    private fun cleanFinal(pendingSteers: List<PendingSteer> = emptyList()) = StreamEvent.Final(
        requestMessage = null,
        responseMessage = com.garfiec.librechat.core.model.Message(
            messageId = SETTLED_ID,
            conversationId = CONVERSATION_ID,
            text = "done",
            isCreatedByUser = false,
        ),
        conversation = null,
        pendingSteers = pendingSteers,
    )

    private fun duringRunTest(
        preference: DuringRunAction,
        arrange: () -> Unit = {},
        body: suspend TestScope.(ChatViewModel) -> Unit,
    ) = runTest(testDispatcher) {
        arrange()
        every { settingsDataStore.duringRunAction } returns flowOf(preference)
        // Stages the (endpoint, model) `init` applies, so the conversation opens with a real
        // selection rather than waiting on the model pipeline.
        selectionHandoff.put(conversationId = CONVERSATION_ID, choice = ModelChoice.Raw(ENDPOINT, MODEL))
        val vm = newViewModel()
        runCurrent()
        try {
            body(vm)
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    private fun newViewModel(): ChatViewModel =
        fixture.build(
            defaultDispatcher = testDispatcher,
            initialConversationId = CONVERSATION_ID,
        )

    private companion object {
        const val CONVERSATION_ID = "conv-1"
        const val ENDPOINT = "anthropic"
        const val MODEL = "claude-haiku-4-5"
        const val TEXT = "Answer only in French."
        const val SETTLED_ID = "assistant-1"
    }
}
