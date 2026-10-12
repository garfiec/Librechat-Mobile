package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.suspendRunCatching
import com.garfiec.librechat.core.network.sse.SseLineParser
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * A generation POST the server's middleware refused before the run started.
 *
 * Upstream's `denyRequest` (`api/server/middleware/denyRequest.js`) answers the message rate
 * limiter, a ban, moderation and a foreign conversation id by writing SSE frames onto the POST
 * itself — HTTP 200, no `Content-Type` — rather than with an error status: an `event: message`
 * frame carrying the user message and `created: true`, then an `event: error` frame whose data is
 * the error turn. The violation is that turn's `text`, either the JSON of a typed payload
 * (`{"type":"message_limit",…}`) or prose (`"error in moderation check"`).
 *
 * Only the error frame is read. The `created` frame must never reach the stream pipeline: on a new
 * chat its conversation id is minted for this response and never persisted.
 */
internal object ChatRefusal {

    /**
     * The refusal in [response] as an [ApiException] whose [ApiException.body] is the error turn's
     * text, or null when the response is JSON or carries no error frame.
     */
    suspend fun from(response: HttpResponse): ApiException? {
        if (response.isJson()) return null
        val text = errorText(response.bodyAsText()) ?: return null
        // A typed payload is classified downstream from `body`; prose is the server's own sentence
        // and goes through the same screen as any other server-authored message.
        val isPayload = text.trimStart().startsWith("{")
        return ApiException(
            statusCode = response.status.value,
            message = if (isPayload) REFUSED_MESSAGE else text,
            body = text,
            serverAuthored = !isPayload,
        )
    }

    /**
     * The error turn's text in an SSE [body]: `text`, then `message`, then `error`, as upstream's
     * `parseSSEErrorData` (`client/src/hooks/SSE/useResumableSSE.ts`) reads it. Only string values
     * count — `sendError` sets `error: true` on the same object.
     */
    suspend fun errorText(body: String): String? {
        if (body.isBlank()) return null
        val data = suspendRunCatching {
            SseLineParser().parse(ByteReadChannel(body)).firstOrNull { it.event == "error" }
        }.getOrNull()?.data ?: return null
        val element = runCatching { json.parseToJsonElement(data) }.getOrNull() ?: return null
        val text = when (element) {
            is JsonObject -> sequenceOf("text", "message", "error")
                .mapNotNull { key -> (element[key] as? JsonPrimitive)?.takeIf { it.isString }?.content }
                .firstOrNull { it.isNotBlank() }
            is JsonPrimitive -> element.takeIf { it.isString }?.content
            else -> null
        }
        return text?.takeIf { it.isNotBlank() }
    }

    private val json = Json { isLenient = true }

    const val REFUSED_MESSAGE = "The server refused this message."
}

/** This response's body, or the [ChatRefusal] it carries thrown in its place. */
internal suspend inline fun <reified T> HttpResponse.bodyUnlessRefused(): T {
    ChatRefusal.from(this)?.let { throw it }
    return body()
}

/** Whether this response declares a JSON body; a missing or malformed `Content-Type` is not. */
internal fun HttpResponse.isJson(): Boolean {
    val contentType = headers[HttpHeaders.ContentType] ?: return false
    return runCatching { ContentType.parse(contentType).match(ContentType.Application.Json) }
        .getOrDefault(false)
}

/**
 * The error turn's text when this exception is a [ChatRefusal], or null.
 *
 * A refusal is the only [ApiException] raised on a 2xx that carries a body: every other one comes
 * from the response validator, which runs on non-2xx only.
 */
val ApiException.chatRefusalText: String?
    get() = body?.takeIf { statusCode in SUCCESS_RANGE }

private val SUCCESS_RANGE = 200..299
