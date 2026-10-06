package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.common.identity.ActiveAccountProvider
import com.garfiec.librechat.core.common.network.ConnectivityObserver
import com.garfiec.librechat.core.data.repository.ChatRepository
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.StreamEvent
import com.garfiec.librechat.core.model.usage.TokenUsage
import com.garfiec.librechat.feature.chat.viewmodel.ChatStateHandle
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * `on_token_usage` handling for the breakdown's Totals. Every billed call counts, buckets included
 * (summary passes, subagent runs, activity labels), and each call counts once: a resume replays
 * calls already seen live, both as live events and as the sync frame's backfill.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StreamingManagerTokenUsageTest {

    private val chatRepository = mockk<ChatRepository>(relaxed = true)

    private fun delegateWith(
        scope: TestScope,
    ): Pair<StreamingManagerDelegate, MutableStateFlow<ChatUiState>> {
        val flow = MutableStateFlow(
            ChatUiState(
                conversation = ConversationMetaState(conversationId = "conv-1"),
                content = MessagesState(
                    messages = listOf(
                        Message(messageId = "u1", conversationId = "conv-1", isCreatedByUser = true),
                    ),
                    isStreaming = true,
                ),
            ),
        )
        val root = ChatStateHandle(flow, scope)
        val connectivity = mockk<ConnectivityObserver>(relaxed = true)
        every { connectivity.isConnected } returns flowOf(true)
        val delegate = StreamingManagerDelegate(
            handle = StreamingHandle(root),
            chatRepository = chatRepository,
            activeAccountProvider = mockk<ActiveAccountProvider>(relaxed = true),
            connectivityObserver = connectivity,
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

    private fun usage(seq: Int, input: Int, output: Int, bucket: String? = null) = TokenUsage(
        inputTokens = input,
        outputTokens = output,
        provider = "anthropic",
        usageType = bucket,
        runId = "run-1",
        seq = seq,
    )

    @Test
    fun `a usage event folds into the pending totals`() = runTest(StandardTestDispatcher()) {
        val events = Channel<StreamEvent>(Channel.UNLIMITED)
        val (delegate, flow) = delegateWith(this)
        delegate.launchStream(events.receiveAsFlow())

        events.send(StreamEvent.TokenUsageUpdate(usage(seq = 1, input = 4000, output = 900)))
        runCurrent()

        assertThat(flow.value.pendingUsage.usage.input).isEqualTo(4000)
        assertThat(flow.value.pendingUsage.usage.output).isEqualTo(900)
        events.close()
        advanceUntilIdle()
    }

    /** A summary pass or activity label is a billed call too; web's Totals count it. */
    @Test
    fun `bucketed calls count toward the totals, subagent ones also on their own`() =
        runTest(StandardTestDispatcher()) {
            val events = Channel<StreamEvent>(Channel.UNLIMITED)
            val (delegate, flow) = delegateWith(this)
            delegate.launchStream(events.receiveAsFlow())

            events.send(StreamEvent.TokenUsageUpdate(usage(seq = 1, input = 4000, output = 900)))
            events.send(StreamEvent.TokenUsageUpdate(usage(seq = 2, input = 30, output = 8, bucket = "activity-label")))
            events.send(StreamEvent.TokenUsageUpdate(usage(seq = 3, input = 200, output = 50, bucket = "subagent")))
            runCurrent()

            assertThat(flow.value.pendingUsage.usage.input).isEqualTo(4230)
            assertThat(flow.value.pendingUsage.usage.output).isEqualTo(958)
            assertThat(flow.value.pendingUsage.subagent.input).isEqualTo(200)
            events.close()
            advanceUntilIdle()
        }

    @Test
    fun `a call replayed live and in a resume backfill is counted once`() = runTest(StandardTestDispatcher()) {
        val events = Channel<StreamEvent>(Channel.UNLIMITED)
        val (delegate, flow) = delegateWith(this)
        delegate.launchStream(events.receiveAsFlow())

        events.send(StreamEvent.TokenUsageUpdate(usage(seq = 1, input = 4000, output = 900)))
        events.send(StreamEvent.TokenUsageUpdate(usage(seq = 1, input = 4000, output = 900)))
        events.send(
            StreamEvent.UsageBackfill(
                listOf(usage(seq = 1, input = 4000, output = 900), usage(seq = 2, input = 100, output = 10)),
            ),
        )
        runCurrent()

        assertThat(flow.value.pendingUsage.usage.input).isEqualTo(4100)
        assertThat(flow.value.pendingUsage.usage.output).isEqualTo(910)
        events.close()
        advanceUntilIdle()
    }

    /** No final frame means no reply to attribute the usage to, unless the run re-attaches. */
    @Test
    fun `a non-network error drops the pending usage, a network error keeps it`() =
        runTest(StandardTestDispatcher()) {
            val failed = Channel<StreamEvent>(Channel.UNLIMITED)
            val (delegate, flow) = delegateWith(this)
            delegate.launchStream(failed.receiveAsFlow())
            failed.send(StreamEvent.TokenUsageUpdate(usage(seq = 1, input = 4000, output = 900)))
            failed.send(StreamEvent.Error(message = "boom"))
            failed.close()
            advanceUntilIdle()
            assertThat(flow.value.pendingUsage.isEmpty).isTrue()

            val dropped = Channel<StreamEvent>(Channel.UNLIMITED)
            val (networkDelegate, networkFlow) = delegateWith(this)
            networkDelegate.launchStream(dropped.receiveAsFlow())
            dropped.send(StreamEvent.TokenUsageUpdate(usage(seq = 1, input = 4000, output = 900)))
            dropped.send(StreamEvent.Error(message = "offline", isNetworkError = true))
            dropped.close()
            advanceUntilIdle()
            assertThat(networkFlow.value.pendingUsage.usage.input).isEqualTo(4000)
        }
}
