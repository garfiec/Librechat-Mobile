package com.garfiec.librechat.core.model.response

import kotlinx.serialization.Serializable

/**
 * Ack for an accepted `POST /api/agents/chat/steer` (HTTP 202).
 *
 * Acceptance means *queued*, not *applied*: the run injects the steer at its next tool-batch
 * boundary and only then emits `on_steer_applied`. [steerId] is the handle for both — cancelling
 * before injection, and matching the applied event to the chip that represents it.
 */
@Serializable
data class SteerResponse(
    /** `"queued"` on success. */
    val status: String? = null,
    val steerId: String? = null,
    /** Depth in the server-side queue after enqueue (1 = next to inject). */
    val position: Int? = null,
    val conversationId: String? = null,
    /**
     * Echoed `true` when the durable item carries the quotes that were sent (v0.8.8-rc2).
     *
     * **Absent is the signal**, and it is not an error: a pre-quotes server 202s while dropping
     * them. It is also NOT the moment to re-stage — the steer is still queued and will still
     * inject, so the excerpts stay on the record's fallback spec. See `SteeringDelegate`, which
     * documents the four places a dropped excerpt can be recovered.
     */
    val quotesAccepted: Boolean? = null,
    /**
     * Whether the `preempt` seal was actually armed. Never a rejection: a deployment whose SDK
     * cannot seal mid-stream still queues the steer and echoes `false`. Decode-only — mobile
     * never asks to preempt, so this is always absent or false today.
     */
    val preempt: Boolean? = null,
    /** Monotonic server revision for last-writer-wins preempt labels. Decode-only. */
    val preemptRevision: Int? = null,
    /** A receipt replayed after the item already left the durable queue (v2 receipts). */
    val settled: Boolean? = null,
    /**
     * Settled specifically by terminal drain, i.e. the run ended without injecting it. The words
     * belong in the follow-up queue, where a normal send delivers their quotes on any server.
     */
    val leftover: Boolean? = null,
    val replayed: Boolean? = null,
    val generationProtocolVersion: Int? = null,
)

/**
 * Ack for `POST /api/agents/chat/steer/cancel`.
 *
 * [removed] false is a 200, not an error: the cancel lost its race (the steer was already
 * injected, or the run ended), and the client should defer to the events it will receive
 * rather than treat the text as still pending.
 */
@Serializable
data class SteerCancelResponse(
    val removed: Boolean = false,
)
