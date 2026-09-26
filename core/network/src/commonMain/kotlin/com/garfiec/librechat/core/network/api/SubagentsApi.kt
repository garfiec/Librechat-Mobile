package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.model.subagent.SubagentIndex
import com.garfiec.librechat.core.model.subagent.SubagentThreadView
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.encodeURLPathPart
import io.ktor.http.path

/**
 * Read-only views of a conversation's child threads (v0.8.8-rc2).
 *
 * The control route (`POST …/:threadId/control`) and the per-task activity SSE stream are
 * deliberately absent — see `DISCOVERY.md`. Everything here is a GET.
 */
class SubagentsApi constructor(
    private val client: HttpClient,
) {
    /**
     * The children of [parentConversationId].
     *
     * A parent with none answers `200` with an empty list. A `404` means the conversation was not
     * found, is not the caller's, or **is itself a subagent thread** — one body for three
     * conditions, none of which is "the route does not exist". It must not be read as a verdict
     * about the server.
     */
    suspend fun getIndex(parentConversationId: String): SubagentIndex =
        client.get {
            url { path("api/convos/${parentConversationId.encodeURLPathPart()}/subagents") }
        }.body()

    /** The child's latest bounded page. */
    suspend fun getThread(parentConversationId: String, threadId: String): SubagentThreadView =
        client.get { url { path(threadPath(parentConversationId, threadId)) } }.body()

    /**
     * The child's activity for ONE execution boundary.
     *
     * Split from [getThreadPage] rather than sharing one method with two optional parameters:
     * sending `taskId` and `cursor` together is a `404`, and two methods make that unrepresentable
     * instead of a runtime failure.
     */
    suspend fun getThreadTask(
        parentConversationId: String,
        threadId: String,
        taskId: String,
    ): SubagentThreadView =
        client.get {
            url { path(threadPath(parentConversationId, threadId)) }
            parameter("taskId", taskId)
        }.body()

    /** The page older than [cursor], which must be a `nextCursor` a previous page returned. */
    suspend fun getThreadPage(
        parentConversationId: String,
        threadId: String,
        cursor: String,
    ): SubagentThreadView =
        client.get {
            url { path(threadPath(parentConversationId, threadId)) }
            parameter("cursor", cursor)
        }.body()

    private fun threadPath(parentConversationId: String, threadId: String): String =
        "api/convos/${parentConversationId.encodeURLPathPart()}/subagents/" +
            threadId.encodeURLPathPart()
}
