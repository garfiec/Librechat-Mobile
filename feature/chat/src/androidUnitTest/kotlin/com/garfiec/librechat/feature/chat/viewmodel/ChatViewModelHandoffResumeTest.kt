package com.garfiec.librechat.feature.chat.viewmodel

import androidx.lifecycle.viewModelScope
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.ChatFontSize
import com.garfiec.librechat.core.data.datastore.ChatHeaderAlignment
import com.garfiec.librechat.core.data.datastore.ChatHeaderContent
import com.garfiec.librechat.core.data.datastore.ContextBarPlacement
import com.garfiec.librechat.core.data.datastore.StarredModelsDisplay
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.response.ChatStatusResponse
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
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
 * A new chat hands its run to the conversation's own screen, which resumes the stream. A fast reply
 * can finish in that gap: the status read still says active, but the stream GET answers 404 because
 * the job is already gone, and the SSE client ends such a flow without an event. The reply is on
 * the server by then, and the only read the screen made landed before it was persisted — so unless
 * the ended resume refetches, the chat shows the user's message alone until it is reopened.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelHandoffResumeTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fixture = ChatViewModelTestFixture()

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
            coEvery { messageRepository.getMessages(CONVERSATION_ID) } returns Result.Success(emptyList<Message>())
            // The status read races the job's cleanup: it still reports the run as active.
            coEvery { chatRepository.checkStreamStatus(eq(CONVERSATION_ID), any()) } returns
                ChatStatusResponse(active = true)
            // The stream GET 404s; SseClient ends that flow with no event at all.
            every { chatRepository.resumeStream(CONVERSATION_ID) } returns emptyFlow()
        }
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a handed-off run that finished before the resume refetches the conversation`() = runTest(testDispatcher) {
        fixture.selectionHandoff.put(
            conversationId = CONVERSATION_ID,
            endpoint = ENDPOINT,
            model = MODEL,
            optimisticUserMessage = Message(messageId = "user-1", conversationId = "", text = "hi", isCreatedByUser = true),
        )
        val vm = fixture.build(defaultDispatcher = testDispatcher, initialConversationId = CONVERSATION_ID)
        try {
            advanceUntilIdle()

            // One read on open, and one after the resume found the stream gone.
            coVerify(exactly = 2) { fixture.messageRepository.getMessages(CONVERSATION_ID) }
            assertThat(vm.uiState.value.isStreaming).isFalse()
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
