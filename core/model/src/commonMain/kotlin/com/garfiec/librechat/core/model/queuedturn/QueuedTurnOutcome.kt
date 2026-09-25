package com.garfiec.librechat.core.model.queuedturn

/**
 * The result of a queued-turn call, split by what it PROVES rather than by whether it worked.
 *
 * An ordinary `Result.Error` collapses the only distinction that matters here. The server has no
 * guard against a turn being enqueued and then also sent the normal way, so a failure the client
 * misreads as "not committed" sends the user's words twice. Every arm below answers the single
 * question "may the local queue take this row back?" — and only [Rejected] and [Unsupported]
 * answer yes.
 */
sealed interface QueuedTurnOutcome<out T> {

    /** The server owns the row. */
    data class Committed<T>(val value: T) : QueuedTurnOutcome<T>

    /**
     * This deployment has no queued turns at all. Permanent for the server, so the caller latches
     * it and stops offering the feature rather than paying a request per enqueue to rediscover it.
     */
    data object Unsupported : QueuedTurnOutcome<Nothing>

    /** The server refused, and the refusal proves nothing was committed. Safe to hand back. */
    data class Rejected(
        val statusCode: Int?,
        val code: String?,
        val message: String?,
    ) : QueuedTurnOutcome<Nothing>

    /**
     * The outcome is unknown — a timeout, a 5xx, a dropped connection, or a 408/425 generated
     * while the origin request kept running. The row may or may not exist.
     *
     * **Never resend on this.** Reconcile by `clientRequestId` instead: re-POSTing the same id is
     * free (the unique index resolves it to the existing row), and the read-only list route proves
     * the answer without mutating anything.
     */
    data class Indeterminate(
        val statusCode: Int?,
        val code: String?,
        val message: String?,
    ) : QueuedTurnOutcome<Nothing>
}

fun <T> QueuedTurnOutcome<T>.valueOrNull(): T? =
    (this as? QueuedTurnOutcome.Committed)?.value
