package com.garfiec.librechat.core.model.trace

import kotlinx.serialization.Serializable

/**
 * The conversation trace viewer (v0.8.8-rc3): a provider-neutral read of what the tracing backend
 * recorded for a conversation's turns.
 *
 * Provider-neutral is the contract, not a description — the server maps whatever its backend
 * stores into these shapes, so the client never sees a backend's field names, credentials, URL
 * structure or API version. Nothing here should grow a Langfuse-shaped field.
 */

object TraceRecordKind {
    const val AGENT = "agent"
    const val GENERATION = "generation"
    const val TOOL = "tool"
    const val SPAN = "span"
    const val EVENT = "event"
}

object TraceStatus {
    const val OK = "ok"
    const val WARNING = "warning"
    const val ERROR = "error"

    /** A record with a start and no end yet, so it has no duration. */
    const val RUNNING = "running"
}

@Serializable
data class TraceUsage(
    val input: Long? = null,
    val output: Long? = null,
    val total: Long? = null,
    val reasoning: Long? = null,
    val cacheRead: Long? = null,
    val cacheWrite: Long? = null,
)

@Serializable
data class TraceRecord(
    val id: String,
    val traceId: String = "",
    /** The response message whose turn produced this record. **This is what groups records.** */
    val messageId: String = "",
    /** Null for a root. May name a record that is not loaded, or does not exist. */
    val parentId: String? = null,
    /** Raw String, not an enum: an unrecognized kind must degrade one row, not fail the page. */
    val kind: String = "",
    val name: String = "",
    val model: String? = null,
    val startTime: String = "",
    val endTime: String? = null,
    /** First streamed token of a generation; splits its span into time-to-first-token and decoding. */
    val completionStartTime: String? = null,
    val status: String = "",
    val statusMessage: String? = null,
    val usage: TraceUsage? = null,
    /**
     * Total cost in USD when the backend priced the record.
     *
     * Already filtered server-side by `interface.contextCost` — the same switch that shows costs in
     * chat — so it is rendered when present and gated on nothing here. Do not add a client check.
     */
    val cost: Double? = null,
)

/**
 * `GET /api/traces/:conversationId/availability`.
 *
 * **This route never fails.** Disabled, not found, not owned and every other refusal answer
 * `200 { available: false }`; only an unexpected throw is a 500. So the entry point's whole
 * precondition is `available == true`, with no error arm to reason about.
 */
@Serializable
data class TraceAvailability(
    val available: Boolean = false,
    /** Set when the backend cannot decide yet. Ask again after this long — do not poll without it. */
    val retryAfterMs: Long? = null,
)

/**
 * `GET /api/traces/:conversationId/records`.
 *
 * Newest turns first; [nextCursor] loads the next older page. **A turn's records may continue on
 * the following page, and their order within a turn is undefined**, so a client accumulates pages
 * and regroups rather than rendering each page as a list — see `groupTraceRecords`.
 */
@Serializable
data class TracePage(
    val records: List<TraceRecord> = emptyList(),
    val nextCursor: String? = null,
    /**
     * Opaque identity of the backend project that served this page.
     *
     * **It has to travel to the detail read.** A multi-project deployment can serve records from
     * different sources across pages, and a detail fetched without the page's own source reads the
     * wrong project — or nothing.
     */
    val sourceId: String? = null,
)

@Serializable
data class TraceContent(
    val value: String = "",
    val truncated: Boolean = false,
)

/** `GET /api/traces/:conversationId/records/:recordId?message=&source=`. */
@Serializable
data class TraceRecordDetail(
    val record: TraceRecord,
    /** False when the deployment withholds input, output and metadata. Not an error. */
    val contentAvailable: Boolean = false,
    val input: TraceContent? = null,
    val output: TraceContent? = null,
    val metadata: TraceContent? = null,
)

/**
 * `errorCode` on a trace failure. The accompanying `error` string is written to be shown, so it is
 * rendered rather than re-worded — these exist to tell apart what a user can act on.
 */
object TraceErrorCode {
    /** 404. Should not reach a surface the availability precondition gates. */
    const val DISABLED = "disabled"

    /** 404. Same. */
    const val NOT_FOUND = "not_found"

    const val INVALID_REQUEST = "invalid_request"

    /** 429 — the per-user read limiter. Waiting is the remedy, and the only one. */
    const val RATE_LIMITED = "rate_limited"

    /** 504 — the tracing backend did not answer in time. Retrying is reasonable. */
    const val TIMEOUT = "timeout"

    /** 502 — the tracing backend rejected the server's credentials. An operator's problem. */
    const val UNAUTHORIZED = "unauthorized"

    /** 501 — the tracing backend cannot serve reads at all. */
    const val UNSUPPORTED = "unsupported"

    /** 502. */
    const val UPSTREAM_ERROR = "upstream_error"
}

@Serializable
data class TraceErrorResponse(
    val error: String = "",
    val errorCode: String = "",
)
