package com.garfiec.librechat.feature.chat.components

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
}
