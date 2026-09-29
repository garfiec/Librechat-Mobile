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
import com.garfiec.librechat.core.model.response.ChatStatusResponse
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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
 * Asserted on what is DISPLAYED, not on whether a fetch was made: "the refetch happened" is exactly
 * what a device showed while the chat still read the user's message alone. The message store here
 * behaves like the real one — `getMessages` writes what the server returned into the flow
 * `observeMessages` reads, as the repository's Room upsert does — so a refetch that is made but
 * never rendered fails the test. And like the real server it re-mints the user message's id —
 * it persists under its own id, not the one the client sent — which is what a same-id fake hides.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelHandoffResumeTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fixture = ChatViewModelTestFixture()

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

    private companion object {
        const val CONVERSATION_ID = "conv-1"
        const val ENDPOINT = "anthropic"
        const val MODEL = "claude-haiku-4-5"
        const val NO_PARENT = "00000000-0000-0000-0000-000000000000"
    }
}
