package com.garfiec.librechat.core.model.response

import com.garfiec.librechat.core.model.PendingSteer
import kotlinx.serialization.Serializable

@Serializable
data class ChatAbortResponse(
    val success: Boolean = true,
    /** Stream id of the job the server actually aborted. */
    val aborted: String? = null,
    /**
     * Steers the user queued that never reached an injection boundary before the abort.
     * The server hands them back exactly once so the client can restore them as queued
     * follow-ups; it clears its own copy when it writes this, so an ack that carries them and
     * is ignored loses the user's words.
     *
     * **Always empty on a value returned by `ChatRepository.abortChat`** — that call hands the
     * list to its `claimSteers` parameter and empties it here. This field carries data only on
     * the raw wire decode.
     */
    val pendingSteers: List<PendingSteer> = emptyList(),
    /**
     * Generation protocol v2 only: the abort lost to the run's own end, so no aborted final will
     * follow. Arrives with `success: false` and `code: RUN_ALREADY_SETTLED`.
     *
     * Mobile advertises no `generationProtocolVersion`, which pins the route to v1, where that
     * outcome is a plain `{ success: true, aborted }` instead. Decoded so a client that opts into
     * v2 reads it rather than waiting out the abort watchdog.
     */
    val settled: Boolean = false,
    /** With [settled]: the job's status when the abort found it already over (`completed`, `error`, ...). */
    val terminalStatus: String? = null,
    /**
     * Generation protocol v2 only: the stop landed but saving the partial failed, so the run is
     * kept for a retry. On v1 the same outcome is a 409 `ABORT_PERSISTENCE_FAILED` with
     * `Retry-After: 1`, which `ChatRepository.abortChat` retries.
     */
    val persistenceFailed: Boolean = false,
    /** The machine-readable outcome on a non-success body, e.g. `RUN_ALREADY_SETTLED`. */
    val code: String? = null,
)
