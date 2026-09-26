package com.garfiec.librechat.core.model

/**
 * Lifecycle/content phases carried on a [StreamEvent.SubagentUpdate] (`on_subagent_update`,
 * v0.8.6). Mirrors upstream's `SubagentUpdatePhase` union (`packages/data-provider`
 * `runs.ts`). [TERMINAL] is the set after which a subagent trace stops accumulating.
 */
object SubagentPhase {
    const val START = "start"
    const val RUN_STEP = "run_step"
    const val RUN_STEP_DELTA = "run_step_delta"
    const val RUN_STEP_COMPLETED = "run_step_completed"

    /** v0.8.8-rc2. Closes a STEP, not the child run — the run terminals stay [STOP] / [ERROR]. */
    const val RUN_STEP_CLOSED = "run_step_closed"
    const val MESSAGE_DELTA = "message_delta"
    const val REASONING_DELTA = "reasoning_delta"
    const val STOP = "stop"
    const val ERROR = "error"

    /** Phases after which the child run is done and folding should stop. */
    val TERMINAL = setOf(STOP, ERROR)

    fun isTerminal(phase: String?): Boolean = phase in TERMINAL
}
