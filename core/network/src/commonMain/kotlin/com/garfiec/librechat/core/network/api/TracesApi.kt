package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.model.trace.TraceAvailability
import com.garfiec.librechat.core.model.trace.TracePage
import com.garfiec.librechat.core.model.trace.TraceRecordDetail
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.encodeURLPathPart
import io.ktor.http.path

/**
 * Conversation trace reads (v0.8.8-rc3). Three routes, all GETs.
 *
 * There is no `GET /api/traces/:conversationId` — the section is reached only through
 * [getAvailability], [getRecords] and [getRecord].
 */
class TracesApi constructor(
    private val client: HttpClient,
) {
    /**
     * Whether this conversation has a readable trace.
     *
     * **Never fails.** Disabled, not found and not-owned all answer `200 { available: false }`, so
     * there is no error arm here to reason about — only an unexpected server fault is non-2xx.
     * Deliberately not rate-limited server-side, unlike the two record routes.
     */
    suspend fun getAvailability(conversationId: String): TraceAvailability =
        client.get { url { path("${base(conversationId)}/availability") } }.body()

    /** Newest turns first. Pass the previous page's `nextCursor` for the next older one. */
    suspend fun getRecords(conversationId: String, cursor: String? = null): TracePage =
        client.get {
            url { path("${base(conversationId)}/records") }
            cursor?.let { parameter("cursor", it) }
        }.body()

    /**
     * One record's detail.
     *
     * [messageId] is REQUIRED — it is the turn the list attributed the record to, and its traces
     * are what authorize the read, so it is taken from the record rather than being optional.
     * [sourceId] is the page's own `sourceId`: a multi-project deployment serves records from
     * different sources across pages, and a detail read without it can reach the wrong project.
     */
    suspend fun getRecord(
        conversationId: String,
        recordId: String,
        messageId: String,
        sourceId: String? = null,
    ): TraceRecordDetail =
        client.get {
            url { path("${base(conversationId)}/records/${recordId.encodeURLPathPart()}") }
            parameter("message", messageId)
            sourceId?.let { parameter("source", it) }
        }.body()

    private fun base(conversationId: String): String =
        "api/traces/${conversationId.encodeURLPathPart()}"
}
