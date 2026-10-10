package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.model.queuedturn.CancelQueuedTurnResponse
import com.garfiec.librechat.core.model.queuedturn.EnqueueQueuedTurnRequest
import com.garfiec.librechat.core.model.queuedturn.EnqueueQueuedTurnResponse
import com.garfiec.librechat.core.model.queuedturn.ListQueuedTurnsResponse
import com.garfiec.librechat.core.model.queuedturn.MAX_QUEUED_TURN_LIST_IDS
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.encodeURLPathPart
import io.ktor.http.path

/**
 * Server-side queued turns (v0.8.8-rc2). A turn handed to these routes is admitted and RUN by the
 * server; the client must not also send it.
 *
 * There is no push channel — reconnect is client-driven reconciliation. [listQueuedTurns] is polled
 * with the client's OWN known ids so the server returns exact proof for the rows the client
 * believes exist.
 */
class QueuedTurnsApi constructor(
    private val client: HttpClient,
) {
    /**
     * Enqueues a turn, or resolves an existing one when [EnqueueQueuedTurnRequest.clientRequestId]
     * has been seen before. 202 for a live row, 200 for a replay of a settled one — both carry the
     * receipt, so the caller need not distinguish.
     */
    suspend fun enqueueQueuedTurn(request: EnqueueQueuedTurnRequest): EnqueueQueuedTurnResponse =
        client.post {
            url { path("api/agents/chat/queued-turns") }
            setBody(request)
        }.body()

    /**
     * Lists the conversation's queued turns.
     *
     * [clientRequestIds] is deduped and capped here rather than at the call site: the server's zod
     * schema rejects more than [MAX_QUEUED_TURN_LIST_IDS], and a rejected poll would look like a
     * dead queue rather than a client error.
     */
    suspend fun listQueuedTurns(
        conversationId: String,
        clientRequestIds: List<String> = emptyList(),
    ): ListQueuedTurnsResponse =
        client.get {
            url { path("api/agents/chat/queued-turns") }
            parameter("conversationId", conversationId)
            clientRequestIds.distinct().take(MAX_QUEUED_TURN_LIST_IDS).forEach {
                parameter("clientRequestIds", it)
            }
        }.body()

    /** Withdraws a queued turn. Only wins against a row still `queued` or `claimed`. */
    suspend fun cancelQueuedTurn(queuedTurnId: String): CancelQueuedTurnResponse =
        client.delete {
            url { path("api/agents/chat/queued-turns/${queuedTurnId.encodeURLPathPart()}") }
        }.body()
}
