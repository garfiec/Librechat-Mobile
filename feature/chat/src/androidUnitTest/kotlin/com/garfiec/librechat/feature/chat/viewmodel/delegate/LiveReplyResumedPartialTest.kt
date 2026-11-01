package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.data.repository.EndpointTokenRepository
import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.StreamEvent
import com.garfiec.librechat.feature.chat.util.buildActiveMessagePath
import com.garfiec.librechat.feature.chat.viewmodel.ChatStateHandle
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.MessagesState
import com.garfiec.librechat.feature.chat.viewmodel.StreamingHandle
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * A client that drops its stream (a new chat handing off to its conversation screen, a network drop)
 * makes the server save the reply so far, `unfinished`. The resumed stream's sync frame names that
 * reply, and the saved partial leaves the thread so it doesn't sit above the streaming bubble.
 */
class LiveReplyResumedPartialTest {

    private fun delegate(flow: MutableStateFlow<ChatUiState>, scope: CoroutineScope) = LiveReplyDelegate(
        StreamingHandle(ChatStateHandle(flow, scope)),
        mockk(relaxed = true),
        mockk(relaxed = true),
        mockk<EndpointTokenRepository>(relaxed = true),
    )

    private val user = Message(messageId = "u", conversationId = "c", text = "hi", isCreatedByUser = true)
    private val partial = Message(messageId = "r", conversationId = "c", parentMessageId = "u", unfinished = true)

    @Test
    fun `a partial already on screen leaves when the sync frame names it`() = runTest {
        val messages = listOf(user, partial)
        val flow = MutableStateFlow(
            ChatUiState(
                content = MessagesState(
                    messages = messages,
                    displayMessages = buildActiveMessagePath(messages),
                    isStreaming = true,
                ),
            ),
        )

        delegate(flow, backgroundScope).apply(StreamEvent.Sync(aggregatedContent = emptyList(), responseMessageId = "r"))

        assertThat(flow.value.content.resumedReplyId).isEqualTo("r")
        assertThat(flow.value.displayMessages.map { it.message.messageId }).containsExactly("u")
        // Only the display drops it: the finished reply merges over this row by id.
        assertThat(flow.value.messages).containsExactly(user, partial)
    }
}
