package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.model.pullrequest.ConversationPullRequestResponse
import com.garfiec.librechat.core.model.pullrequest.ConversationPullRequestsRequest
import com.garfiec.librechat.core.model.pullrequest.ConversationPullRequestsResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.encodeURLPathPart
import io.ktor.http.path

/**
 * Conversation pull requests (v0.8.9-rc1, `api/server/routes/convos.js`). Both routes are JWT and
 * owner-scoped; a chat with no pull request (or another user's, or the feature off) answers null,
 * not an error. The account-connection routes under `/api/pull-requests/oauth` are not built.
 */
class PullRequestsApi(private val client: HttpClient) {
    /**
     * `GET /api/convos/:id/pull-request`. 404 for a missing id; 503 `{code}` (`NOT_CONFIGURED`,
     * `RATE_LIMITED`, `UPSTREAM_ERROR`); 500 otherwise.
     */
    suspend fun pullRequest(conversationId: String): ConversationPullRequestResponse =
        client.get {
            url { path("api/convos/${conversationId.encodeURLPathPart()}/pull-request") }
        }.body()

    /**
     * `POST /api/convos/pull-requests` with a bare `{conversationIds}` (not `arg`-wrapped), at most
     * `PULL_REQUEST_BATCH_MAX` ids. The server answers by its own deadline (up to 120 s), so the
     * client-wide 30 s cap is lifted to wait just past it.
     */
    suspend fun pullRequests(conversationIds: List<String>): ConversationPullRequestsResponse =
        client.post {
            url { path("api/convos/pull-requests") }
            timeout { requestTimeoutMillis = BATCH_TIMEOUT_MS }
            setBody(ConversationPullRequestsRequest(conversationIds))
        }.body()

    private companion object {
        /** Upstream `PULL_REQUEST_BATCH_TIMEOUT_MAX_SECONDS` (120) plus a margin. */
        const val BATCH_TIMEOUT_MS = 125_000L
    }
}
