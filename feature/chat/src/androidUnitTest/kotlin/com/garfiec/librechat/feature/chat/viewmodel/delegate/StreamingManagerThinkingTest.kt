package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.common.network.ConnectivityObserver
import com.garfiec.librechat.core.data.repository.ChatRepository
import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.StreamEvent
import com.garfiec.librechat.core.model.content.MessageContentPart
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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The live reply's reasoning goes to its own buffer, which the streaming bubble shows in a
 * collapsed Thinking block. It used to share the text buffer, so it streamed as body text under the
 * sender label — and a resume's sync frame, reading only `text`, dropped it altogether.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StreamingManagerThinkingTest {

    private val chatRepository = mockk<ChatRepository>(relaxed = true)
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
            ),
        )
        every { connectivityObserver.isConnected } returns flowOf(true)
        val delegate = StreamingManagerDelegate(
            handle = StreamingHandle(ChatStateHandle(flow, scope)),
            chatRepository = chatRepository,
            activeAccountProvider = mockk(relaxed = true),
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
            reloadConversation = {},
            reloadRestoringUnsaved = { _, _ -> },
            restoreUnsentInput = { _, _ -> },
            isNewConversation = { false },
            isHandedOffNewChat = { false },
        )
        return delegate to flow
    }

    @Test
    fun `thinking streams to its own buffer, not the body`() = runTest(StandardTestDispatcher()) {
        val events = Channel<StreamEvent>(Channel.UNLIMITED)
        val (delegate, state) = delegateWith(this)
        delegate.beginStreaming(isEdit = false)
        delegate.launchStream(events.receiveAsFlow())

        events.send(StreamEvent.ThinkingDelta(chunk = "Weighing the options. "))
        events.send(StreamEvent.ContentDelta(chunk = "Alpine meadows"))
        runCurrent()
        advanceTimeBy(FLUSH_MS)
        runCurrent()

        assertThat(state.value.streamingThinking).isEqualTo("Weighing the options. ")
        assertThat(state.value.streamingContent).isEqualTo("Alpine meadows")
        delegate.reset()
    }

    /** A resume's snapshot carries the reasoning in THINK parts' `think` field. */
    @Test
    fun `a sync frame restores the thinking apart from the text`() = runTest(StandardTestDispatcher()) {
        val events = Channel<StreamEvent>(Channel.UNLIMITED)
        val (delegate, state) = delegateWith(this)
        delegate.beginStreaming(isEdit = false)
        delegate.launchStream(events.receiveAsFlow())

        events.send(
            StreamEvent.Sync(
                aggregatedContent = listOf(
                    MessageContentPart(type = ContentType.THINK, think = "Weighing the options. "),
                    MessageContentPart(type = ContentType.TEXT, text = "Alpine meadows"),
                ),
            ),
        )
        runCurrent()
        advanceTimeBy(FLUSH_MS)
        runCurrent()

        assertThat(state.value.streamingThinking).isEqualTo("Weighing the options. ")
        assertThat(state.value.streamingContent).isEqualTo("Alpine meadows")
        delegate.reset()
    }

    private companion object {
        /** Past the streaming updater's flush interval. */
        const val FLUSH_MS = 500L
    }
}
