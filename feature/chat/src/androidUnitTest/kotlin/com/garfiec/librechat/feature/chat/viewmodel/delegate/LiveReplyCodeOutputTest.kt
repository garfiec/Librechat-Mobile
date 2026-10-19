package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.data.repository.EndpointTokenRepository
import com.garfiec.librechat.core.model.RunStepStatus
import com.garfiec.librechat.core.model.StreamEvent
import com.garfiec.librechat.core.model.WorkspaceChange
import com.garfiec.librechat.feature.chat.viewmodel.ActiveToolCall
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
 * What the live reply keeps from a code-interpreter call: the args the completion reports, the
 * duration the closure reports, and every field of the files it produced, including a download
 * fallback that has no file_id. The events mirror the shapes `SseEventMapper` produces from
 * upstream's `callbacks.js`; see `SseEventMapperTest` for the wire fixtures and their sources.
 */
class LiveReplyCodeOutputTest {

    private fun delegate(flow: MutableStateFlow<ChatUiState>, scope: CoroutineScope) = LiveReplyDelegate(
        StreamingHandle(ChatStateHandle(flow, scope)),
        mockk(relaxed = true),
        mockk(relaxed = true),
        mockk<EndpointTokenRepository>(relaxed = true),
    )

    private fun start() = StreamEvent.ToolCallStart(toolCallId = "call_1", toolName = "execute_code", input = "")

    @Test
    fun `the completion's args replace the empty args the step started with`() = runTest {
        val flow = MutableStateFlow(ChatUiState())
        val live = delegate(flow, backgroundScope)

        live.apply(start())
        live.apply(
            StreamEvent.ToolCallComplete(
                toolCallId = "call_1",
                output = "stdout:\n42",
                input = """{"lang":"py","code":"print(42)"}""",
            ),
        )

        val call = flow.value.activeToolCalls.single()
        assertThat(call.isComplete).isTrue()
        assertThat(call.input).isEqualTo("""{"lang":"py","code":"print(42)"}""")
        assertThat(call.output).isEqualTo("stdout:\n42")
        assertThat(call.durationMs).isNull()
    }

    @Test
    fun `a completion without args keeps the start's input`() = runTest {
        val flow = MutableStateFlow(ChatUiState())
        val live = delegate(flow, backgroundScope)

        live.apply(StreamEvent.ToolCallStart(toolCallId = "call_1", toolName = "dalle", input = """{"prompt":"a cat"}"""))
        live.apply(StreamEvent.ToolCallComplete(toolCallId = "call_1", output = "done"))

        assertThat(flow.value.activeToolCalls.single().input).isEqualTo("""{"prompt":"a cat"}""")
    }

    @Test
    fun `the closure's duration lands on the tool call`() = runTest {
        val flow = MutableStateFlow(ChatUiState())
        val live = delegate(flow, backgroundScope)

        live.apply(start())
        live.apply(StreamEvent.ToolCallComplete(toolCallId = "call_1", output = "ok"))
        live.apply(StreamEvent.ToolCallClosed(toolCallId = "call_1", status = RunStepStatus.COMPLETED, durationMs = 1_250))

        val call = flow.value.activeToolCalls.single()
        assertThat(call.durationMs).isEqualTo(1_250)
        assertThat(call.closedStatus).isEqualTo(RunStepStatus.COMPLETED)
    }

    @Test
    fun `the comparison panes apply the same completion and closure`() {
        val call = ActiveToolCall(id = "call_1", name = "execute_code", input = "")

        val completed = call.completedBy(StreamEvent.ToolCallComplete("call_1", output = "ok", input = "{}"))
        val closed = completed.closedBy(StreamEvent.ToolCallClosed("call_1", RunStepStatus.COMPLETED, durationMs = 7))

        assertThat(closed).isEqualTo(
            call.copy(isComplete = true, output = "ok", input = "{}", closedStatus = RunStepStatus.COMPLETED, durationMs = 7),
        )
    }

    @Test
    fun `a closure without a duration keeps the call's duration unknown`() {
        val closed = ActiveToolCall(id = "call_1", name = "execute_code")
            .closedBy(StreamEvent.ToolCallClosed("call_1", RunStepStatus.CANCELLED))

        assertThat(closed.durationMs).isNull()
    }

    @Test
    fun `sandbox starting flags its call until the call completes`() = runTest {
        val flow = MutableStateFlow(ChatUiState())
        val live = delegate(flow, backgroundScope)
        live.apply(start())

        live.apply(StreamEvent.SandboxStarting(toolCallId = "call_1", runId = "run_1"))
        assertThat(flow.value.sandboxStartingToolCallIds).containsExactly("call_1")

        live.apply(StreamEvent.ToolCallComplete(toolCallId = "call_1", output = "ok"))
        assertThat(flow.value.sandboxStartingToolCallIds).isEmpty()
    }

    @Test
    fun `a closure without a completion also ends the boot`() = runTest {
        val flow = MutableStateFlow(ChatUiState())
        val live = delegate(flow, backgroundScope)
        live.apply(start())
        live.apply(StreamEvent.SandboxStarting(toolCallId = "call_1"))

        live.apply(StreamEvent.ToolCallClosed(toolCallId = "call_1", status = RunStepStatus.CANCELLED))

        assertThat(flow.value.sandboxStartingToolCallIds).isEmpty()
    }

    @Test
    fun `each code call in a step carries its own flag`() = runTest {
        val flow = MutableStateFlow(ChatUiState())
        val live = delegate(flow, backgroundScope)
        live.apply(StreamEvent.SandboxStarting(toolCallId = "call_1"))
        live.apply(StreamEvent.SandboxStarting(toolCallId = "call_2"))

        live.apply(StreamEvent.ToolCallComplete(toolCallId = "call_1", output = "ok"))

        assertThat(flow.value.sandboxStartingToolCallIds).containsExactly("call_2")
    }

    @Test
    fun `ending the live reply clears every flag`() {
        val state = MessagesState(sandboxStartingToolCallIds = setOf("call_1"))

        assertThat(state.resetLiveReply(isStreaming = false).sandboxStartingToolCallIds).isEmpty()
    }

    @Test
    fun `a streamed code output keeps its agent, workspace change, size and ids`() = runTest {
        val flow = MutableStateFlow(ChatUiState())
        val change = WorkspaceChange(profile = "stateful", operation = "created", path = "report.md")

        delegate(flow, backgroundScope).apply(
            StreamEvent.AttachmentCreated(
                fileId = "file_1",
                filename = "report.md",
                type = "text/markdown",
                filepath = "/uploads/u/report.md",
                toolCallId = "call_1",
                messageId = "msg_1",
                conversationId = "convo_1",
                agentId = "agent_a",
                bytes = 512,
                workspaceChange = change,
            ),
        )

        val att = flow.value.streamingAttachments.single()
        assertThat(att.fileId).isEqualTo("file_1")
        assertThat(att.agentId).isEqualTo("agent_a")
        assertThat(att.workspaceChange).isEqualTo(change)
        assertThat(att.bytes).isEqualTo(512)
        assertThat(att.messageId).isEqualTo("msg_1")
        assertThat(att.conversationId).isEqualTo("convo_1")
    }

    @Test
    fun `a download fallback keeps a null file id and its expiry`() = runTest {
        val flow = MutableStateFlow(ChatUiState())
        val live = delegate(flow, backgroundScope)

        listOf("id_1", "id_2").forEach { id ->
            live.apply(
                StreamEvent.AttachmentCreated(
                    fileId = null,
                    filename = "big.csv",
                    type = null,
                    filepath = "/api/files/code/download/sess_1/$id",
                    toolCallId = "call_1",
                    agentId = "agent_a",
                    expiresAt = 1_760_000_000_000,
                ),
            )
        }

        val atts = flow.value.streamingAttachments
        assertThat(atts).hasSize(2)
        assertThat(atts.map { it.fileId }).containsExactly(null, null)
        assertThat(atts.map { it.type }).containsExactly(null, null)
        assertThat(atts.first().expiresAt).isEqualTo(1_760_000_000_000)
    }
}
