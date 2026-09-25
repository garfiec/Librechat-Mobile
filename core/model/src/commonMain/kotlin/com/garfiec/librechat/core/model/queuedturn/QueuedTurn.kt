package com.garfiec.librechat.core.model.queuedturn

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Server-side queued turns (v0.8.8-rc2): a follow-up the SERVER holds and admits itself when the
 * run it follows completes, rather than one this client sends on its own drain.
 *
 * The identity that makes this safe is `clientRequestId`. A UNIQUE index on
 * `(tenantId, user, conversationId, clientRequestId)` means a re-POST with the same id resolves to
 * the existing row instead of creating a second one — so a retry after a lost response is free.
 * **That protection is entirely conditional on the id being STABLE across retries**; minting a
 * fresh one per attempt defeats it and enqueues the turn twice.
 */

/** Lifecycle of a queued turn. Terminal: [CANCELLED], [DEAD]. */
object QueuedTurnStatus {
    /** Accepted and waiting; the only status a cancel can still win against (with [CLAIMED]). */
    const val QUEUED = "queued"

    /** The server has taken it for admission. */
    const val CLAIMED = "claimed"

    /** The server started its run. Nothing further is owed on the receipt. */
    const val ADMITTED = "admitted"

    /** Withdrawn, by this client or another. */
    const val CANCELLED = "cancelled"

    /** The server gave up on it; [AgentQueuedTurnReceipt.failure] says why. */
    const val DEAD = "dead"

    /** Whether the server still owes this turn something — drives the reconcile poll. */
    fun isUnsettled(status: String?): Boolean = status == QUEUED || status == CLAIMED
}

/** Wire values of [AgentQueuedTurnCapability.durability]. */
object QueuedTurnDurability {
    /** The queue does not survive a server restart. */
    const val PROCESS_LOCAL = "process_local"

    /** The queue is persisted. **Every rc3 response hardcodes this** — see the capability KDoc. */
    const val DURABLE = "durable"
}

@Serializable
data class QueuedTurnFailure(
    val code: String? = null,
    val message: String? = null,
) {
    companion object {
        /**
         * The server cannot tell whether this turn's admission started a run. Upstream stops
         * polling such a row — a two-second loop cannot resolve it, and continuing would disguise
         * permanent quarantine as ordinary progress.
         */
        const val ADMISSION_INDETERMINATE = "ADMISSION_INDETERMINATE"
    }
}

/** A file already uploaded, referenced by a queued turn. */
@Serializable
data class QueuedTurnFileRef(
    @SerialName("file_id") val fileId: String,
    val type: String? = null,
    val filepath: String? = null,
    val filename: String? = null,
    val height: Int? = null,
    val width: Int? = null,
    val bytes: Long? = null,
    val llmDeliveryPath: String? = null,
)

/**
 * The server's record of one queued turn. Upstream's schema `.extend`s the enqueue body, so the
 * receipt echoes back everything that was submitted alongside the server's own fields.
 *
 * [status] is a raw string rather than an enum on purpose: it rides inside a response body, and an
 * unrecognized enum value throws at DECODE time — `ignoreUnknownKeys` does not rescue it — which
 * would fail the whole list rather than degrading one row. Compare against [QueuedTurnStatus].
 */
@Serializable
data class AgentQueuedTurnReceipt(
    val queuedTurnId: String,
    val clientRequestId: String,
    val conversationId: String? = null,
    val parentMessageId: String? = null,
    val text: String = "",
    val status: String? = null,
    /** Immutable queue sequence. Not a version counter — it never rewinds for a given row. */
    val revision: Long? = null,
    /** 1-based depth among the rows still `queued`/`claimed`; settled rows are not numbered. */
    val position: Int? = null,
    val failure: QueuedTurnFailure? = null,
    val files: List<QueuedTurnFileRef>? = null,
    val quotes: List<String>? = null,
    val manualSkills: List<String>? = null,
    val priority: Boolean? = null,
    /** The boundary captured at enqueue. */
    val expectedPredecessorCreatedAt: Long? = null,
    /**
     * The boundary the admission actually consumed. It can advance past
     * [expectedPredecessorCreatedAt] as queued turns chain, so the two are not interchangeable.
     */
    val effectivePredecessorCreatedAt: Long? = null,
    /** Present, and only ever `true`, when the admission consumed no predecessor boundary. */
    val rootPredecessor: Boolean? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
) {
    val isUnsettled: Boolean get() = QueuedTurnStatus.isUnsettled(status)

    /** Admission could not be confirmed; polling this row cannot resolve it. */
    val isAdmissionIndeterminate: Boolean
        get() = failure?.code == QueuedTurnFailure.ADMISSION_INDETERMINATE
}

/**
 * Whether this server runs a durable queue at all.
 *
 * `supported = false` and an absent capability are the same answer for the client: do not hand it
 * turns. The union is modelled flat rather than as a discriminated one because a `false` carries no
 * durability and a flat shape decodes both arms without a custom serializer.
 *
 * **[durability] is decode surface only.** Every v0.8.8-rc3 response hardcodes `"durable"`
 * (`packages/api/src/agents/queuedTurnHttp.ts:31`, used at all six sites), so `process_local` is
 * unreachable today — nothing may branch on it. Note the `process_local` in upstream's
 * `IJobStore.ts` belongs to a different union for a different subsystem.
 */
@Serializable
data class AgentQueuedTurnCapability(
    val supported: Boolean = false,
    val durability: String? = null,
)
