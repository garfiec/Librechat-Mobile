package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.common.network.ConnectivityObserver
import com.garfiec.librechat.core.data.repository.ChatRepository
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * `resumeActiveStreamIfNeeded` may start from an ended session — that is where every server-admitted
 * queued turn arrives — so "ended" is no longer read as stale. These pin what still must be: an
 * attach whose status read is overtaken by a newer local send, or by the end of the very session it
 * started from, does nothing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StreamingManagerAttachStaleTest {

    private val chatRepository = mockk<ChatRepository>(relaxed = true)
    private val connectivityObserver = mockk<ConnectivityObserver>(relaxed = true)
    private val statusGate = CompletableDeferred<Unit>()

    private fun delegateWith(scope: TestScope, streaming: Boolean): Pair<StreamingManagerDelegate, MutableStateFlow<ChatUiState>> {
        val flow = MutableStateFlow(
            ChatUiState(
                conversation = ConversationMetaState(conversationId = "conv-1"),
                content = MessagesState(isStreaming = streaming),
            ),
        )
        every { connectivityObserver.isConnected } returns flowOf(true)
        coEvery { chatRepository.checkStreamStatus("conv-1", any()) } coAnswers {
            statusGate.await()
            ChatStatusResponse(active = true)
        }
        every { chatRepository.resumeStream("conv-1") } returns flow { awaitCancellation() }
        val root = ChatStateHandle(flow, scope)
        val delegate = StreamingManagerDelegate(
            handle = StreamingHandle(root),
            chatRepository = chatRepository,
            activeAccountProvider = mockk(relaxed = true),
            connectivityObserver = connectivityObserver,
            comparisonDelegate = mockk(relaxed = true),
            liveReply = LiveReplyDelegate(StreamingHandle(root), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true)),
            completionDelegate = mockk(relaxed = true),
            queueDelegate = mockk(relaxed = true),
            pendingActionDelegate = mockk(relaxed = true),
            steeringDelegate = mockk(relaxed = true),
            host = mockk(relaxed = true),
        )
        return delegate to flow
    }

    /** Ends the delegate's current session the way a finished turn does. */
    private fun TestScope.finishATurn(delegate: StreamingManagerDelegate) {
        delegate.beginStreaming(isEdit = false)
        delegate.launchStream(flowOf(StreamEvent.Final()))
        advanceUntilIdle()
    }

    @Test
    fun `an attach from an ended session resumes the run`() = runTest(StandardTestDispatcher()) {
        val (delegate, state) = delegateWith(this, streaming = true)
        finishATurn(delegate)

        delegate.resumeActiveStreamIfNeeded("conv-1")
        runCurrent()
        statusGate.complete(Unit)
        runCurrent()

        verify(exactly = 1) { chatRepository.resumeStream("conv-1") }
        assertThat(state.value.isStreaming).isTrue()
        delegate.reset()
    }

    /** The user sends while the attach's status read is out: the send owns the screen. */
    @Test
    fun `a local send during the attach's status read wins`() = runTest(StandardTestDispatcher()) {
        val (delegate, _) = delegateWith(this, streaming = true)
        finishATurn(delegate)

        delegate.resumeActiveStreamIfNeeded("conv-1")
        runCurrent()
        delegate.beginStreaming(isEdit = false)
        statusGate.complete(Unit)
        runCurrent()

        verify(exactly = 0) { chatRepository.resumeStream(any()) }
        delegate.reset()
    }

    /** The session the attach started from ends during the read: resuming would resurrect it. */
    @Test
    fun `a live session that ends during the attach's status read is not resumed`() =
        runTest(StandardTestDispatcher()) {
            val (delegate, _) = delegateWith(this, streaming = true)
            val events = Channel<StreamEvent>(Channel.UNLIMITED)
            delegate.beginStreaming(isEdit = false)
            delegate.launchStream(events.receiveAsFlow())
            runCurrent()

            delegate.resumeActiveStreamIfNeeded("conv-1")
            runCurrent()
            events.send(StreamEvent.Final(aborted = true))
            runCurrent()
            statusGate.complete(Unit)
            runCurrent()

            verify(exactly = 0) { chatRepository.resumeStream(any()) }
            delegate.reset()
        }
}
