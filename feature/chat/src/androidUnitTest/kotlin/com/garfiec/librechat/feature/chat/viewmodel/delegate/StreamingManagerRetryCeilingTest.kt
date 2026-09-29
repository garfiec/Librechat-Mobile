package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.common.identity.AccountState
import com.garfiec.librechat.core.common.identity.ActiveAccountProvider
import com.garfiec.librechat.core.common.identity.InMemoryActiveAccountProvider
import com.garfiec.librechat.core.common.network.ConnectivityObserver
import com.garfiec.librechat.core.data.repository.ChatRepository
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.StreamErrorCodes
import com.garfiec.librechat.core.model.StreamEvent
import com.garfiec.librechat.core.model.response.ChatStatusResponse
import com.garfiec.librechat.feature.chat.viewmodel.ChatStateHandle
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.ConversationMetaState
import com.garfiec.librechat.feature.chat.viewmodel.MessagesState
import com.garfiec.librechat.feature.chat.viewmodel.StreamingHandle
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The SSE client's retry ladder ran out. That says nothing about the run: upstream sends no
 * heartbeat, so a live run behind an idle-timing proxy exhausts the ladder while the device is
 * online, and a "check your network" banner there is wrong and a dead end — the connectivity
 * observer that would recover it never fires on a network that never went away. The delegate asks
 * the server instead, as upstream does at its ceiling.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StreamingManagerRetryCeilingTest {

    private val chatRepository = mockk<ChatRepository>(relaxed = true)
    private val reloadConversation = mockk<(String) -> Unit>(relaxed = true)
    private val connectivityObserver = mockk<ConnectivityObserver>(relaxed = true)

    private fun streamingState() = ChatUiState(
        conversation = ConversationMetaState(conversationId = "conv-1"),
        content = MessagesState(
            messages = listOf(
                Message(messageId = "u1", conversationId = "conv-1", text = "hi", isCreatedByUser = true),
            ),
            isStreaming = true,
        ),
    )

    private fun delegateWith(
        scope: TestScope,
        accounts: ActiveAccountProvider = mockk(relaxed = true),
    ): Pair<StreamingManagerDelegate, MutableStateFlow<ChatUiState>> {
        val flow = MutableStateFlow(streamingState())
        every { connectivityObserver.isConnected } returns flowOf(true)
        val delegate = StreamingManagerDelegate(
            handle = StreamingHandle(ChatStateHandle(flow, scope)),
            chatRepository = chatRepository,
            activeAccountProvider = accounts,
            connectivityObserver = connectivityObserver,
            comparisonDelegate = mockk(relaxed = true),
            subagentTraceDelegate = mockk(relaxed = true),
            officePreviewDelegate = mockk(relaxed = true),
            completionDelegate = mockk(relaxed = true),
            queueDelegate = mockk(relaxed = true),
            treeDelegate = mockk(relaxed = true),
            pendingActionDelegate = mockk(relaxed = true),
            steeringDelegate = mockk(relaxed = true),
            emitUserKeyError = {},
            reloadConversation = reloadConversation,
            restoreUnsentInput = { _, _ -> },
            isNewConversation = { false },
            isHandedOffNewChat = { false },
        )
        return delegate to flow
    }

    private val exhausted = StreamEvent.Error(
        message = "Lost the connection to the reply. Please try again.",
        code = StreamErrorCodes.RETRY_EXHAUSTED,
    )

    @Test
    fun `a run still active at the retry ceiling is resumed`() = runTest(StandardTestDispatcher()) {
        coEvery { chatRepository.checkStreamStatus("conv-1", any()) } returns ChatStatusResponse(active = true)
        every { chatRepository.resumeStream("conv-1") } returns flow { awaitCancellation() }
        val (delegate, state) = delegateWith(this)

        delegate.launchStream(flowOf(StreamEvent.ContentDelta(chunk = "half an answer"), exhausted))
        runCurrent()

        verify(exactly = 1) { chatRepository.resumeStream("conv-1") }
        assertThat(state.value.isStreaming).isTrue()
        assertThat(state.value.error).isNull()
        verify(exactly = 0) { reloadConversation(any()) }
        verify(exactly = 0) { connectivityObserver.isConnected }
        delegate.reset()
    }

    @Test
    fun `a run gone at the retry ceiling is refetched with no network banner`() = runTest(StandardTestDispatcher()) {
        coEvery { chatRepository.checkStreamStatus("conv-1", any()) } returns ChatStatusResponse(active = false)
        val (delegate, state) = delegateWith(this)

        delegate.launchStream(flowOf(StreamEvent.ContentDelta(chunk = "half an answer"), exhausted))
        advanceUntilIdle()

        verify(exactly = 1) { reloadConversation("conv-1") }
        assertThat(state.value.isStreaming).isFalse()
        assertThat(state.value.error).isNull()
        verify(exactly = 0) { chatRepository.resumeStream(any()) }
        verify(exactly = 0) { connectivityObserver.isConnected }
    }

    /** Only when the server cannot be reached either is it the network — and then the observer arms. */
    @Test
    fun `an unreachable server at the retry ceiling is a network error`() = runTest(StandardTestDispatcher()) {
        coEvery { chatRepository.checkStreamStatus("conv-1", any()) } throws IllegalStateException("offline")
        val (delegate, state) = delegateWith(this)

        delegate.launchStream(flowOf(StreamEvent.ContentDelta(chunk = "half an answer"), exhausted))
        runCurrent()

        assertThat(state.value.isStreaming).isFalse()
        assertThat(state.value.error).isNotNull()
        assertThat(state.value.streamingContent).isEqualTo("half an answer")
        verify { connectivityObserver.isConnected }
    }

    /** Re-attaches once per turn: a run nothing can reach ends instead of looping forever. */
    @Test
    fun `a second ceiling after re-attaching ends the turn`() = runTest(StandardTestDispatcher()) {
        coEvery { chatRepository.checkStreamStatus("conv-1", any()) } returns ChatStatusResponse(active = true)
        every { chatRepository.resumeStream("conv-1") } returns flowOf(exhausted)
        val (delegate, state) = delegateWith(this)

        delegate.launchStream(flowOf(StreamEvent.ContentDelta(chunk = "half an answer"), exhausted))
        advanceUntilIdle()

        verify(exactly = 1) { chatRepository.resumeStream("conv-1") }
        assertThat(state.value.isStreaming).isFalse()
        assertThat(state.value.error).isNotNull()
    }

    /**
     * The SSE client stops reconnecting on an account switch and completes the flow. Ending that
     * with a reload would read the outgoing account's conversation with the incoming account's
     * credentials.
     */
    @Test
    fun `a stream cut short by an account switch is not reconciled`() = runTest(StandardTestDispatcher()) {
        val accounts = InMemoryActiveAccountProvider(AccountState.Resolved(AccountId("srv:user-a")))
        val (delegate, _) = delegateWith(this, accounts)
        val switched = CompletableDeferred<Unit>()

        delegate.beginStreaming(isEdit = false)
        delegate.launchStream(
            flow {
                emit(StreamEvent.ContentDelta(chunk = "half an answer"))
                switched.await()
            },
        )
        runCurrent()
        accounts.set(AccountId("srv:user-b"))
        switched.complete(Unit)
        runCurrent()

        verify(exactly = 0) { reloadConversation(any()) }
        delegate.reset()
    }

    private val statusExhausted = StreamEvent.Error(
        message = "The server returned an error (HTTP 502). Please try again.",
        code = StreamErrorCodes.STATUS_RETRY_EXHAUSTED,
    )

    /**
     * A proxy 502 on every resume while the run is live: it used to end with the status error and a
     * refetch, and a reply with no text yet then sat as an empty "Thinking" row until the chat was
     * reopened. Adjudicated like the transport ladder, it resumes.
     */
    @Test
    fun `a run still active at a persistent-status ceiling is resumed`() = runTest(StandardTestDispatcher()) {
        coEvery { chatRepository.checkStreamStatus("conv-1", any()) } returns ChatStatusResponse(active = true)
        every { chatRepository.resumeStream("conv-1") } returns flow { awaitCancellation() }
        val (delegate, state) = delegateWith(this)

        delegate.launchStream(flowOf(StreamEvent.ContentDelta(chunk = "half an answer"), statusExhausted))
        runCurrent()

        verify(exactly = 1) { chatRepository.resumeStream("conv-1") }
        assertThat(state.value.isStreaming).isTrue()
        assertThat(state.value.error).isNull()
        verify(exactly = 0) { reloadConversation(any()) }
        delegate.reset()
    }

    /** Status read fails too: the status error is what the user sees, and no observer is armed. */
    @Test
    fun `an unreachable status after a persistent-status ceiling shows the status error`() =
        runTest(StandardTestDispatcher()) {
            coEvery { chatRepository.checkStreamStatus("conv-1", any()) } throws IllegalStateException("502")
            val (delegate, state) = delegateWith(this)

            delegate.launchStream(flowOf(StreamEvent.ContentDelta(chunk = "half an answer"), statusExhausted))
            runCurrent()

            assertThat(state.value.isStreaming).isFalse()
            assertThat(state.value.error).contains("502")
            verify(exactly = 0) { connectivityObserver.isConnected }
        }

    /** The status read is in flight when the account switches: nothing may act on its answer. */
    @Test
    fun `an account switch during the ceiling's status read neither resumes nor reloads`() =
        runTest(StandardTestDispatcher()) {
            val accounts = InMemoryActiveAccountProvider(AccountState.Resolved(AccountId("srv:user-a")))
            val statusGate = CompletableDeferred<Unit>()
            coEvery { chatRepository.checkStreamStatus("conv-1", any()) } coAnswers {
                statusGate.await()
                ChatStatusResponse(active = true)
            }
            val (delegate, _) = delegateWith(this, accounts)

            delegate.beginStreaming(isEdit = false)
            delegate.launchStream(flowOf(StreamEvent.ContentDelta(chunk = "half an answer"), exhausted))
            runCurrent()
            accounts.set(AccountId("srv:user-b"))
            statusGate.complete(Unit)
            runCurrent()

            verify(exactly = 0) { chatRepository.resumeStream(any()) }
            verify(exactly = 0) { reloadConversation(any()) }
            delegate.reset()
        }
}
