package com.garfiec.librechat.shared

import co.touchlab.kermit.Severity
import kotlin.test.Test

/**
 * Logging text that looks like a format string must not crash (#533). There is no assertion: the
 * failure mode is the test process dying with a segfault or bus error inside `NSLog`.
 */
class NsLogVerbatimTest {

    private val formatLike = "server said %@ %s %20s %n 100%"

    @Test
    fun text_with_format_specifiers_is_logged_verbatim() {
        nsLogVerbatim(formatLike)
    }

    @Test
    fun the_kermit_writer_logs_a_message_and_a_stack_trace_containing_format_specifiers() {
        IosNSLogWriter().log(
            severity = Severity.Error,
            message = formatLike,
            tag = "Test",
            throwable = IllegalStateException(formatLike),
        )
    }
}
