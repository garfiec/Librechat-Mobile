package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.data.repository.EndpointTokenRepository
import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.StreamEvent
import com.garfiec.librechat.core.model.content.AgentToolCall
import com.garfiec.librechat.core.model.content.MessageContentPart
import com.garfiec.librechat.feature.chat.viewmodel.ActiveToolCall
import com.garfiec.librechat.feature.chat.viewmodel.ChatStateHandle
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.StreamingHandle
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * A tool call the server rejected at schema validation — e.g. an `ask_user_question`
 * batch of more than four — must reach the live card as failed, on the live path and on a resume,
 * so it renders the way the persisted message does instead of as unanswered questions.
 */
class LiveReplyValidationErrorTest {

    private fun delegate(flow: MutableStateFlow<ChatUiState>, scope: CoroutineScope) = LiveReplyDelegate(
        StreamingHandle(ChatStateHandle(flow, scope)),
        mockk(relaxed = true),
        mockk(relaxed = true),
        mockk<EndpointTokenRepository>(relaxed = true),
    )

    private val rejected = StreamEvent.ToolCallComplete(
        toolCallId = "call_1",
        output = "Error: Array must contain at most 4 element(s)\n Please fix your mistakes.",
        inputValidationError = true,
    )

    @Test
    fun `a live completion rejected at validation marks the call`() = runTest {
        val flow = MutableStateFlow(ChatUiState())
        val live = delegate(flow, backgroundScope)

        live.apply(StreamEvent.ToolCallStart(toolCallId = "call_1", toolName = "ask_user_question", input = ""))
        live.apply(rejected)

        val call = flow.value.activeToolCalls.single()
        assertThat(call.isComplete).isTrue()
        assertThat(call.inputValidationError).isTrue()
    }

    @Test
    fun `a resume snapshot carries the flag from the aggregated part`() = runTest {
        val flow = MutableStateFlow(ChatUiState())
        val live = delegate(flow, backgroundScope)

        live.apply(
            StreamEvent.Sync(
                aggregatedContent = listOf(
                    MessageContentPart(
                        type = ContentType.TOOL_CALL,
                        toolCall = AgentToolCall(
                            id = "call_1",
                            name = "ask_user_question",
                            output = "Error: …",
                            inputValidationError = true,
                        ),
                    ),
                    MessageContentPart(
                        type = ContentType.TOOL_CALL,
                        toolCall = AgentToolCall(id = "call_2", name = "search", output = "ok"),
                    ),
                ),
            ),
        )

        assertThat(flow.value.activeToolCalls.map { it.inputValidationError }).containsExactly(true, false).inOrder()
    }

    /** Both comparison panes complete their calls through [completedBy]. */
    @Test
    fun `the comparison panes carry the flag too`() {
        val call = ActiveToolCall(id = "call_1", name = "ask_user_question")

        assertThat(call.completedBy(rejected).inputValidationError).isTrue()
        assertThat(call.completedBy(StreamEvent.ToolCallComplete("call_1", output = "{}")).inputValidationError).isFalse()
    }
}
