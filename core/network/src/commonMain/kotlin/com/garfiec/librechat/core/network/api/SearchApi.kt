package com.garfiec.librechat.core.network.api

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.path

/** The server's Meilisearch availability probe. */
class SearchApi(
    private val client: HttpClient,
) {
    /**
     * `GET /api/search/enable` answers a bare `true`/`false`: true only when the server has
     * `SEARCH` set and Meilisearch reports healthy. Anything else reads as disabled.
     */
    suspend fun isSearchEnabled(): Boolean =
        client.get { url { path("api/search/enable") } }
            .bodyAsText()
            .trim()
            .toBooleanStrictOrNull() ?: false
}
