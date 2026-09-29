package com.garfiec.librechat.core.model.wakeup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Ported from upstream `client/src/components/Chat/Messages/Content/Parts/__tests__/wakeup.test.ts`. */
class WakeupMessageTest {

    private val subagentHeader =
        "A detached subagent task has completed. Continue the parent task using its durable result below."

    private val subagentText = listOf(
        subagentHeader,
        """{"background_task_id":"task-1","subagent_thread_id":"thread-1","subagent_type":"self",""" +
            """"status":"completed","result":"## Daily briefing\nAll clear."}""",
        "Host-authored bounded orchestration snapshot:",
        """{"scope":"current_parent_branch","known_children":[]}""",
    ).joinToString("\n")

    private val backgroundHeader =
        "A background tool task has finished. Continue using its durable result below."

    @Test
    fun parses_a_subagent_completion_into_one_task() {
        assertEquals(
            WakeupDisplay(
                kind = WakeupKind.SUBAGENT,
                tasks = listOf(
                    WakeupTask(
                        taskId = "task-1",
                        status = WakeupTaskStatus.COMPLETED,
                        result = "## Daily briefing\nAll clear.",
                        threadId = "thread-1",
                        subagentType = "self",
                    ),
                ),
            ),
            WakeupMessage.parse(subagentText),
        )
    }

    @Test
    fun parses_subagent_error_and_cancelled() {
        for (status in listOf(WakeupTaskStatus.ERROR, WakeupTaskStatus.CANCELLED)) {
            val text = "A detached subagent task has ${status.wire}. Continue the parent task using its durable result below.\n" +
                """{"background_task_id":"t","subagent_thread_id":"th","subagent_type":"researcher",""" +
                """"status":"${status.wire}","result":""}"""
            assertEquals(status, WakeupMessage.parse(text)?.tasks?.single()?.status)
        }
    }

    @Test
    fun parses_a_single_background_tool_wakeup() {
        val text = backgroundHeader + "\n" +
            """[{"background_task_id":"bg-1","tool_call_id":"call-1","tool":"web_search","status":"completed","result":"Found 3 sources."}]"""
        assertEquals(
            WakeupDisplay(
                kind = WakeupKind.BACKGROUND_TOOL,
                tasks = listOf(
                    WakeupTask(
                        taskId = "bg-1",
                        status = WakeupTaskStatus.COMPLETED,
                        result = "Found 3 sources.",
                        toolCallId = "call-1",
                        toolName = "web_search",
                    ),
                ),
            ),
            WakeupMessage.parse(text),
        )
    }

    @Test
    fun parses_a_plural_background_tool_wakeup() {
        val text = "2 background tool tasks have finished. Continue using their durable results below.\n" +
            """[{"background_task_id":"bg-1","tool_call_id":"call-1","tool":"web_search","status":"completed","result":"ok"},""" +
            """{"background_task_id":"bg-2","tool_call_id":"call-2","tool":"execute_code","status":"error","result":"boom"}]"""
        val display = WakeupMessage.parse(text)
        assertEquals(WakeupKind.BACKGROUND_TOOL, display?.kind)
        assertEquals(2, display?.tasks?.size)
        assertEquals(WakeupTaskStatus.ERROR, display?.tasks?.get(1)?.status)
        assertEquals("execute_code", display?.tasks?.get(1)?.toolName)
    }

    @Test
    fun rejects_ordinary_user_text() {
        assertNull(WakeupMessage.parse("Please summarize the detached subagent task results."))
        assertNull(WakeupMessage.parse(""))
        assertNull(WakeupMessage.parse(null))
    }

    @Test
    fun rejects_a_quoted_prompt_that_does_not_start_the_message() {
        assertNull(WakeupMessage.parse("Look at this:\n$subagentText"))
    }

    /** The header must be the whole first line: upstream's regex requires the newline right after it. */
    @Test
    fun rejects_a_header_line_with_anything_after_the_sentence() {
        assertNull(WakeupMessage.parse(subagentText.replaceFirst("below.\n", "below. See above.\n")))
        assertNull(WakeupMessage.parse(subagentHeader))
    }

    /** JavaScript's `\d` is ASCII; ICU's `\d` would admit these Arabic-Indic digits. */
    @Test
    fun rejects_a_task_count_in_non_ascii_digits() {
        val text = "٢ background tool tasks have finished. Continue using their durable results below.\n" +
            """[{"background_task_id":"bg-1","tool_call_id":"call-1","tool":"web_search","status":"completed","result":"ok"}]"""
        assertNull(WakeupMessage.parse(text))
    }

    @Test
    fun rejects_a_payload_that_is_not_json() {
        assertNull(WakeupMessage.parse("$subagentHeader\nnot json"))
        // A lenient parser would accept this; upstream's JSON.parse does not.
        assertNull(
            WakeupMessage.parse(
                "$backgroundHeader\n[{background_task_id:bg-1,tool_call_id:c,tool:t,status:completed,result:ok}]",
            ),
        )
    }

    @Test
    fun rejects_a_status_that_disagrees_with_the_header() {
        val text = "$subagentHeader\n" +
            """{"background_task_id":"t","subagent_thread_id":"th","subagent_type":"self","status":"error","result":""}"""
        assertNull(WakeupMessage.parse(text))
    }

    @Test
    fun rejects_missing_or_mistyped_identity_fields() {
        assertNull(WakeupMessage.parse("$backgroundHeader\n" + """[{"background_task_id":"bg-1","status":"completed","result":"ok"}]"""))
        assertNull(
            WakeupMessage.parse(
                "$backgroundHeader\n" +
                    """[{"background_task_id":1,"tool_call_id":"c","tool":"t","status":"completed","result":"ok"}]""",
            ),
        )
    }

    @Test
    fun rejects_an_empty_background_payload_and_a_cancelled_background_task() {
        assertNull(WakeupMessage.parse("$backgroundHeader\n[]"))
        assertNull(
            WakeupMessage.parse(
                "$backgroundHeader\n" +
                    """[{"background_task_id":"b","tool_call_id":"c","tool":"t","status":"cancelled","result":""}]""",
            ),
        )
    }
}
