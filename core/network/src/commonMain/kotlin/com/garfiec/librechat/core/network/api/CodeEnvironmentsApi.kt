package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.model.code.CodeEnvironmentStatus
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.encodeURLPathPart
import io.ktor.http.path

/**
 * Attached code machines (`api/server/routes/code-environments.js`). Only the status read is used:
 * machine setup and pairing need a same-origin browser session, and moving a saved chat's decision
 * (`PATCH …/conversations/:id/decision`) is not built (see DISCOVERY.md).
 */
class CodeEnvironmentsApi constructor(
    private val client: HttpClient,
) {
    /**
     * A machine's live state and the workspaces its worker registered. `404` unknown machine,
     * `503` not configured or busy, `504` timeout, `502` anything else. Behind an IP and a per-user
     * rate limiter, so callers poll at web's 10 s, never faster.
     */
    suspend fun status(environmentId: String): CodeEnvironmentStatus =
        client.get {
            url { path("api/code-environments/${environmentId.encodeURLPathPart()}/status") }
        }.body()
}
