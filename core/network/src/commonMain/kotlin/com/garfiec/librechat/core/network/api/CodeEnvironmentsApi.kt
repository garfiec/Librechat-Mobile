package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.model.code.CodeEnvironmentMoveRequest
import com.garfiec.librechat.core.model.code.CodeEnvironmentMoveResponse
import com.garfiec.librechat.core.model.code.CodeEnvironmentPairingRequest
import com.garfiec.librechat.core.model.code.CodeEnvironmentPairingResponse
import com.garfiec.librechat.core.model.code.CodeEnvironmentResponse
import com.garfiec.librechat.core.model.code.CodeEnvironmentSettingsRequest
import com.garfiec.librechat.core.model.code.CodeEnvironmentStatus
import com.garfiec.librechat.core.model.code.CodeEnvironmentUserSettings
import com.garfiec.librechat.core.model.code.CodeEnvironmentsResponse
import com.garfiec.librechat.core.model.code.CodeSetupApprovalRequest
import com.garfiec.librechat.core.model.code.CodeSetupAttempt
import com.garfiec.librechat.core.network.client.noServerErrorRetry
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.encodeURLPathPart
import io.ktor.http.path
import kotlinx.serialization.json.JsonObject

/**
 * Attached code machines (`api/server/routes/code-environments.js`). Every route here is plain
 * JWT; the setup approval routes also run `requireSameOrigin`, which passes a request carrying
 * neither `Origin` nor `Sec-Fetch-Site`, and this client sends neither (see DISCOVERY.md).
 */
class CodeEnvironmentsApi constructor(
    private val client: HttpClient,
) {
    /**
     * A machine's live state and the workspaces its worker registered. `404` unknown machine,
     * `503` not configured or busy, `504` timeout, `502` anything else. Behind an IP and a per-user
     * rate limiter, so callers poll at web's 10 s, never faster. A 5xx is not retried here: the
     * caller already waits 30 s after a 503/504, and the shared retry would triple each one.
     */
    suspend fun status(environmentId: String): CodeEnvironmentStatus =
        client.get {
            noServerErrorRetry()
            url { path("api/code-environments/${environmentId.encodeURLPathPart()}/status") }
        }.body()

    /**
     * Replaces a saved chat's recorded decision: a move, an attach (from "No workspace"), a detach
     * (empty [CodeEnvironmentMoveRequest.to]) or a recovery. The body is NOT `arg`-wrapped. `from`
     * must equal what the chat stores, or the server answers 409 `locked`. `403` moves (or
     * attach/detach) are off, `404` unknown chat, `409` without a `code` a response is running,
     * `409`/`400` `{code, reason}` a refused selection, `503` policy unavailable. Shares the status
     * route's rate limiters.
     */
    suspend fun moveDecision(conversationId: String, request: CodeEnvironmentMoveRequest): CodeEnvironmentMoveResponse =
        client.patch {
            url { path("api/code-environments/conversations/${conversationId.encodeURLPathPart()}/decision") }
            setBody(request)
        }.body()

    /** The machines this user can see, and the control planes a personal worker may pair with. `503` policy unavailable. */
    suspend fun list(): CodeEnvironmentsResponse =
        client.get { url { path("api/code-environments") } }.body()

    /**
     * Registers a personal machine and returns its one-time pairing. Rate-limited per user (429).
     * `409` principal workers off / account being deleted / already exists / limit reached, `404`
     * control plane gone, `403` enrollment disabled, `503` not configured, `502`/`504` the Code API.
     */
    suspend fun pair(request: CodeEnvironmentPairingRequest): CodeEnvironmentPairingResponse =
        client.post {
            url { path("api/code-environments/pairings") }
            setBody(request)
        }.body()

    /** `400` a setting the machine doesn't offer, `404` gone, `503` unavailable. */
    suspend fun updateSettings(environmentId: String, settings: CodeEnvironmentUserSettings): CodeEnvironmentResponse =
        client.patch {
            url { path("api/code-environments/${environmentId.encodeURLPathPart()}/settings") }
            setBody(CodeEnvironmentSettingsRequest(settings))
        }.body()

    /** Revokes the worker, then removes the machine. `409` an agent still uses it, `502`/`504` revoke failed. */
    suspend fun remove(environmentId: String): CodeEnvironmentResponse =
        client.delete { url { path("api/code-environments/${environmentId.encodeURLPathPart()}") } }.body()

    /** A worker's setup request. Errors are `{code}` only: 404 `SETUP_NOT_FOUND` (or another account's). */
    suspend fun setupAttempt(attemptId: String): CodeSetupAttempt =
        client.get { url { path("api/code-environments/setup/${attemptId.encodeURLPathPart()}") } }.body()

    /**
     * Approves the attempt for [CodeSetupApprovalRequest.controlPlaneId]. Behind `requireSameOrigin`,
     * which passes this client because it sends neither `Origin` nor `Sec-Fetch-Site`: keep it that way.
     */
    suspend fun approveSetup(attemptId: String, request: CodeSetupApprovalRequest): CodeSetupAttempt =
        client.post {
            url { path("api/code-environments/setup/${attemptId.encodeURLPathPart()}/approve") }
            setBody(request)
        }.body()

    /** Denies the attempt. The body must be exactly `{}`. */
    suspend fun denySetup(attemptId: String): CodeSetupAttempt =
        client.post {
            url { path("api/code-environments/setup/${attemptId.encodeURLPathPart()}/deny") }
            setBody(JsonObject(emptyMap()))
        }.body()
}
