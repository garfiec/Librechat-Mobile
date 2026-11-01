package com.garfiec.librechat.feature.chat.viewmodel.delegate

import com.garfiec.librechat.core.data.repository.EndpointTokenRepository
import com.garfiec.librechat.core.model.StreamEvent
import com.garfiec.librechat.feature.chat.viewmodel.ChatStateHandle
import com.garfiec.librechat.feature.chat.viewmodel.ChatUiState
import com.garfiec.librechat.feature.chat.viewmodel.StreamingHandle
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
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
}
