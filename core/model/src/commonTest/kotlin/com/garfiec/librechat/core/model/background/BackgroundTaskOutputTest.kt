package com.garfiec.librechat.core.model.background

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** Ported from upstream `Parts/__tests__/background.test.ts` (v0.8.8). */
class BackgroundTaskOutputTest {

    private fun parse(s: String) = BackgroundTaskOutput.parse(s)

    @Test
    fun every_non_pending_notice_is_a_failed_check() {
        for (status in listOf("invalid", "rejected", "unavailable", "not_found", "outcome_unknown", "error", "future")) {
            assertEquals(
                BackgroundTaskOutcome.FAILED,
                BackgroundTaskOutput.outcome(parse("""{"status":"$status","message":"Host advice"}""")),
                status,
            )
        }
        for (status in listOf("delivery_scheduled", "result_persisting")) {
            assertNull(BackgroundTaskOutput.outcome(parse("""{"status":"$status","message":"Host advice"}""")))
        }
    }

    @Test
    fun failed_task_statuses_and_control_receipts_are_failures() {
        for (status in listOf("error", "interrupted", "failed", "not_running", "control_not_found")) {
            val display = parse("""{"background_task_id":"t1","tool":"subagent","status":"$status"}""")
            assertEquals(BackgroundTaskOutcome.FAILED, BackgroundTaskOutput.outcome(display), status)
        }
    }

    @Test
    fun a_discarded_result_is_cancelled_and_a_pending_cancellation_is_not() {
        assertEquals(
            BackgroundTaskOutcome.CANCELLED,
            BackgroundTaskOutput.outcome(parse("""{"status":"cancelled","message":"Result discarded."}""")),
        )
        val stopping = parse("""{"background_task_id":"t1","tool":"bash_tool","status":"cancellation_requested"}""")
        assertEquals(PolledTaskStatus.STOPPING, assertIs<BackgroundTaskDisplay.Task>(stopping).task.status)
        assertNull(BackgroundTaskOutput.outcome(stopping))
    }

    @Test
    fun a_running_task_with_cancellation_requested_reads_as_stopping() {
        val display =
            parse("""{"background_task_id":"t1","tool":"bash_tool","status":"running","cancellation_requested":true}""")
        assertEquals(PolledTaskStatus.STOPPING, assertIs<BackgroundTaskDisplay.Task>(display).task.status)
    }

    @Test
    fun an_incomplete_list_warns_and_a_list_of_failed_tasks_does_not() {
        val incomplete = parse("""{"tasks":[],"partial":true,"warning":"Some results are missing."}""")
        val complete =
            parse("""{"tasks":[{"background_task_id":"t1","tool":"bash_tool","status":"error"}],"partial":false}""")
        assertEquals(BackgroundTaskOutcome.FAILED, BackgroundTaskOutput.outcome(incomplete))
        assertNull(BackgroundTaskOutput.outcome(complete))
    }

    @Test
    fun parses_a_task_with_its_exact_output() {
        val display = parse(
            """{"background_task_id":"task-1","tool":"bash_tool","status":"completed","result":"stdout:\nok",""" +
                """"delivery":"delivered","started_at":"2026-09-27T00:11:00Z"}""",
        )
        assertEquals(
            BackgroundTaskDisplay.Task(
                PolledTask(
                    taskId = "task-1",
                    toolName = "bash_tool",
                    status = PolledTaskStatus.COMPLETED,
                    result = "stdout:\nok",
                    delivery = "delivered",
                ),
            ),
            display,
        )
    }

    @Test
    fun anything_not_host_shaped_stays_raw() {
        assertNull(parse(""))
        assertNull(parse("plain text"))
        assertNull(parse("""[{"background_task_id":"t1"}]"""))
        assertNull(parse("""{"tasks":"nope"}"""))
        // One malformed row refuses the whole list rather than dropping it silently.
        assertNull(parse("""{"tasks":[{"background_task_id":"t1","tool":"x","status":"running"},{"tool":"x"}]}"""))
        assertNull(parse("""{"background_task_id":"t1","tool":"x","status":"someday"}"""))
        assertNull(parse("""{"background_task_id":"","tool":"x","status":"running"}"""))
        assertNull(parse("""{"background_task_id":"t1","tool":"x","status":"running","result":null}"""))
        assertNull(parse("""{"background_task_id":"t1","tool":"x","status":"running","result_available":"yes"}"""))
        assertNull(parse("""{"background_task_id":"t1","tool":"x","status":"completed","delivery":"lost"}"""))
        assertNull(parse("""{"status":"invalid"}"""))
    }
}
