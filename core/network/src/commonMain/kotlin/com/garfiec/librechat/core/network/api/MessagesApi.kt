package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.model.Message
import com.garfiec.librechat.core.model.MinimalFeedback
import com.garfiec.librechat.core.model.request.BranchMessageRequest
import com.garfiec.librechat.core.model.request.FeedbackRequest
import com.garfiec.librechat.core.model.request.UpdateMessageRequest
import com.garfiec.librechat.core.model.response.MessageSearchResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.path

class MessagesApi constructor(
    private val client: HttpClient,
) {
    suspend fun getMessages(conversationId: String): List<Message> =
        client.get {
            url { path("api/messages/$conversationId") }
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

    suspend fun branchMessage(request: BranchMessageRequest): Message =
        client.post {
            url { path("api/messages/branch") }
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
