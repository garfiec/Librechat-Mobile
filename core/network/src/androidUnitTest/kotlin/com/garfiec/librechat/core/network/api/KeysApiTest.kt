package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.model.request.UpdateKeyRequest
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test

class KeysApiTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    private fun jsonClient(engine: MockEngine): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) { json(json) }
        defaultRequest {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
        }
    }

    @Test
    fun `getKeyExpiry passes name as query parameter`() = runTest {
        var capturedUrl: String? = null
        val engine = MockEngine { request ->
            capturedUrl = request.url.toString()
            respond(
                content = """{"expiresAt":"2026-05-01T00:00:00Z"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val response = KeysApi(jsonClient(engine)).getKeyExpiry("openAI")

        assertThat(capturedUrl).contains("api/keys")
        assertThat(capturedUrl).contains("name=openAI")
        assertThat(response.expiresAt).isEqualTo("2026-05-01T00:00:00Z")
    }

    @Test
    fun `getKeyExpiry decodes null expiresAt`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"expiresAt":null}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val response = KeysApi(jsonClient(engine)).getKeyExpiry("openAI")
        assertThat(response.expiresAt).isNull()
    }

    @Test
    fun `getKeyExpiry decodes never literal`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"expiresAt":"never"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val response = KeysApi(jsonClient(engine)).getKeyExpiry("openAI")
        assertThat(response.expiresAt).isEqualTo("never")
    }

    @Test
    fun `updateKey returns Unit and tolerates empty 201 body`() = runTest {
        var receivedBody: String? = null
        val engine = MockEngine { request ->
            receivedBody = (request.body as? OutgoingContent.ByteArrayContent)
                ?.bytes()?.decodeToString()
            respond(
                content = "",
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        KeysApi(jsonClient(engine)).updateKey(
            UpdateKeyRequest(
                name = "openAI",
                value = """{"apiKey":"sk-x","baseURL":""}""",
                expiresAt = "2026-05-01T00:00:00Z",
            ),
        )
        assertThat(receivedBody).contains("\"name\":\"openAI\"")
        assertThat(receivedBody).contains("\"value\":\"{\\\"apiKey\\\":\\\"sk-x\\\",\\\"baseURL\\\":\\\"\\\"}\"")
    }

    @Test
    fun `getKeyExpiry uses GET`() = runTest {
        var method: HttpMethod? = null
        val engine = MockEngine { request ->
            method = request.method
            respond(
                content = """{"expiresAt":null}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        KeysApi(jsonClient(engine)).getKeyExpiry("openAI")
        assertThat(method).isEqualTo(HttpMethod.Get)
    }
}
