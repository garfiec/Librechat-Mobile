package com.garfiec.librechat.feature.chat.components

import com.garfiec.librechat.core.model.RunStepStatus
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The live card and the reloaded card both answer "how did this tool call end", from two
 * different fields, and this module has no Compose harness to catch them disagreeing. Pulling the
 * rule out is what makes the mapping assertable at all — the same reason `sendButtonModeFor`
 * exists.
 */
class ToolCallVerdictTest {

    @Test
    fun `a closed step's own verdict wins over the completion heuristic`() {
        // The heuristic says "done"; the run says it was stopped or broke. A green check here is
        // a result the user never got.
        assertThat(toolCallVerdict(RunStepStatus.CANCELLED, isComplete = true))
            .isEqualTo(ToolCallVerdict.CANCELLED)
        assertThat(toolCallVerdict(RunStepStatus.FAILED, isComplete = true))
            .isEqualTo(ToolCallVerdict.FAILED)
    }

    @Test
    fun `a closed step is never running, whatever the heuristic says`() {
        // An abort closes its steps without ever completing them, so this is the live shape.
        assertThat(toolCallVerdict(RunStepStatus.CANCELLED, isComplete = false))
            .isEqualTo(ToolCallVerdict.CANCELLED)
        assertThat(toolCallVerdict(RunStepStatus.COMPLETED, isComplete = false))
            .isEqualTo(ToolCallVerdict.COMPLETED)
    }

    /** No close event — every pre-rc2 server, and endpoints that never emit one. */
    @Test
    fun `without a close event the heuristic decides alone`() {
        assertThat(toolCallVerdict(closedStatus = null, isComplete = true))
            .isEqualTo(ToolCallVerdict.COMPLETED)
        assertThat(toolCallVerdict(closedStatus = null, isComplete = false))
            .isEqualTo(ToolCallVerdict.RUNNING)
    }

    /**
     * A reloaded part has no running state, so the persisted card passes `isComplete = true`.
     * Its status must still be able to say the step did not succeed — that is the whole gap this
     * pins: the field was written by the delegate and read by nothing.
     */
    @Test
    fun `a persisted part still reports a failure it was stamped with`() {
        assertThat(toolCallVerdict(RunStepStatus.FAILED, isComplete = true))
            .isEqualTo(ToolCallVerdict.FAILED)
        assertThat(toolCallVerdict(RunStepStatus.COMPLETED, isComplete = true))
            .isEqualTo(ToolCallVerdict.COMPLETED)
    }
}
