package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.data.repository.EndpointTokenRepository
import com.garfiec.librechat.core.model.ContentType
import com.garfiec.librechat.core.model.StreamEvent
import com.garfiec.librechat.core.model.SubagentPhase
import com.garfiec.librechat.core.model.content.AgentToolCall
import com.garfiec.librechat.core.model.content.MessageContentPart
import com.garfiec.librechat.feature.chat.viewmodel.ChatStateHandle
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.StreamingHandle
import com.garfiec.librechat.feature.chat.viewmodel.SubagentHandle
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test

/** The run step that waits on an MCP sign-in (`buildMCPAuthRunStep*Event` upstream). */
class LiveReplyMcpSignInTest {

    private fun delegate(flow: MutableStateFlow<ChatUiState>, scope: CoroutineScope) = LiveReplyDelegate(
        StreamingHandle(ChatStateHandle(flow, scope)),
        mockk(relaxed = true),
        mockk(relaxed = true),
        mockk<EndpointTokenRepository>(relaxed = true),
    )

    private val id = "u1:github:123"

    @Test
    fun `the auth delta puts the sign-in on the announced call, and completion takes it off`() = runTest {
        val flow = MutableStateFlow(ChatUiState())
        val live = delegate(flow, backgroundScope)

        live.apply(StreamEvent.ToolCallStart(toolCallId = id, toolName = "oauth_mcp_github", input = ""))
        live.apply(
            StreamEvent.ToolCallAuthRequired(
                toolCallId = id,
                authUrl = "https://idp/authorize",
                serverName = "github",
                expiresAt = 1_000L,
            ),
        )

        val waiting = flow.value.activeToolCalls.single()
        assertThat(waiting.authUrl).isEqualTo("https://idp/authorize")
        assertThat(waiting.authServerName).isEqualTo("github")
        assertThat(waiting.authExpiresAt).isEqualTo(1_000L)
        assertThat(waiting.isComplete).isFalse()

        live.apply(StreamEvent.ToolCallComplete(toolCallId = id, output = "OAuth authentication completed"))

        // The card shows the action only while the call is not complete.
        assertThat(flow.value.activeToolCalls.single().isComplete).isTrue()
    }

    @Test
    fun `an auth delta for an unknown call changes nothing`() = runTest {
        val flow = MutableStateFlow(ChatUiState())
        val live = delegate(flow, backgroundScope)
        live.apply(StreamEvent.ToolCallStart(toolCallId = "other", toolName = "search", input = ""))

        live.apply(StreamEvent.ToolCallAuthRequired(toolCallId = id, authUrl = "https://idp", serverName = "github"))

        assertThat(flow.value.activeToolCalls.single().authUrl).isNull()
    }

    private fun delegateWithTraces(flow: MutableStateFlow<ChatUiState>, scope: CoroutineScope): LiveReplyDelegate {
        val root = ChatStateHandle(flow, scope)
        return LiveReplyDelegate(
            StreamingHandle(root),
            SubagentTraceDelegate(SubagentHandle(root), Json),
            mockk(relaxed = true),
            mockk<EndpointTokenRepository>(relaxed = true),
        )
    }

    @Test
    fun `a reconnect restores the sign-in the snapshot wiped`() = runTest {
        val flow = MutableStateFlow(ChatUiState())
        val live = delegate(flow, backgroundScope)
        live.apply(StreamEvent.ToolCallStart(toolCallId = id, toolName = "oauth_mcp_github", input = ""))
        live.apply(StreamEvent.ToolCallAuthRequired(toolCallId = id, authUrl = "https://idp", serverName = "github"))

        // The sync frame replaces the tool calls from aggregatedContent, which carries no auth...
        live.apply(
            StreamEvent.Sync(
                listOf(
                    MessageContentPart(
                        type = ContentType.TOOL_CALL,
                        toolCall = AgentToolCall(id = id, name = "oauth_mcp_github"),
                    ),
                ),
            ),
        )
        assertThat(flow.value.activeToolCalls.single().authUrl).isNull()
        // ...and the frame's pendingOAuthPrompts puts it back.
        live.apply(StreamEvent.ToolCallAuthRequired(toolCallId = id, authUrl = "https://idp", serverName = "github"))

        assertThat(flow.value.activeToolCalls.single().authUrl).isEqualTo("https://idp")
    }

    @Test
    fun `a subagent child's sign-in shows on the subagent call until the child call completes`() = runTest {
        val flow = MutableStateFlow(ChatUiState())
        val live = delegateWithTraces(flow, backgroundScope)
        live.apply(StreamEvent.ToolCallStart(toolCallId = "call_sub", toolName = "subagent", input = ""))
        live.apply(
            StreamEvent.SubagentUpdate(
                phase = SubagentPhase.RUN_STEP,
                parentToolCallId = "call_sub",
                subagentRunId = "run_1",
                inner = StreamEvent.ToolCallStart(toolCallId = "call_child", toolName = "search_mcp_github", input = ""),
            ),
        )

        // The child's prompt goes out on the parent's stream, top-level, naming the child call.
        live.apply(StreamEvent.ToolCallAuthRequired(toolCallId = "call_child", authUrl = "https://idp", serverName = "github"))

        val parent = flow.value.activeToolCalls.single()
        assertThat(parent.authUrl).isEqualTo("https://idp")
        assertThat(parent.authServerName).isEqualTo("github")
        assertThat(parent.authToolCallId).isEqualTo("call_child")

        live.apply(
            StreamEvent.SubagentUpdate(
                phase = SubagentPhase.RUN_STEP_COMPLETED,
                parentToolCallId = "call_sub",
                subagentRunId = "run_1",
                inner = StreamEvent.ToolCallComplete(toolCallId = "call_child", output = "ok"),
            ),
        )

        val after = flow.value.activeToolCalls.single()
        assertThat(after.authUrl).isNull()
        assertThat(after.authToolCallId).isNull()
        assertThat(after.isComplete).isFalse()
    }

    @Test
    fun `a sign-in wrapped in a subagent envelope lands on its parent call`() = runTest {
        val flow = MutableStateFlow(ChatUiState())
        val live = delegateWithTraces(flow, backgroundScope)
        live.apply(StreamEvent.ToolCallStart(toolCallId = "call_sub", toolName = "subagent", input = ""))

        live.apply(
            StreamEvent.SubagentUpdate(
                phase = SubagentPhase.RUN_STEP_DELTA,
                parentToolCallId = "call_sub",
                subagentRunId = "run_1",
                inner = StreamEvent.ToolCallAuthRequired(toolCallId = "call_child", authUrl = "https://idp", serverName = "github"),
            ),
        )

        assertThat(flow.value.activeToolCalls.single().authUrl).isEqualTo("https://idp")
    }
}
