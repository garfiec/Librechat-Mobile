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
import com.garfiec.librechat.core.network.sse.SseClient
import com.garfiec.librechat.core.network.sse.SseHttpTransport
import com.garfiec.librechat.feature.chat.viewmodel.delegate.PlatformFileHandler
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.url
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.writeStringUtf8
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * A stream whose body ends without the run's end, while the run is still live — a proxy or CDN
 * idle timeout during a human-review pause, where upstream sends nothing at all while it waits.
 *
 * The real [SseClient] runs here over a mocked transport, because the bug is in how the two compose:
 * a stubbed event flow can only say "the flow completed", which is exactly the signal that was
 * misread. Read as the run's end, the ViewModel reconciled — cleared the partial and the question
 * card, reloaded before anything was saved — and the user was left with no reply and nothing to
 * answer. The stream must resume instead and leave both on screen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelCleanEndResumeTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fixture = ChatViewModelTestFixture()
    private val fileHandler = mockk<PlatformFileHandler>(relaxed = true)
    private val stored = MutableStateFlow<List<Message>>(emptyList())

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

    /** The resume's body: held open, as a live run's is, after its sync frame. */
    private val resumed = ByteChannel(autoFlush = true)
    private val requests = mutableListOf<HttpRequestData>()

    private fun sseClient(): SseClient {
        val engine = MockEngine(
            MockEngineConfig().apply {
                // Inline on the test scheduler, so virtual time cannot run past a request still in
                // flight into the parser's stall watchdog.
                dispatcher = Dispatchers.Unconfined
                addHandler { request ->
                    requests += request
                    if (request.url.parameters["resume"] == "true") {
                        respond(resumed as ByteReadChannel, HttpStatusCode.OK)
                    } else {
                        // The first connection: the run starts, streams a partial, asks the user a
                        // question — and then the connection is closed cleanly underneath it.
                        respond(CREATED + DELTA + QUESTION, HttpStatusCode.OK)
                    }
                }
            },
        )
        return SseClient(
            json = Json { ignoreUnknownKeys = true },
            transport = SseHttpTransport(HttpClient(engine) { defaultRequest { url(SERVER) } }),
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fixture.stubDefaults()
        with(fixture) {
            every { configRepository.endpointConfigs } returns MutableStateFlow(mapOf(ENDPOINT to EndpointConfig()))
            every { configRepository.availableModels } returns MutableStateFlow(mapOf(ENDPOINT to listOf(MODEL)))
            every { settingsDataStore.selectedMcpServers } returns flowOf(emptySet())
            every { settingsDataStore.enabledTools } returns flowOf(emptySet())
            every { serverDataStore.currentUrlFlow } returns flowOf(SERVER)
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
            coEvery { chatRepository.checkStreamStatus(eq(CONVERSATION_ID), any()) } returns
                ChatStatusResponse(active = false)
        }
    }

    @After
    fun tearDown() {
        resumed.cancel(null)
        Dispatchers.resetMain()
    }

    @Test
    fun `a live run whose connection closes cleanly is resumed, keeping the partial and the question`() =
        runTest(testDispatcher) {
            stored.value = listOf(user, reply)
            coEvery { fixture.messageRepository.getMessages(CONVERSATION_ID) } returns
                Result.Success(listOf(user, reply))
            val client = sseClient()
            every {
                fixture.chatRepository.startChat(
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                    any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                    any(),
                )
            } answers { client.connect(STREAM_PATH) }
            fixture.selectionHandoff.put(conversationId = CONVERSATION_ID, endpoint = ENDPOINT, model = MODEL)
            val vm = fixture.build(defaultDispatcher = testDispatcher, initialConversationId = CONVERSATION_ID)
            try {
                advanceUntilIdle()
                var fetchesAfterSend = 0
                coEvery { fixture.messageRepository.getMessages(CONVERSATION_ID) } coAnswers {
                    fetchesAfterSend++
                    Result.Success(listOf(user, reply))
                }
                // The resume answers as a live run does: its snapshot so far, then silence.
                resumed.writeStringUtf8(SYNC)

                vm.onInputChanged("Twenty sentences, please")
                vm.sendMessage()
                // Bounded, well under the parser's 120s stall watchdog: the resumed body stays open.
                advanceTimeBy(SETTLE_MS)
                runCurrent()

                val state = vm.uiState.value
                assertThat(state.isStreaming).isTrue()
                assertThat(state.streamingContent).startsWith("Alpine meadows on")
                assertThat(state.pendingAction?.actionId).isEqualTo("act_2")
                assertThat(state.error).isNull()
                assertThat(fetchesAfterSend).isEqualTo(0)
                assertThat(requests).hasSize(2)
                assertThat(requests[1].url.parameters["resume"]).isEqualTo("true")
            } finally {
                vm.viewModelScope.cancel()
            }
        }

    private companion object {
        const val SERVER = "https://chat.example.com"
        const val CONVERSATION_ID = "conv-1"
        const val STREAM_PATH = "api/agents/chat/stream/conv-1"
        const val ENDPOINT = "anthropic"
        const val MODEL = "claude-haiku-4-5"
        const val SETTLE_MS = 10_000L
        const val NO_PARENT = "00000000-0000-0000-0000-000000000000"

        const val CREATED =
            "data: {\"created\":true,\"message\":{\"conversationId\":\"conv-1\"," +
                "\"messageId\":\"user-2\",\"parentMessageId\":\"assistant-1\"}}\n\n"
        const val DELTA =
            "data: {\"event\":\"on_message_delta\",\"data\":{\"id\":\"s1\"," +
                "\"delta\":{\"content\":[{\"type\":\"text\",\"text\":\"Alpine meadows on \"}]}}}\n\n"
        const val QUESTION =
            "data: {\"event\":\"on_pending_action\",\"data\":{\"actionId\":\"act_2\",\"streamId\":\"conv-1\"," +
                "\"payload\":{\"type\":\"ask_user_question\",\"question\":{\"question\":\"Which slope?\"," +
                "\"multiSelect\":false,\"options\":[{\"label\":\"North\",\"value\":\"n\"}]}}}}\n\n"
        const val SYNC =
            "data: {\"sync\":true,\"resumeState\":{\"aggregatedContent\":" +
                "[{\"type\":\"text\",\"text\":\"Alpine meadows on \"}]}}\n\n"
    }
}
