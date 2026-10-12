package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.model.queuedturn.EnqueueQueuedTurnRequest
import com.garfiec.librechat.core.model.request.ChatResumeRequest
import com.garfiec.librechat.core.model.request.SteerCancelRequest
import com.garfiec.librechat.core.model.request.SteerRequest
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test

/**
 * The generation POSTs' refusal path (upstream `denyRequest`). Bodies are built the way the server
 * writes them — `sendEvent` for the user message, then `sendError` → `handleError` for the error
 * turn — on HTTP 200 with no `Content-Type`.
 */
class ChatRefusalTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    private fun client(body: String, contentType: String? = null): HttpClient {
        val engine = MockEngine {
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = contentType?.let { headersOf(HttpHeaders.ContentType, it) } ?: headersOf(),
            )
        }
        return HttpClient(engine) {
            install(ContentNegotiation) { json(json) }
            defaultRequest {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
            }
        }
    }

    /** [text] is the error turn's `text`, already a JSON string or prose — as the server sets it. */
    private fun denyBody(text: String): String {
        val userMessage = """{"sender":"User","messageId":"u1","parentMessageId":"p1",""" +
            """"conversationId":"c1","isCreatedByUser":true,"text":"hello"}"""
        val errorTurn = buildString {
            append("""{"sender":"Agent","messageId":"e1","conversationId":"c1","parentMessageId":"u1",""")
            append(""""unfinished":false,"error":true,"final":true,"text":""")
            append(JsonPrimitive(text).toString())
            append(""","isCreatedByUser":false}""")
        }
        return "event: message\ndata: {\"message\":$userMessage,\"created\":true}\n\n" +
            "event: error\ndata: $errorTurn\n\n"
    }

    private suspend fun startFailure(body: String, contentType: String? = null): ApiException {
        val api = ChatApi(client(body, contentType))
        return runCatching { api.startChat("agents", JsonObject(emptyMap())) }
            .exceptionOrNull() as ApiException
    }

    @Test
    fun `each typed refusal is carried in the body, never the version message`() = runTest {
        val texts = listOf(
            """{"type":"message_limit","max":40,"limiter":"ip","windowInMinutes":1,"resetAt":1,"retryAfterSeconds":42}""",
            """{"type":"ban"}""",
            """{"type":"moderation"}""",
            """{"type":"convo_access","error":"User not authorized for this conversation"}""",
        )
        texts.forEach { text ->
            val e = startFailure(denyBody(text))

            assertThat(e.body).isEqualTo(text)
            assertThat(e.chatRefusalText).isEqualTo(text)
            assertThat(e.message).isEqualTo(ChatRefusal.REFUSED_MESSAGE)
            assertThat(e.serverAuthored).isFalse()
        }
    }

    @Test
    fun `a prose refusal is the server's own sentence, screened like any server text`() = runTest {
        val e = startFailure(denyBody("error in moderation check"))

        assertThat(e.chatRefusalText).isEqualTo("error in moderation check")
        assertThat(e.message).isEqualTo("error in moderation check")
        assertThat(e.serverAuthored).isTrue()
    }

    @Test
    fun `an event-stream content type is read the same way`() = runTest {
        val e = startFailure(denyBody("""{"type":"ban"}"""), contentType = "text/event-stream")

        assertThat(e.chatRefusalText).isEqualTo("""{"type":"ban"}""")
    }

    @Test
    fun `a body with no error frame keeps the version message`() = runTest {
        listOf("", "<html><body>Hello</body></html>", "event: message\ndata: {\"created\":true}\n\n").forEach { body ->
            val e = startFailure(body)

            assertThat(e.chatRefusalText).isNull()
            assertThat(e.message).contains("backend version incompatibility")
        }
    }

    @Test
    fun `resume, steer and steer cancel surface a refusal instead of a decode failure`() = runTest {
        val text = """{"type":"message_limit","max":40}"""
        val resume = runCatching {
            ChatApi(client(denyBody(text))).resumeChat(
                ChatResumeRequest(conversationId = "c1", actionId = "a1", endpoint = "agents"),
            )
        }.exceptionOrNull()
        val steer = runCatching {
            ChatApi(client(denyBody(text))).steerChat(SteerRequest(conversationId = "c1", text = "go"))
        }.exceptionOrNull()
        val cancel = runCatching {
            ChatApi(client(denyBody(text))).cancelSteer(SteerCancelRequest(conversationId = "c1", steerId = "s1"))
        }.exceptionOrNull()

        assertThat((resume as ApiException).chatRefusalText).isEqualTo(text)
        assertThat((steer as ApiException).chatRefusalText).isEqualTo(text)
        assertThat((cancel as ApiException).chatRefusalText).isEqualTo(text)
    }

    @Test
    fun `queued-turn enqueue and cancel surface a refusal instead of a decode failure`() = runTest {
        val text = """{"type":"message_limit","max":40}"""
        val enqueue = runCatching {
            QueuedTurnsApi(client(denyBody(text))).enqueueQueuedTurn(
                EnqueueQueuedTurnRequest(
                    conversationId = "c1",
                    parentMessageId = "p1",
                    clientRequestId = "r1",
                    text = "go",
                ),
            )
        }.exceptionOrNull()
        val cancel = runCatching { QueuedTurnsApi(client(denyBody(text))).cancelQueuedTurn("q1") }.exceptionOrNull()

        assertThat((enqueue as ApiException).chatRefusalText).isEqualTo(text)
        assertThat((cancel as ApiException).chatRefusalText).isEqualTo(text)
    }

    @Test
    fun `a JSON success is decoded untouched`() = runTest {
        val api = ChatApi(client("""{"conversationId":"c1","status":"started","streamId":"c1"}""", "application/json"))

        assertThat(api.startChat("agents", JsonObject(emptyMap())).conversationId).isEqualTo("c1")
    }
}
