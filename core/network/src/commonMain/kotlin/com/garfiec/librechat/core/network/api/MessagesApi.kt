package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.MinimalFeedback
import com.garfiec.librechat.core.model.request.BranchMessageRequest
import com.garfiec.librechat.core.model.request.FeedbackRequest
import com.garfiec.librechat.core.model.request.OwnerTextRequest
import com.garfiec.librechat.core.model.request.UpdateMessageRequest
import com.garfiec.librechat.core.model.response.MessageSearchResponse
import com.garfiec.librechat.core.model.response.OwnerTextResponse
import com.garfiec.librechat.core.model.response.SubagentAttachmentCallsResponse
import com.garfiec.librechat.core.model.response.SubagentAttachmentFile
import com.garfiec.librechat.core.model.response.ToolCallPartResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.retry
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.appendPathSegments
import io.ktor.http.path

class MessagesApi constructor(
    private val client: HttpClient,
) {
    /**
     * A conversation's messages. With [toolPreviews] a v0.8.9-rc1 server shortens settled tool calls
     * and marks them (`AgentToolCall.isPreview`); a server without the feature, or with it turned
     * off, ignores the parameter and sends everything in full with no markers.
     */
    suspend fun getMessages(conversationId: String, toolPreviews: Boolean): List<Message> =
        client.get {
            url { path("api/messages/$conversationId") }
            if (toolPreviews) withToolCallPreviews()
        }.body()

    /**
     * A conversation's messages in full, tried once: an export the user is waiting on. Its caller
     * falls back to the cache on failure, and the client's retry backoff would only hold that answer
     * back (several seconds offline) after the user has moved on.
     */
    suspend fun getMessagesOnce(conversationId: String): List<Message> =
        client.get {
            url { path("api/messages/$conversationId") }
            retry { noRetry() }
        }.body()

    /**
     * One stored tool call in full (v0.8.9-rc1), found by its identity: [toolCallId], plus [stepId]
     * and [agentId] when the call has them (an absent one only matches a call that has none).
     * [partIndex] is the raw index into `Message.content`, which the server only echoes. Every id
     * is an encoded path segment or query value: provider call ids can carry `|` and the like.
     */
    suspend fun getToolCallPart(
        conversationId: String,
        messageId: String,
        partIndex: Int,
        toolCallId: String,
        stepId: String?,
        agentId: String?,
    ): ToolCallPartResponse =
        client.get {
            url {
                path("api/messages")
                appendPathSegments(listOf(conversationId, messageId), encodeSlash = true)
                appendPathSegments("parts", partIndex.toString())
            }
            parameter("toolCallId", toolCallId)
            stepId?.takeIf { it.isNotEmpty() }?.let { parameter("stepId", it) }
            agentId?.takeIf { it.isNotEmpty() }?.let { parameter("agentId", it) }
        }.body()

    /**
     * `GET /api/messages/:conversationId/:messageId/subagent-calls?files=[…]` (v0.8.9-rc1): which
     * child calls of the stored message's subagent transcripts [files] were saved under. [files] is
     * sent as a JSON array (at most [SUBAGENT_CALLS_BATCH]; upstream answers 400 past 32), with blank
     * scopes left out. Ids are encoded path segments.
     */
    suspend fun getSubagentAttachmentCalls(
        conversationId: String,
        messageId: String,
        files: List<SubagentAttachmentFile>,
    ): SubagentAttachmentCallsResponse =
        client.get {
            url {
                path("api/messages")
                appendPathSegments(listOf(conversationId, messageId), encodeSlash = true)
                appendPathSegments("subagent-calls")
            }
            parameter("files", encodeAttachmentFiles(files))
        }.body()

    /**
     * The server's full-text message search (`GET /api/messages?search=`, Meilisearch). One page,
     * no cursor. [pageSize] applies twice: at most that many hits, kept only if they fall in that
     * many of the most recently updated matching conversations (archived ones included). The web
     * client sends none, so the server's default of 25 applies there; v0.8.8 caps a larger one at
     * `MEILI_SEARCH_LIMIT` (1000). A server before v0.8.8-rc2 doesn't pass it to Meilisearch, so it
     * answers Meilisearch's own default of at most 20 hits whatever was asked. Each hit is the
     * stored message overlaid with the search hit, plus its conversation's `title` and `model`; its
     * `text` is the server's flatten of `content` with steer parts included.
     */
    suspend fun searchMessages(query: String, pageSize: Int = SEARCH_PAGE_SIZE): MessageSearchResponse =
        client.get {
            url { path("api/messages") }
            parameter("search", query)
            parameter("pageSize", pageSize)
        }.body()

    suspend fun updateMessage(conversationId: String, messageId: String, request: UpdateMessageRequest): Message =
        client.put {
            url { path("api/messages/$conversationId/$messageId") }
            setBody(request)
        }.body()

    /**
     * The author's original text of redacted user turns (v0.8.9-rc1). At most 50 ids per call; a
     * 503 means the server can't open them now.
     */
    suspend fun getOwnerTexts(conversationId: String, request: OwnerTextRequest): OwnerTextResponse =
        client.post {
            url { path("api/messages/$conversationId/owner-text") }
            setBody(request)
        }.body()

    /** Only the response is shaped by the preview opt-in; the copy is made server-side, in full. */
    suspend fun branchMessage(request: BranchMessageRequest): Message =
        client.post {
            url { path("api/messages/branch") }
            withToolCallPreviews()
            setBody(request)
        }.body()

    /** Submits [feedback], or clears it when null. */
    suspend fun updateFeedback(conversationId: String, messageId: String, feedback: MinimalFeedback?) {
        client.put {
            url { path("api/messages/$conversationId/$messageId/feedback") }
            setBody(FeedbackRequest(feedback = feedback))
        }
    }

    companion object {
        /**
         * Hits asked for per search: the server's ceiling (`MEILI_SEARCH_LIMIT`), so a common word
         * lists every match it can find. Each hit is a full stored message, so a common word's
         * reply runs to megabytes; it is decoded off the main thread. The server's own default,
         * 25, which the web client gets, ran out on common words.
         */
        const val SEARCH_PAGE_SIZE: Int = 1000

        /**
         * What a server that doesn't pass `pageSize` to Meilisearch (before v0.8.8-rc2) answers with
         * at most: Meilisearch's default `limit`, not the route's default page of 25 conversations.
         */
        const val SERVER_DEFAULT_PAGE_SIZE: Int = 20
    }
}

/** Files per `subagent-calls` request: upstream `MAX_ATTACHMENT_CALL_QUERIES` / `SUBAGENT_ATTACHMENT_CALLS_BATCH`. */
const val SUBAGENT_CALLS_BATCH: Int = 32

/** [files] as the route's `files` query: a JSON array of `{toolCallId, agentId?, stepId?}`, blanks left out. */
internal fun encodeAttachmentFiles(files: List<SubagentAttachmentFile>): String =
    kotlinx.serialization.json.buildJsonArray {
        files.forEach { file ->
            add(
                kotlinx.serialization.json.buildJsonObject {
                    put("toolCallId", kotlinx.serialization.json.JsonPrimitive(file.toolCallId))
                    file.agentId?.takeIf { it.isNotEmpty() }?.let { put("agentId", kotlinx.serialization.json.JsonPrimitive(it)) }
                    file.stepId?.takeIf { it.isNotEmpty() }?.let { put("stepId", kotlinx.serialization.json.JsonPrimitive(it)) }
                },
            )
        }
    }.toString()
