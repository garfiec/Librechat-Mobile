package com.garfiec.librechat.core.model.content

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The v0.8.8 `PartMetadata` timing fields as the server persists them on a tool call. */
class AgentToolCallTimingTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun persisted_timing_fields_decode() {
        val call = json.decodeFromString<AgentToolCall>(
            """{"id":"c1","name":"web_search","runStepDurationMs":4200,"runStepClosedAt":1790000000000,
               "toolPreparationStartedAt":1789999995800,"toolDispatchedAt":1789999997000,
               "toolPreparationDurationMs":1200,"toolExecutionDurationMs":2900}""",
        )
        assertEquals(4200L, call.runStepDurationMs)
        assertEquals(1790000000000L, call.runStepClosedAt)
        assertEquals(1200L, call.toolPreparationDurationMs)
        assertEquals(2900L, call.toolExecutionDurationMs)
        assertEquals(1789999995800L, call.toolPreparationStartedAt)
        assertEquals(1789999997000L, call.toolDispatchedAt)
    }

    @Test
    fun the_attached_workspace_executor_decodes() {
        val call = json.decodeFromString<AgentToolCall>("""{"id":"c1","name":"bash_tool","executor":"attached_workspace"}""")
        assertEquals(AgentToolCall.EXECUTOR_ATTACHED_WORKSPACE, call.executor)
    }

    @Test
    fun older_content_has_none_of_them() {
        val call = json.decodeFromString<AgentToolCall>("""{"id":"c1","name":"web_search"}""")
        assertNull(call.runStepClosedAt)
        assertNull(call.toolPreparationDurationMs)
        assertNull(call.toolExecutionDurationMs)
    }
}
