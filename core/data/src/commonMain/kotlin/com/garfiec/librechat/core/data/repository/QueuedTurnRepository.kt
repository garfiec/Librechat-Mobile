package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.model.queuedturn.AgentQueuedTurnReceipt
import com.garfiec.librechat.core.model.queuedturn.EnqueueQueuedTurnRequest
import com.garfiec.librechat.core.model.queuedturn.QueuedTurnOutcome

/**
 * Server-side queued turns (v0.8.8-rc2). A turn accepted here is admitted and RUN by the server,
 * so the local queue must never also send it.
 *
 * The calls return [QueuedTurnOutcome] rather than `Result` because the distinction this feature
 * turns on is not success/failure but *committed / provably-not-committed / unknown*, and only the
 * first two may be handed back to the local drain.
 */
interface QueuedTurnRepository {

    suspend fun enqueue(request: EnqueueQueuedTurnRequest): QueuedTurnOutcome<AgentQueuedTurnReceipt>

    /**
     * The conversation's queue as the server sees it.
     *
     * [clientRequestIds] are the rows the caller believes exist. Passing them is what makes the
     * answer exact proof about those ids: one of the caller's rows missing from the response has
     * genuinely left the queue, rather than merely falling outside some window.
     */
    suspend fun list(
        conversationId: String,
        clientRequestIds: List<String> = emptyList(),
    ): QueuedTurnOutcome<List<AgentQueuedTurnReceipt>>

    suspend fun cancel(queuedTurnId: String): QueuedTurnOutcome<AgentQueuedTurnReceipt>

    /**
     * Whether this server has already answered that it has no queued turns.
     *
     * Latched, because the answer cannot change without a redeploy, and keyed on the active
     * account so one server's verdict is never carried onto the next one's.
     */
    suspend fun isUnsupported(): Boolean
}
