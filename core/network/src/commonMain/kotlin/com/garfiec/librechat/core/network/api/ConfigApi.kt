package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.model.response.Category
import com.garfiec.librechat.core.network.client.ServerProbeKey
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.path
import kotlinx.serialization.json.Json

class ConfigApi constructor(
    private val client: HttpClient,
) {
    suspend fun getStartupConfig(): StartupConfig =
        client.get {
            url { path("api/config") }
        }.body()

    /** The server check: stops at a redirect with [ServerRedirectException] instead of following it. */
    suspend fun probeStartupConfig(): StartupConfig =
        client.get {
            url { path("api/config") }
            attributes.put(ServerProbeKey, Unit)
        }.body()

    private val lenientJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * Fetches configured endpoint configs. The server may return JSON with
     * Content-Type: text/html, so we read the raw body and deserialize manually.
     */
    suspend fun getEndpoints(): Map<String, EndpointConfig> {
        val response = client.get {
            url { path("api/endpoints") }
        }
        val text = response.bodyAsText()
        return lenientJson.decodeFromString(text)
    }

    suspend fun getModels(): Map<String, List<String>> =
        client.get {
            url { path("api/models") }
        }.body()

    suspend fun getCategories(): List<Category> =
        client.get {
            url { path("api/categories") }
        }.body()
}
