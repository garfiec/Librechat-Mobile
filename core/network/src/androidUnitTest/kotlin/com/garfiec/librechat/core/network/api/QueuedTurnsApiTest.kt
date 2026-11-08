package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.model.queuedturn.EnqueueQueuedTurnRequest
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.accept
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class QueuedTurnsApiTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false; explicitNulls = false }
    private val sent = mutableListOf<HttpRequestData>()

    private val api = QueuedTurnsApi(
        HttpClient(
            MockEngine { request ->
                sent += request
                respond(
                    content = """{"receipt":null,"capability":{"supported":true,"durability":"durable"}}""",
                    status = HttpStatusCode.Accepted,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        ) {
            install(ContentNegotiation) { json(json) }
            defaultRequest {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
            }
        },
    )

    private val request = EnqueueQueuedTurnRequest(
        conversationId = "convo-1",
        parentMessageId = "msg-1",
        clientRequestId = "req-1",
        text = "follow up",
    )

    private fun HttpRequestData.jsonBody(): JsonObject =
        json.parseToJsonElement(String((body as OutgoingContent.ByteArrayContent).bytes())).jsonObject

    @Test
    fun `a turn with an approval mode, ask included, goes to v2 carrying it`() = runTest {
        api.enqueueQueuedTurn(request.copy(codeApprovalMode = "ask"))

        val call = sent.single()
        assertThat(call.url.encodedPath).isEqualTo("/api/agents/chat/queued-turns/v2")
        assertThat(call.jsonBody()["codeApprovalMode"]?.jsonPrimitive?.content).isEqualTo("ask")
    }

    @Test
    fun `a turn without a mode stays on v1 and sends no mode`() = runTest {
        api.enqueueQueuedTurn(request)

        val call = sent.single()
        assertThat(call.url.encodedPath).isEqualTo("/api/agents/chat/queued-turns")
        assertThat(call.jsonBody().containsKey("codeApprovalMode")).isFalse()
    }
}
