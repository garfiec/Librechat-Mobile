package com.garfiec.librechat.feature.chat.viewmodel

import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.ChatFontSize
import com.garfiec.librechat.core.data.datastore.ChatHeaderAlignment
import com.garfiec.librechat.core.data.datastore.ChatHeaderContent
import com.garfiec.librechat.core.data.datastore.ContextBarPlacement
import com.garfiec.librechat.core.data.datastore.StarredModelsDisplay
import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.RunStepStatus
import com.garfiec.librechat.core.model.StreamEvent
import com.garfiec.librechat.core.model.content.AgentToolCall
import com.garfiec.librechat.core.model.content.MessageContentPart
import com.garfiec.librechat.core.model.response.ChatStatusResponse
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
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
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * The "Starting sandbox environment" flag through the real ViewModel: set by `on_sandbox_starting`,
 * cleared by the call's completion or closure, pruned by a resume snapshot and cleared by the
 * finalize. Opens onto a live run the way [ChatViewModelDuringRunSendTest] does (see its KDoc for
 * why the run is resumed rather than sent, and why every case cancels the ViewModel scope itself).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelSandboxStartingTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val fixture = ChatViewModelTestFixture()
    private val resumedStream = MutableSharedFlow<StreamEvent>(extraBufferCapacity = 8)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fixture.stubDefaults()
        with(fixture) {
            every { configRepository.endpointConfigs } returns MutableStateFlow(mapOf(ENDPOINT to EndpointConfig()))
            every { configRepository.availableModels } returns MutableStateFlow(mapOf(ENDPOINT to listOf(MODEL)))
            every { settingsDataStore.selectedMcpServers } returns flowOf(emptySet())
            every { settingsDataStore.enabledTools } returns flowOf(emptySet())
            // Every source of the `uiState` combine must emit, or `uiState.value` stays on its placeholder.
            every { serverDataStore.currentUrlFlow } returns flowOf("https://example.test")
            every { settingsDataStore.chatFontSize } returns flowOf(ChatFontSize.MEDIUM)
            every { settingsDataStore.starredModelsDisplay } returns flowOf(StarredModelsDisplay.OFF)
            every { settingsDataStore.chatHeaderContent } returns flowOf(ChatHeaderContent.TITLE)
            every { settingsDataStore.chatHeaderAlignment } returns flowOf(ChatHeaderAlignment.LEFT)
            every { settingsDataStore.contextBarPlacement } returns flowOf(ContextBarPlacement.OPTIONS_SHEET)
            every { settingsDataStore.contextGaugeExpanded } returns flowOf(false)
            coEvery { conversationRepository.getConversation(any(), any()) } returns Result.Error(message = "test")
            coEvery { chatRepository.checkStreamStatus(eq(CONVERSATION_ID), any()) } returns
                ChatStatusResponse(active = true)
            every { chatRepository.resumeStream(any()) } returns resumedStream
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a code call reads as booting until it completes or closes`() = liveRunTest { vm ->
        resumedStream.emit(StreamEvent.ToolCallStart(toolCallId = "call_1", toolName = "execute_code", input = ""))
        resumedStream.emit(StreamEvent.ToolCallStart(toolCallId = "call_2", toolName = "execute_code", input = ""))
        resumedStream.emit(StreamEvent.SandboxStarting(toolCallId = "call_1"))
        resumedStream.emit(StreamEvent.SandboxStarting(toolCallId = "call_2"))
        runCurrent()
        assertThat(vm.uiState.value.sandboxStartingToolCallIds).containsExactly("call_1", "call_2")

        resumedStream.emit(StreamEvent.ToolCallComplete(toolCallId = "call_1", output = "42"))
        runCurrent()
        assertThat(vm.uiState.value.sandboxStartingToolCallIds).containsExactly("call_2")

        resumedStream.emit(StreamEvent.ToolCallClosed(toolCallId = "call_2", status = RunStepStatus.CANCELLED))
        runCurrent()
        assertThat(vm.uiState.value.sandboxStartingToolCallIds).isEmpty()
    }

    @Test
    fun `a resume snapshot keeps the boot flag only on a call it still shows running`() = liveRunTest { vm ->
        listOf("call_1", "call_2", "call_3").forEach {
            resumedStream.emit(StreamEvent.SandboxStarting(toolCallId = it))
        }
        runCurrent()

        // call_1 still running, call_2 finished while we were away, call_3 gone from the run.
        resumedStream.emit(
            StreamEvent.Sync(
                aggregatedContent = listOf(codePart(id = "call_1", output = null), codePart(id = "call_2", output = "done")),
            ),
        )
        runCurrent()

        assertThat(vm.uiState.value.activeToolCalls.map { it.id }).containsExactly("call_1", "call_2")
        assertThat(vm.uiState.value.sandboxStartingToolCallIds).containsExactly("call_1")
    }

    @Test
    fun `the finalize clears the boot flags with the live reply`() = liveRunTest { vm ->
        resumedStream.emit(StreamEvent.SandboxStarting(toolCallId = "call_1"))
        runCurrent()
        assertThat(vm.uiState.value.sandboxStartingToolCallIds).isNotEmpty()

        resumedStream.emit(
            StreamEvent.Final(
                requestMessage = null,
                responseMessage = Message(
                    messageId = "assistant-1",
                    conversationId = CONVERSATION_ID,
                    text = "done",
                    isCreatedByUser = false,
                ),
                conversation = null,
            ),
        )
        runCurrent()

        assertThat(vm.uiState.value.isStreaming).isFalse()
        assertThat(vm.uiState.value.sandboxStartingToolCallIds).isEmpty()
    }

    private fun codePart(id: String, output: String?) = MessageContentPart(
        type = ContentType.TOOL_CALL,
        toolCall = AgentToolCall(id = id, name = "execute_code", args = JsonObject(emptyMap()), output = output),
    )

    private fun liveRunTest(body: suspend TestScope.(ChatViewModel) -> Unit) = runTest(testDispatcher) {
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
    }
}
