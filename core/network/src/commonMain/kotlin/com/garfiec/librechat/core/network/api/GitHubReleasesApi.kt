package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.network.api.dto.GitHubRelease
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders

/**
 * Reads the app's own release list from GitHub for the opt-in update check.
 *
 * [client] must be the `KoinQualifiers.External` client: the LibreChat clients attach the
 * server's tokens and gateway headers, none of which may ever reach a third-party host.
 */
class GitHubReleasesApi(private val client: HttpClient) {

    /** Newest first, as GitHub orders them. Anonymous callers never see drafts. */
    suspend fun listReleases(): List<GitHubRelease> =
        client.get(RELEASES_URL) {
            header(HttpHeaders.Accept, "application/vnd.github+json")
            parameter("per_page", PAGE_SIZE)
        }.body()

    companion object {
        const val REPO_URL = "https://github.com/garfiec/Librechat-Mobile"
        private const val RELEASES_URL = "https://api.github.com/repos/garfiec/Librechat-Mobile/releases"
        private const val PAGE_SIZE = 30
    }
}
