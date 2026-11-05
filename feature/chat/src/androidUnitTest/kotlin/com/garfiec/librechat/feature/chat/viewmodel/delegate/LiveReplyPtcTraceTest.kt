package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.data.repository.EndpointTokenRepository
import com.garfiec.librechat.core.model.StreamEvent
import com.garfiec.librechat.feature.chat.util.PtcCallStatus
import com.garfiec.librechat.feature.chat.util.PtcTrace
import com.garfiec.librechat.feature.chat.viewmodel.ChatStateHandle
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.StreamingHandle
import com.garfiec.librechat.feature.chat.viewmodel.neutralizeStreamingChurn
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The programmatic tool-calling trace on the live reply: folded per code card, kept past the end
 * of the stream for the saved card, cleared when the next turn starts, interrupted by a resume.
 */
class LiveReplyPtcTraceTest {

    private fun delegate(flow: MutableStateFlow<ChatUiState>, scope: CoroutineScope) = LiveReplyDelegate(
        StreamingHandle(ChatStateHandle(flow, scope)),
        mockk(relaxed = true),
        mockk(relaxed = true),
        mockk<EndpointTokenRepository>(relaxed = true),
    )

    private fun ptc(callId: String, status: String, toolCallId: String = "code_1") =
        StreamEvent.PtcToolCall(toolCallId = toolCallId, callId = callId, name = "web_search", status = status)

    @Test
    fun `inner calls fold into their code card's trace`() = runTest {
        val flow = MutableStateFlow(ChatUiState())
        val live = delegate(flow, backgroundScope)

        live.apply(ptc("a", "running"))
        live.apply(ptc("b", "running", toolCallId = "code_2"))
        live.apply(ptc("a", "success"))

        assertThat(flow.value.ptcTraces.keys).containsExactly("code_1", "code_2")
        assertThat(flow.value.ptcTraces.getValue("code_1").entries.single().status).isEqualTo(PtcCallStatus.SUCCESS)
    }

    @Test
    fun `the trace outlives the stream and clears on the next turn`() = runTest {
        val flow = MutableStateFlow(ChatUiState())
        val live = delegate(flow, backgroundScope)
        live.apply(ptc("a", "running"))

        flow.value = flow.value.copy(content = flow.value.content.resetLiveReply(isStreaming = false))
        assertThat(flow.value.ptcTraces).isNotEmpty()

        live.onTurnStarted()
        assertThat(flow.value.ptcTraces).isEmpty()
    }

    @Test
    fun `a resume marks what was running as interrupted`() = runTest {
        val flow = MutableStateFlow(ChatUiState())
        val live = delegate(flow, backgroundScope)
        live.apply(ptc("a", "running"))

        live.apply(StreamEvent.Sync(aggregatedContent = emptyList()))

        assertThat(flow.value.ptcTraces.getValue("code_1").entries.single().status).isEqualTo(PtcCallStatus.INTERRUPTED)
    }

    @Test
    fun `the chrome never sees the trace`() {
        val state = ChatUiState()
        val withTrace = state.copy(content = state.content.copy(ptcTraces = mapOf("code_1" to PtcTrace.EMPTY)))
        assertThat(withTrace.neutralizeStreamingChurn()).isEqualTo(state.neutralizeStreamingChurn())
    }
}
