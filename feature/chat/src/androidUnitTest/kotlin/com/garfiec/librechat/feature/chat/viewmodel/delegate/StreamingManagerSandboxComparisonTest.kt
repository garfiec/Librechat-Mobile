package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.common.network.ConnectivityObserver
import com.garfiec.librechat.core.data.repository.ChatRepository
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.StreamEvent
import com.garfiec.librechat.feature.chat.viewmodel.ChatStateHandle
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.ComparisonHandle
import com.garfiec.librechat.feature.chat.viewmodel.ComparisonState
import com.garfiec.librechat.feature.chat.viewmodel.ConversationMetaState
import com.garfiec.librechat.feature.chat.viewmodel.MessagesState
import com.garfiec.librechat.feature.chat.viewmodel.StreamingHandle
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * In comparison mode the completion that ends a sandbox boot is consumed by the comparison
 * delegate, so the standard path never sees it. The boot flag must still come off, or the card
 * says "Starting sandbox" over a finished call for the rest of the run.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StreamingManagerSandboxComparisonTest {

    private val connectivityObserver = mockk<ConnectivityObserver>(relaxed = true)

    private fun delegateWith(scope: TestScope): Pair<StreamingManagerDelegate, MutableStateFlow<ChatUiState>> {
        val flow = MutableStateFlow(
            ChatUiState(
                conversation = ConversationMetaState(conversationId = "conv-1"),
                content = MessagesState(
                    messages = listOf(
                        Message(messageId = "u1", conversationId = "conv-1", text = "hi", isCreatedByUser = true),
                    ),
                    isStreaming = true,
                ),
                comparisonState = ComparisonState(isEnabled = true),
            ),
        )
        every { connectivityObserver.isConnected } returns flowOf(true)
        val root = ChatStateHandle(flow, scope)
        val delegate = StreamingManagerDelegate(
            handle = StreamingHandle(root),
            chatRepository = mockk<ChatRepository>(relaxed = true),
            activeAccountProvider = mockk(relaxed = true),
            connectivityObserver = connectivityObserver,
            comparisonDelegate = ComparisonModeDelegate(
                handle = ComparisonHandle(root),
                messageRepository = mockk(relaxed = true),
                reloadConversation = {},
            ),
            liveReply = LiveReplyDelegate(StreamingHandle(root), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true)),
            completionDelegate = mockk(relaxed = true),
            queueDelegate = mockk(relaxed = true),
            pendingActionDelegate = mockk(relaxed = true),
            steeringDelegate = mockk(relaxed = true),
            host = mockk(relaxed = true),
        )
        return delegate to flow
    }

    @Test
    fun `a completion consumed by comparison mode clears the sandbox boot`() = runTest(StandardTestDispatcher()) {
        val events = Channel<StreamEvent>(Channel.UNLIMITED)
        val (delegate, state) = delegateWith(this)
        delegate.beginStreaming(isEdit = false)
        delegate.launchStream(events.receiveAsFlow())

        events.send(StreamEvent.ToolCallStart(toolCallId = "call_1", toolName = "execute_code", input = "", agentId = "agent_a"))
        events.send(StreamEvent.SandboxStarting(toolCallId = "call_1", runId = "run_1"))
        runCurrent()
        assertThat(state.value.sandboxStartingToolCallIds).containsExactly("call_1")

        events.send(StreamEvent.ToolCallComplete(toolCallId = "call_1", output = "ok", agentId = "agent_a"))
        runCurrent()

        assertThat(state.value.sandboxStartingToolCallIds).isEmpty()
        delegate.reset()
    }
}
