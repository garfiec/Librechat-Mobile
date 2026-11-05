package com.garfiec.librechat.feature.chat.components

import com.garfiec.librechat.core.model.content.AgentToolCall
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The attached-workspace `bash_tool` trailer, as upstream `parseCommandOutput` reads its verdict. */
class CommandOutputTest {

    @Test
    fun a_zero_exit_is_not_a_failure() {
        assertEquals(false, attachedCommandFailed("stdout:\nhello\n[exit code: 0]"))
    }

    @Test
    fun a_non_zero_exit_a_signal_or_a_timeout_is_a_failure() {
        assertEquals(true, attachedCommandFailed("stdout:\nhello\nstderr:\nboom\n[exit code: 2]"))
        assertEquals(true, attachedCommandFailed("stdout:\n\n[terminated by SIGKILL]"))
        assertEquals(true, attachedCommandFailed("stdout:\nslow\n[exit code: 0][timed out]\nCommand reached timeoutMs: 30000"))
    }

    @Test
    fun a_truncation_alone_is_not_a_failure() {
        assertEquals(false, attachedCommandFailed("stdout:\nlots\n[exit code: 0][output truncated]"))
    }

    @Test
    fun the_empty_notice_and_a_directory_header_are_accepted() {
        assertEquals(false, attachedCommandFailed("Command completed with no output.\n[exit code: 0]"))
        assertEquals(
            true,
            attachedCommandFailed("[starting directory: \"workspace/app\"]\nstdout:\nx\n[exit code: 1]\n[directory hint: use cwd]"),
        )
    }

    @Test
    fun text_that_is_not_the_shape_is_null() {
        assertNull(attachedCommandFailed(null))
        assertNull(attachedCommandFailed("just text\n[exit code: 1]"))
        assertNull(attachedCommandFailed("stdout:\nhello"))
        // JavaScript's `$` is the end of the text; a trailing newline after the trailer is not the shape.
        assertNull(attachedCommandFailed("stdout:\nhello\n[exit code: 1]\n"))
    }

    @Test
    fun the_output_splits_into_stdout_stderr_and_trailer_without_losing_text() {
        val output = "stdout:\nhello\nstderr:\nboom\n[exit code: 2]"
        val parts = parseCommandOutput(output)!!
        assertEquals("stdout:\nhello\n", parts.head)
        assertEquals("stderr:\nboom\n", parts.stderr)
        assertEquals("[exit code: 2]", parts.trailer)
        assertEquals(output, parts.head + parts.stderr + parts.trailer)
        assertEquals(2, parts.exitCode)
        assertEquals(true, parts.failed)
    }

    @Test
    fun a_signal_and_a_timeout_are_named() {
        assertEquals("SIGKILL", parseCommandOutput("stdout:\n\n[terminated by SIGKILL]")?.signal)
        val timedOut = parseCommandOutput("stdout:\nslow\n[exit code: 0][timed out]\nCommand reached timeoutMs: 30000")!!
        assertEquals(true, timedOut.timedOut)
        assertEquals(true, timedOut.failed)
    }

    @Test
    fun only_an_attached_workspace_call_reads_the_trailer() {
        val output = "stdout:\nok\nstderr:\nwarning\n[exit code: 0]"
        val attached = parseCodeExecution(
            AgentToolCall(name = "bash_tool", output = output, executor = AgentToolCall.EXECUTOR_ATTACHED_WORKSPACE),
        )!!
        assertEquals(false, attached.indicatesError())
        assertEquals(0, attached.exitCode)
        assertEquals("stderr:\nwarning", attached.error)
        val sandbox = parseCodeExecution(AgentToolCall(name = "bash_tool", output = output))!!
        assertNull(sandbox.command)
    }

    @Test
    fun the_live_card_reads_the_trailer_from_the_completions_executor() {
        val output = "stdout:\nslow\n[exit code: 124][timed out]\nCommand reached timeoutMs: 30000"
        val parsed = parseCodeExecution("bash_tool", """{"command":"make build"}""", output)!!
        val live = parsed.withAttachedCommand(AgentToolCall.EXECUTOR_ATTACHED_WORKSPACE, output)
        assertEquals(true, live.command?.timedOut)
        assertEquals("[exit code: 124][timed out]\nCommand reached timeoutMs: 30000", live.command?.trailer)
        assertEquals("stdout:\nslow", live.output)
        assertNull(parsed.withAttachedCommand(null, output).command)
    }
}
