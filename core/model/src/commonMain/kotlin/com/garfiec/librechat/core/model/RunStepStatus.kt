package com.garfiec.librechat.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The terminal state a run step closed with — upstream `Agents.RunStepClosedStatus` (v0.8.8-rc2),
 * carried by `on_run_step_closed` and persisted onto the tool-call part as `runStepStatus`.
 *
 * `in_progress` is deliberately absent: it is a member of upstream's `RunStepStatus`, but only the
 * three terminal values ever reach a client, and modelling the fourth would invite a card to render
 * a "still running" state from a frame that exists to say the opposite.
 */
@Serializable
enum class RunStepStatus {
    @SerialName("completed")
    COMPLETED,

    @SerialName("cancelled")
    CANCELLED,

    @SerialName("failed")
    FAILED,
    ;

    companion object {
        private val byWire = mapOf(
            "completed" to COMPLETED,
            "cancelled" to CANCELLED,
            "failed" to FAILED,
        )

        /** Null for an unrecognized value, so a newer terminal state degrades to the heuristic. */
        fun fromWire(value: String?): RunStepStatus? = value?.let { byWire[it] }
    }
}
