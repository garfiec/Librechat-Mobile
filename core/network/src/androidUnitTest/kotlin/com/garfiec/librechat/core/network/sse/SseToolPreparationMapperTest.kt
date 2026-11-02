@file:Suppress("MaxLineLength") // wire-format JSON fixtures stay on one line, as the server sends them

package com.garfiec.librechat.core.network.sse

import com.garfiec.librechat.core.model.StreamEvent
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

/**
 * `on_tool_preparation` / `on_tool_calls_dispatched` (upstream `Agents.ToolPreparationMarker` and
 * `Agents.ToolCallsDispatchedEvent`, resolved as `packages/chat/src/core/stream/timing.ts` does).
 */
class SseToolPreparationMapperTest {

    private val mapper = SseEventMapper(Json { ignoreUnknownKeys = true; isLenient = true })

    private fun map(data: String) = mapper.map(SseEvent(event = "", data = data))

    private val runStep =
        """{"event":"on_run_step","data":{"id":"step_1","stepDetails":{"type":"tool_calls","tool_calls":[{"id":"call_1","name":"web_search","args":""}]}}}"""

    @Test
    fun `a marker for an announced step's first call names that call`() {
        map(runStep)
        val event = map("""{"event":"on_tool_preparation","data":{"id":"step_1","index":0,"observed_at":100}}""")
            as StreamEvent.ToolCallPreparation
        assertThat(event.toolCallIds).containsExactly("call_1")
        assertThat(event.dispatched).isFalse()
    }

    @Test
    fun `the marker's own call id wins`() {
        map(runStep)
        val event = map("""{"event":"on_tool_preparation","data":{"id":"step_1","index":1,"toolCallId":"call_2","observed_at":100}}""")
            as StreamEvent.ToolCallPreparation
        assertThat(event.toolCallIds).containsExactly("call_2")
    }

    @Test
    fun `a marker that arrives before its run step makes the start preparing`() {
        assertThat(map("""{"event":"on_tool_preparation","data":{"id":"step_1","index":0,"observed_at":100}}""")).isNull()
        val start = map(runStep) as StreamEvent.ToolCallStart
        assertThat(start.preparing).isTrue()
    }

    @Test
    fun `a start with no marker is not preparing`() {
        assertThat((map(runStep) as StreamEvent.ToolCallStart).preparing).isFalse()
    }

    @Test
    fun `a dispatch names every call it hands off`() {
        val event = map(
            """{"event":"on_tool_calls_dispatched","data":{"dispatched_at":200,"toolCalls":[{"id":"call_1","name":"a","stepId":"step_1"},{"id":"call_2","name":"b"}]}}""",
        ) as StreamEvent.ToolCallPreparation
        assertThat(event.toolCallIds).containsExactly("call_1", "call_2").inOrder()
        assertThat(event.dispatched).isTrue()
    }

    @Test
    fun `a dispatch before the start clears a held marker`() {
        map("""{"event":"on_tool_preparation","data":{"id":"step_x","index":0,"toolCallId":"call_1","observed_at":100}}""")
        map("""{"event":"on_tool_calls_dispatched","data":{"dispatched_at":200,"toolCalls":[{"id":"call_1","name":"a"}]}}""")
        assertThat((map(runStep) as StreamEvent.ToolCallStart).preparing).isFalse()
    }

    @Test
    fun `malformed markers are dropped`() {
        assertThat(map("""{"event":"on_tool_preparation","data":{"index":0}}""")).isNull()
        assertThat(map("""{"event":"on_tool_calls_dispatched","data":{"toolCalls":[]}}""")).isNull()
    }
}
