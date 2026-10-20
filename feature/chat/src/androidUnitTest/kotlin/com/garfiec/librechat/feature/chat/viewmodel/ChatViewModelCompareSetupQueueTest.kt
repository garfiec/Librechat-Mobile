package com.garfiec.librechat.feature.chat.viewmodel

import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.ChatFontSize
import com.garfiec.librechat.core.data.datastore.ChatHeaderAlignment
import com.garfiec.librechat.core.data.datastore.ChatHeaderContent
import com.garfiec.librechat.core.data.datastore.ContextBarPlacement
import com.garfiec.librechat.core.data.datastore.DuringRunAction
import com.garfiec.librechat.core.data.datastore.StarredModelsDisplay
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.StreamEvent
import com.garfiec.librechat.core.model.request.AddedConversation
import com.garfiec.librechat.core.model.response.ChatResumeResponse
import com.garfiec.librechat.core.model.response.ChatStatusResponse
import com.garfiec.librechat.feature.chat.viewmodel.delegate.PlatformFileHandler
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * A follow-up queued while a reply streams, with Compare setup opened before the reply ends, waits
 * through the setup and goes to both models on Start. `MessageQueueDelegateTest` proves the hold
 * and its release; only the assembled ViewModel shows the release reaching a send that carries the
 * second model. Harness as in [ChatViewModelDuringRunSendTest] (a resumed live run, cancelled
 * inside the body).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelCompareSetupQueueTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val fixture = ChatViewModelTestFixture()
    private val chatRepository get() = fixture.chatRepository
    private val configRepository get() = fixture.configRepository
    private val settingsDataStore get() = fixture.settingsDataStore
    private val fileHandler = mockk<PlatformFileHandler>(relaxed = true)
    private val resumedStream = MutableSharedFlow<StreamEvent>(extraBufferCapacity = 8)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fixture.stubDefaults()
        every { configRepository.endpointConfigs } returns MutableStateFlow(mapOf(ENDPOINT to EndpointConfig()))
        every { configRepository.availableModels } returns
            MutableStateFlow(mapOf(ENDPOINT to listOf(MODEL, SECOND_MODEL)))
        every { settingsDataStore.selectedMcpServers } returns flowOf(emptySet())
        every { settingsDataStore.enabledTools } returns flowOf(emptySet())
        every { settingsDataStore.duringRunAction } returns flowOf(DuringRunAction.QUEUE)
        every { fixture.serverDataStore.currentUrlFlow } returns flowOf("https://example.test")
        every { settingsDataStore.chatFontSize } returns flowOf(ChatFontSize.MEDIUM)
        every { settingsDataStore.starredModelsDisplay } returns flowOf(StarredModelsDisplay.OFF)
        every { settingsDataStore.chatHeaderContent } returns flowOf(ChatHeaderContent.TITLE)
        every { settingsDataStore.chatHeaderAlignment } returns flowOf(ChatHeaderAlignment.LEFT)
        every { settingsDataStore.contextBarPlacement } returns flowOf(ContextBarPlacement.OPTIONS_SHEET)
        every { settingsDataStore.contextGaugeExpanded } returns flowOf(false)
        every { fixture.platformDelegateFactory.createFileHandler(any()) } returns fileHandler
        every { fileHandler.attachedFiles } returns MutableStateFlow(emptyList())
        coEvery { fixture.conversationRepository.getConversation(any(), any()) } returns Result.Error(message = "test")
        coEvery { chatRepository.resumeChat(any()) } returns Result.Success(ChatResumeResponse(status = "resuming"))
        // Opens onto a live run, so a follow-up can be queued behind it.
        coEvery { chatRepository.checkStreamStatus(eq(CONVERSATION_ID), any()) } returns ChatStatusResponse(active = true)
        every { chatRepository.resumeStream(any()) } returns resumedStream
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a follow-up held by Compare setup is sent to both models on Start`() = runTest(testDispatcher) {
        val added = mutableListOf<AddedConversation?>()
        every {
            chatRepository.startChat(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(),
                captureNullable(added),
                any(), any(), any(), any(),
            )
        } returns MutableSharedFlow()
        fixture.selectionHandoff.put(conversationId = CONVERSATION_ID, endpoint = ENDPOINT, model = MODEL)
        val vm = fixture.build(defaultDispatcher = testDispatcher, initialConversationId = CONVERSATION_ID)
        runCurrent()
        try {
            vm.onInputChanged("the next turn")
            vm.queueMessage()
            vm.beginComparisonSetup()
            resumedStream.emit(cleanFinal())
            runCurrent()

            assertThat(vm.uiState.value.messageQueue).hasSize(1)
            assertThat(added).isEmpty()

            vm.startComparison(ENDPOINT, SECOND_MODEL)
            runCurrent()

            assertThat(vm.uiState.value.messageQueue).isEmpty()
            assertThat(added.single()?.model).isEqualTo(SECOND_MODEL)
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    @Test
    fun `a follow-up held by Compare setup goes to the chat's model alone on Cancel`() = runTest(testDispatcher) {
        val added = mutableListOf<AddedConversation?>()
        every {
            chatRepository.startChat(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(),
                captureNullable(added),
                any(), any(), any(), any(),
            )
        } returns MutableSharedFlow()
        fixture.selectionHandoff.put(conversationId = CONVERSATION_ID, endpoint = ENDPOINT, model = MODEL)
        val vm = fixture.build(defaultDispatcher = testDispatcher, initialConversationId = CONVERSATION_ID)
        runCurrent()
        try {
            vm.onInputChanged("the next turn")
            vm.queueMessage()
            vm.beginComparisonSetup()
            resumedStream.emit(cleanFinal())
            runCurrent()

            vm.cancelComparisonSetup()
            runCurrent()

            assertThat(vm.uiState.value.messageQueue).isEmpty()
            assertThat(added).containsExactly(null)
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    private fun cleanFinal() = StreamEvent.Final(
        requestMessage = null,
        responseMessage = Message(
            messageId = "assistant-1",
            conversationId = CONVERSATION_ID,
            text = "done",
            isCreatedByUser = false,
        ),
        conversation = null,
    )

    private companion object {
        const val CONVERSATION_ID = "conv-1"
        const val ENDPOINT = "anthropic"
        const val MODEL = "claude-haiku-4-5"
        const val SECOND_MODEL = "claude-opus-4-5"
    }
}
