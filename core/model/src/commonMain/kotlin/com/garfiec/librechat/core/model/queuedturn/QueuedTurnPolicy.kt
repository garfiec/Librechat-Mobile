package com.garfiec.librechat.core.model.queuedturn

/**
 * The classification rules that decide whether a queued turn exists, is owed, or was never
 * committed. Ported from `client/src/data-provider/SSE/queuedTurns.ts`.
 *
 * These are pure and live here rather than in the repository because the safety property they
 * encode is the whole feature: a row the client wrongly reads as "not committed" gets sent a
 * second time, and there is no server-side guard on the ordinary send path to catch it.
 */

/** How the client learned a turn's outcome. */
enum class QueuedTurnReceiptSource {
    /** The enqueue response itself — authoritative for the row it names, and for nothing else. */
    Enqueue,

    /** A reconcile poll, which speaks for every row whose id was in the request. */
    Snapshot,
}

/**
 * Whether this deployment definitively has no queued turns, so the client should fall back to the
 * legacy local drain permanently.
 *
 * The `404 && code == null` arm is what an older server answers: `/api/agents/chat/queued-turns`
 * falls through the agents router to `apiNotFound`, which sends `{"message":"Endpoint not found"}`
 * — a body with no `code` — **before** the SPA fallback, so this is a real JSON 404 rather than
 * `index.html`. A 404 that *does* carry a code came from the feature itself and means something
 * narrower, so it must not latch the fallback.
 *
 * [code] must be read from the body's `code` key only, never through
 * `ServerErrorCode.from`'s `error` fallback — upstream reads `response.data.code`, and the `error`
 * key on these bodies holds an English sentence.
 */
fun isDefiniteQueuedTurnsUnsupported(statusCode: Int?, code: String?): Boolean = when (statusCode) {
    HTTP_NOT_FOUND -> code == null
    HTTP_NOT_IMPLEMENTED ->
        code == null ||
            code == QueuedTurnErrorCode.UNSUPPORTED ||
            code == QueuedTurnErrorCode.PRIORITY_UNSUPPORTED
    else -> false
}

/**
 * Whether the failure proves the row was never committed, so the words are safe to hand back to
 * the legacy drain.
 *
 * A bounded origin 4xx is that proof. 408 and 425 are excluded because both can be generated while
 * the origin request is still running — as can any 5xx or a dropped connection — which leaves the
 * outcome ambiguous. Anything not proven here must reconcile by request identity instead of being
 * resent.
 */
fun isDefiniteQueuedTurnRejection(statusCode: Int?, code: String?): Boolean =
    !isDefiniteQueuedTurnsUnsupported(statusCode, code) &&
        statusCode != null &&
        statusCode >= HTTP_BAD_REQUEST &&
        statusCode < HTTP_INTERNAL_SERVER_ERROR &&
        statusCode != HTTP_REQUEST_TIMEOUT &&
        statusCode != HTTP_TOO_EARLY

/**
 * Whether the reconcile poll should keep running.
 *
 * [expectsReceipts] keeps it alive while the client still holds a server-owned row the projection
 * has not settled: a snapshot fetched while the enqueue is still committing comes back empty, and
 * stopping on that answer would leave a fast successor unnoticed until the next foreground.
 *
 * [reconcileUntilMillis] does the same for a transport-ambiguous enqueue, which has no row to
 * observe at all.
 *
 * A `claimed` row whose failure is [QueuedTurnFailure.ADMISSION_INDETERMINATE] deliberately does
 * NOT keep it alive — a two-second loop cannot resolve that row, and polling it would dress
 * permanent quarantine up as ordinary progress. It is still refreshed on foreground.
 */
fun shouldPollQueuedTurns(
    receipts: List<AgentQueuedTurnReceipt>?,
    reconcileUntilMillis: Long? = null,
    nowMillis: Long = 0L,
    expectsReceipts: Boolean = false,
): Boolean =
    expectsReceipts ||
        (reconcileUntilMillis != null && nowMillis < reconcileUntilMillis) ||
        receipts?.any { it.keepsProjectionAlive } == true

/**
 * Whether the server owes this conversation a run it will start itself.
 *
 * Wider than [shouldPollQueuedTurns] by exactly `admitted`: the poll stops there because nothing is
 * left to wait for, but for a client that is not attached to a stream, `admitted` is the strongest
 * evidence a run exists — and the receipt leaves the projection almost immediately afterwards.
 * Anything deciding whether to keep listening has to count it.
 */
fun isQueuedTurnSuccessorOwed(receipts: List<AgentQueuedTurnReceipt>?): Boolean =
    receipts?.any { it.status == QueuedTurnStatus.ADMITTED || it.keepsProjectionAlive } == true

private val AgentQueuedTurnReceipt.keepsProjectionAlive: Boolean
    get() = status == QueuedTurnStatus.QUEUED ||
        (status == QueuedTurnStatus.CLAIMED && !isAdmissionIndeterminate)

/**
 * How long an enqueue whose outcome the transport never revealed stays reconcilable. After this
 * the row is held for manual recovery and never becomes resendable — the outcome is still unknown,
 * it is just no longer worth polling for.
 */
const val QUEUED_TURN_RECONCILIATION_MS: Long = 60_000

private const val HTTP_BAD_REQUEST = 400
private const val HTTP_REQUEST_TIMEOUT = 408
private const val HTTP_NOT_FOUND = 404
private const val HTTP_TOO_EARLY = 425
private const val HTTP_INTERNAL_SERVER_ERROR = 500
private const val HTTP_NOT_IMPLEMENTED = 501
