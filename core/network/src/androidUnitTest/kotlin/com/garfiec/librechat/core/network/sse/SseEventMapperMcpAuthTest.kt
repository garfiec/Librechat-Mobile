@file:Suppress("MaxLineLength") // wire-format JSON fixtures stay on one line, as the server sends them

package com.garfiec.librechat.core.network.sse

import com.garfiec.librechat.core.model.StreamEvent
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

/** `on_run_step_delta` carrying `auth` — upstream `buildMCPAuthRunStepEvent` + `buildMCPAuthRunStepDeltaEvent`. */
class SseEventMapperMcpAuthTest {

    private val mapper = SseEventMapper(Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true })

    private val authUrl =
        "https://idp.example.com/authorize?client_id=x&redirect_uri=https%3A%2F%2Fchat.example.com%2Fapi%2Fmcp%2Fmy%20server%2Foauth%2Fcallback&state=s"

    private fun runStep() = SseEvent(
        event = "",
        data = """{"event":"on_run_step","data":{"runId":"r","id":"step_oauth_login_my_server","type":"tool_calls","index":0,"stepDetails":{"type":"tool_calls","tool_calls":[{"id":"u1:my server:123","name":"oauth_mcp_my_server","type":"tool_call_chunk"}]}}}""",
    )

    private fun authDelta(auth: String = authUrl, extra: String = ""","expires_at":1704067200000""") = SseEvent(
        event = "",
        data = """{"event":"on_run_step_delta","data":{"id":"step_oauth_login_my_server","delta":{"type":"tool_calls","tool_calls":[{"id":"u1:my server:123","name":"oauth_mcp_my_server","type":"tool_call_chunk","args":""}],"auth":"$auth"$extra}}}""",
    )

    @Test
    fun `an auth delta becomes a sign-in prompt on the announced call`() {
        mapper.map(runStep())

        val event = mapper.map(authDelta()) as StreamEvent.ToolCallAuthRequired

        assertThat(event.toolCallId).isEqualTo("u1:my server:123")
        assertThat(event.authUrl).isEqualTo(authUrl)
        assertThat(event.expiresAt).isEqualTo(1_704_067_200_000L)
        // The redirect_uri carries the stored name; the tool name only its normalized form.
        assertThat(event.serverName).isEqualTo("my server")
    }

    @Test
    fun `without the announcing step the delta's own call id is used`() {
        val event = mapper.map(authDelta()) as StreamEvent.ToolCallAuthRequired
        assertThat(event.toolCallId).isEqualTo("u1:my server:123")
    }

    @Test
    fun `the tool name is the fallback when the url names no MCP callback`() {
        mapper.map(runStep())
        val event = mapper.map(authDelta(auth = "https://idp.example.com/authorize?x=1")) as StreamEvent.ToolCallAuthRequired
        assertThat(event.serverName).isEqualTo("my_server")
        assertThat(event.expiresAt).isEqualTo(1_704_067_200_000L)
    }

    @Test
    fun `a delta without auth is still dropped`() {
        mapper.map(runStep())
        val event = mapper.map(
            SseEvent(
                event = "",
                data = """{"event":"on_run_step_delta","data":{"id":"step_oauth_login_my_server","delta":{"type":"tool_calls","tool_calls":[{"id":"u1:my server:123","name":"oauth_mcp_my_server"}]}}}""",
            ),
        )
        assertThat(event).isNull()
    }

    @Test
    fun `a missing expiry maps to null rather than failing`() {
        mapper.map(runStep())
        val event = mapper.map(authDelta(extra = "")) as StreamEvent.ToolCallAuthRequired
        assertThat(event.expiresAt).isNull()
    }

    private fun syncFrame(aggregatedContent: String, prompts: String) = SseEvent(
        event = "",
        data = """{"sync":true,"resumeState":{"runSteps":[],"aggregatedContent":$aggregatedContent,"pendingOAuthPrompts":$prompts}}""",
    )

    private val prompt =
        """{"stepId":"step_oauth_login_my_server","runId":"r","index":0,"toolCallId":"u1:my server:123","toolName":"oauth_mcp_my_server","authURL":"$authUrl","expiresAt":1704067200000}"""

    @Test
    fun `a resume restores a pending sign-in on a call the snapshot holds`() {
        val events = mapper.mapFrame(
            syncFrame(
                """[{"type":"tool_call","tool_call":{"id":"u1:my server:123","name":"oauth_mcp_my_server","args":""}}]""",
                "[$prompt]",
            ),
        )

        assertThat(events[0]).isInstanceOf(StreamEvent.Sync::class.java)
        val auth = events[1] as StreamEvent.ToolCallAuthRequired
        assertThat(auth.toolCallId).isEqualTo("u1:my server:123")
        assertThat(auth.authUrl).isEqualTo(authUrl)
        assertThat(auth.serverName).isEqualTo("my server")
        // Already in the past by the device clock: the server is the judge of expiry, not this side.
        assertThat(auth.expiresAt).isEqualTo(1_704_067_200_000L)
        assertThat(events.filterIsInstance<StreamEvent.ToolCallStart>()).isEmpty()
    }

    @Test
    fun `a pending sign-in on a call the snapshot lacks announces the call first`() {
        val events = mapper.mapFrame(syncFrame("[]", "[$prompt]"))

        assertThat(events[0]).isInstanceOf(StreamEvent.Sync::class.java)
        val start = events[1] as StreamEvent.ToolCallStart
        assertThat(start.toolCallId).isEqualTo("u1:my server:123")
        assertThat(start.toolName).isEqualTo("oauth_mcp_my_server")
        assertThat((events[2] as StreamEvent.ToolCallAuthRequired).toolCallId).isEqualTo("u1:my server:123")
    }

    @Test
    fun `a pending prompt without a tool call id is skipped`() {
        val events = mapper.mapFrame(
            syncFrame("[]", """[{"stepId":"s","index":0,"toolName":"oauth_mcp_x","authURL":"https://idp"}]"""),
        )
        assertThat(events.filterIsInstance<StreamEvent.ToolCallAuthRequired>()).isEmpty()
        assertThat(events.filterIsInstance<StreamEvent.ToolCallStart>()).isEmpty()
    }

    @Test
    fun `a resume registers the prompt's step so a later live delta on it resolves`() {
        mapper.mapFrame(syncFrame("[]", "[$prompt]"))
        val live = mapper.map(
            SseEvent(
                event = "",
                data = """{"event":"on_run_step_delta","data":{"id":"step_oauth_login_my_server","delta":{"type":"tool_calls","auth":"$authUrl"}}}""",
            ),
        ) as StreamEvent.ToolCallAuthRequired
        assertThat(live.toolCallId).isEqualTo("u1:my server:123")
    }

    @Test
    fun `a subagent's auth delta maps to its inner sign-in prompt`() {
        val event = mapper.map(
            SseEvent(
                event = "",
                data = """{"event":"on_subagent_update","data":{"phase":"run_step_delta","parentToolCallId":"call_sub","subagentRunId":"run_1","data":{"id":"step_child","delta":{"type":"tool_calls","tool_calls":[{"id":"call_child","name":"search_mcp_github","args":""}],"auth":"$authUrl"}}}}""",
            ),
        ) as StreamEvent.SubagentUpdate

        val inner = event.inner as StreamEvent.ToolCallAuthRequired
        assertThat(inner.toolCallId).isEqualTo("call_child")
        assertThat(inner.serverName).isEqualTo("my server")
    }

    @Test
    fun `a subagent's ordinary run step delta still carries nothing`() {
        val event = mapper.map(
            SseEvent(
                event = "",
                data = """{"event":"on_subagent_update","data":{"phase":"run_step_delta","parentToolCallId":"call_sub","data":{"id":"step_child","delta":{"type":"tool_calls","tool_calls":[{"id":"call_child","args":"{}"}]}}}}""",
            ),
        ) as StreamEvent.SubagentUpdate
        assertThat(event.inner).isNull()
    }
}
