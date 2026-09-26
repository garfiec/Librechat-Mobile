package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.trace.TraceAvailability
import com.garfiec.librechat.core.model.trace.TracePage
import com.garfiec.librechat.core.model.trace.TraceRecordDetail

/**
 * Conversation trace reads (v0.8.8-rc3). Server is the sole source of truth; no cache.
 *
 * **Nothing here latches.** `/availability` answers `200 { available: false }` for every refusal
 * including a server that has the feature switched off, so there is no response shape that means
 * "this deployment does not have the routes" and nothing to latch on. Suppression on a proven-old
 * server is a version check ([isRuledOutForServer]) instead — and in practice the interface flag
 * does the work on its own, since a pre-rc3 server's config schema strips `traceViewer` before it
 * is ever served.
 */
interface TraceRepository {

    /**
     * Whether this server is KNOWN to predate the routes, so nothing should be requested.
     *
     * Three-state underneath: only an `ABSENT` verdict suppresses, so an unplaceable server is
     * asked. Cheap here — the ask is gated behind `interface.traceViewer` being present at all.
     */
    fun isRuledOutForServer(): Boolean

    /**
     * Resolves whether this conversation has a readable trace, waiting out the backend's own
     * "ask again" and retrying a server fault a few times.
     *
     * A precondition, not an after-open call: the entry point does not render until this says
     * true. Suspends across the backend's `retryAfterMs` waits, so callers run it in a scope they
     * cancel when the conversation changes.
     */
    suspend fun resolveAvailability(conversationId: String): Result<TraceAvailability>

    /** Newest turns first. Pass the previous page's `nextCursor` for the next older one. */
    suspend fun getRecords(conversationId: String, cursor: String? = null): Result<TracePage>

    /**
     * One record's detail.
     *
     * [messageId] comes from the record and [sourceId] from the page that listed it — a
     * multi-project deployment serves records from different sources across pages, and a detail
     * read carrying the wrong one reads the wrong project.
     */
    suspend fun getRecord(
        conversationId: String,
        recordId: String,
        messageId: String,
        sourceId: String?,
    ): Result<TraceRecordDetail>
}
