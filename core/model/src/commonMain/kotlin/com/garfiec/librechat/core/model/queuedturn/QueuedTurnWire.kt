package com.garfiec.librechat.core.model.queuedturn

import kotlinx.serialization.Serializable

/**
 * Body of `POST /api/agents/chat/queued-turns`.
 *
 * [clientRequestId] must be STABLE across retries of the same turn — it is the idempotency key the
 * server's UNIQUE index resolves a replay against. It must also address the same content: the
 * server compares the replay's intent and answers `409 QUEUED_TURN_IDEMPOTENCY_CONFLICT` when an
 * id is reused for a different message.
 *
 * [parentMessageId] and [expectedPredecessorCreatedAt] are the boundary this turn follows, captured
 * at ENQUEUE. That is the opposite of what mobile's local queue does, and it is not optional here:
 * the server owns admission, so it has to be told what to admit behind.
 */
@Serializable
data class EnqueueQueuedTurnRequest(
    val conversationId: String,
    val parentMessageId: String,
    val clientRequestId: String,
    val text: String,
    val files: List<QueuedTurnFileRef>? = null,
    val quotes: List<String>? = null,
    val manualSkills: List<String>? = null,
    val priority: Boolean? = null,
    val expectedPredecessorCreatedAt: Long? = null,
)

/** `202` (queued) or `200` (a replay of a row that already settled). */
@Serializable
data class EnqueueQueuedTurnResponse(
    val receipt: AgentQueuedTurnReceipt? = null,
    val capability: AgentQueuedTurnCapability = AgentQueuedTurnCapability(),
)

/** `GET /api/agents/chat/queued-turns?conversationId=&clientRequestIds=…` */
@Serializable
data class ListQueuedTurnsResponse(
    val queuedTurns: List<AgentQueuedTurnReceipt> = emptyList(),
    val capability: AgentQueuedTurnCapability = AgentQueuedTurnCapability(),
    val revision: Long? = null,
)

/** `DELETE /api/agents/chat/queued-turns/:queuedTurnId` */
@Serializable
data class CancelQueuedTurnResponse(
    val receipt: AgentQueuedTurnReceipt? = null,
)

/** `code` values the queued-turn routes answer with. */
object QueuedTurnErrorCode {
    /** The deployment has no queued-turn support. Definitive: stop offering it this session. */
    const val UNSUPPORTED = "QUEUED_TURNS_UNSUPPORTED"

    /** `priority` specifically is unsupported; the turn itself would have been accepted. */
    const val PRIORITY_UNSUPPORTED = "QUEUED_TURN_PRIORITY_UNSUPPORTED"

    /** The id was reused for different content — a client bug, never a retry to repeat. */
    const val IDEMPOTENCY_CONFLICT = "QUEUED_TURN_IDEMPOTENCY_CONFLICT"

    /** The server's queue for this conversation is full. */
    const val QUEUE_FULL = "QUEUED_TURN_QUEUE_FULL"

    /** Transient: the row exists but could not be scheduled yet. Retrying the POST is correct,
     *  and safe, because the id resolves to the same row. */
    const val SCHEDULING_PENDING = "QUEUED_TURN_SCHEDULING_PENDING"

    /** 400 from the list route — more than [MAX_QUEUED_TURN_LIST_IDS] ids, or one over 128 chars. */
    const val INVALID_CLIENT_REQUEST_IDS = "INVALID_CLIENT_REQUEST_IDS"
}

/** Server cap on a `clientRequestId`, enforced by the enqueue schema and the list parser alike. */
const val MAX_CLIENT_REQUEST_ID_LENGTH = 128

/** Server cap on `clientRequestIds` per list request; upstream dedupes and rejects beyond it. */
const val MAX_QUEUED_TURN_LIST_IDS = 100
