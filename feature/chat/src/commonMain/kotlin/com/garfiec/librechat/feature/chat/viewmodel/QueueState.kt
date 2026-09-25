package com.garfiec.librechat.feature.chat.viewmodel

import androidx.compose.runtime.Immutable
import com.garfiec.librechat.core.data.endpoint.EndpointDispatch
import com.garfiec.librechat.core.model.request.EphemeralAgent
import com.garfiec.librechat.core.ui.components.ModelParameters
import com.garfiec.librechat.feature.chat.components.AttachedFile
import kotlinx.serialization.json.JsonObject

/**
 * The in-memory FIFO follow-up queue staged while a reply streams. Owned by
 * [com.garfiec.librechat.feature.chat.viewmodel.delegate.MessageQueueDelegate];
 * never persisted (lives and dies with the ViewModel).
 */
@Immutable
data class QueueState(
    /** Follow-up messages queued while a reply streams, drained FIFO on each successful
     *  completion. Rendered as ghost bubbles after the streaming bubble; never part of the
     *  message tree. In-memory only (dropped on conversation switch / process death). */
    val messageQueue: List<QueuedMessage> = emptyList(),
    /** True after Stop/stream-error with a non-empty queue: draining is held until the user
     *  explicitly taps "Send queued". A successful Final drains automatically instead. */
    val isQueuePaused: Boolean = false,
    /**
     * What the server has told this client about queued turns that have LEFT [messageQueue].
     *
     * Deliberately outlives the rows: an admitted turn is removed from the queue the moment the
     * reconcile poll sees it, but the boundary it consumed still has to fence the local drain —
     * otherwise the run's own `Final` arrives moments later, finds no server-owned row, and
     * drains a legacy follow-up into a turn the server has already started.
     */
    val settledQueuedTurns: List<SettledQueuedTurn> = emptyList(),
    /**
     * `clientRequestId`s whose enqueue POST has not settled. Keeps the reconcile poll alive
     * across the window where a snapshot can legitimately come back empty because the row is
     * still committing.
     */
    val pendingQueuedTurnEnqueueIds: List<String> = emptyList(),
)

/**
 * A follow-up message the user queued while a response was streaming, waiting to be
 * auto-sent (FIFO) once the current reply completes. Rendered as a dimmed "ghost" bubble
 * after the streaming bubble — it is NOT part of the message tree.
 *
 * Captures a full snapshot of the send config **at queue time** (model/endpoint/tools/
 * webSearch/attachments + the resolved [dispatch]/[ephemeralAgent]), so a mid-stream model
 * switch never retro-edits an already-queued item.
 *
 * **Lineage depends on who owns the row.** For a legacy row, conversationId / parentMessageId /
 * userMessageId are deliberately NOT snapshotted — they are recomputed from the current tree when
 * the item actually fires, so each send chains onto the freshly finalized turn (#167). A row the
 * server owns ([server] non-null) reverses that: [parentMessageId] and
 * [expectedPredecessorCreatedAt] are captured at ENQUEUE, because the server does the admitting
 * and has to be told what to admit behind. The two cannot be reconciled — live recompute is what
 * makes the second-and-later item in a queue work at all, since its parent does not exist yet
 * when it is composed, while server ownership needs a boundary stated up front. The server closes
 * that gap itself: it revalidates the captured boundary at admission and reports the one it
 * actually consumed as `effectivePredecessorCreatedAt`, which advances as queued turns chain.
 *
 * [attachments] holds the already-uploaded [AttachedFile]s (not bare FileReferences) so editing
 * a queued item restores its composer chips — including the local-uri image thumbnail — intact.
 */
@Immutable
data class QueuedMessage(
    /** Stable local id for list keying, edit, and reorder. Not a server message id. */
    val localId: String,
    val text: String,
    val attachments: List<AttachedFile> = emptyList(),
    val endpoint: String,
    val model: String?,
    val agentId: String?,
    val enabledTools: Set<String> = emptySet(),
    /** Selected ephemeral MCP servers — snapshotted so editing the item restores its tool state. */
    val mcpServerNames: Set<String> = emptySet(),
    /** Full composer parameters (web search, reasoning effort, etc.) — restored to the composer on edit. */
    val modelParameters: ModelParameters = ModelParameters.DEFAULT,
    /**
     * Non-default model params (provider-keyed) serialized for the wire, snapshotted at enqueue time
     * so a queued send carries the params it was composed with. Null when nothing was customized.
     */
    val modelParamsPayload: JsonObject? = null,
    val ephemeralAgent: EphemeralAgent? = null,
    /**
     * Quoted excerpts taken from the pending-quote chips when this spec was minted (v0.8.7
     * "Add to chat"). They ride the spec — not the composer — so a queued follow-up, a drained
     * item, and a degraded steer that re-homes here all send the excerpts they were composed
     * with.
     *
     * Composer-origin steers mint their spec WITH quotes from v0.8.8-rc2, when
     * `POST /api/agents/chat/steer` began carrying them. The spec is also what a dropped excerpt
     * is recovered from, so `SteeringDelegate` strips it once the chips hold the copy.
     */
    val quotes: List<String> = emptyList(),
    val dispatch: EndpointDispatch,
    val isTemporary: Boolean = false,
    /**
     * The active account when this item was queued. A drain guard drops any item whose account no
     * longer matches the active one (the user switched accounts since queueing), so a follow-up
     * composed under account A can never be POSTed to account B's server under B's bearer. Null for
     * items composed before multi-account (or in tests) — treated as "matches any", never dropped.
     */
    val accountId: String? = null,
    /**
     * The server's authority over this row, or null while it stays on the legacy local drain —
     * which is also where a definite old-server answer puts it back.
     *
     * Non-null means the local queue must not send this item, and must not send anything behind
     * it either. [QueuedTurnServerState.Status.Uncertain] is deliberately included: falling back
     * after an ambiguous POST could submit the same words twice.
     */
    val server: QueuedTurnServerState? = null,
    /**
     * Idempotency key for the server enqueue, minted when the row becomes server-owned — not
     * [localId], which survives an edit and would then address different content (409).
     */
    val clientRequestId: String? = null,
    /** The visible leaf this turn was queued behind. Captured at enqueue; see the class KDoc. */
    val parentMessageId: String? = null,
    /** The generation epoch observed when this turn became eligible. */
    val expectedPredecessorCreatedAt: Long? = null,
)

/**
 * A server-owned queued turn as the client tracks it.
 *
 * The statuses are a superset of the server's on one side and a subset on the other: the client
 * adds the three the wire cannot express ([Status.Sending], [Status.Uncertain],
 * [Status.Rejected]) and has no `admitted` — admission removes the row, handing the turn to the
 * reveal. An enum is safe here precisely because none of it is decoded from a response.
 */
@Immutable
data class QueuedTurnServerState(
    val status: Status,
    /** The server's `queuedTurnId`; absent until a receipt comes back. Cancel needs it. */
    val id: String? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    /**
     * When the enqueue became transport-ambiguous. The row itself may be far older than the
     * request that just lost its answer, so this is the observation time, not the item's.
     */
    val uncertainSince: Long? = null,
    /**
     * The reconciliation window elapsed with no authoritative evidence. The outcome is still
     * unknown — this marks it as no longer worth polling for, never as resendable.
     */
    val reconciliationExpired: Boolean = false,
    /** Current 1-based projection. [revision] is the stable order when positions close up. */
    val position: Int? = null,
    val revision: Long? = null,
) {
    enum class Status {
        /** The enqueue is in flight. Already server-owned: the POST may have landed. */
        Sending,

        /** The enqueue's outcome was never revealed. Reconcile by id; never resend. */
        Uncertain,

        /** The server cannot say whether admission started a run. Polling cannot resolve it. */
        Indeterminate,

        /** The server refused, provably without committing. Needs the user, not a retry. */
        Rejected,

        Queued,
        Claimed,
    }
}

/** Loads a queued item's content + config onto the composer surface when entering edit mode. */
fun QueuedMessage.toComposerSnapshot(): ComposerSnapshot = ComposerSnapshot(
    text = text,
    attachments = attachments,
    endpoint = endpoint,
    model = model,
    enabledTools = enabledTools,
    mcpServerNames = mcpServerNames,
    modelParameters = modelParameters,
)
