package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.model.speech.TtsVoice
import com.garfiec.librechat.core.network.client.configureRetryPolicy
import com.garfiec.librechat.core.network.di.librechatJson
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertFails

/**
 * Pins the speech routes. For `GET /api/files/speech/tts/voices` every server provider answers with
 * plain names; the object shape is accepted defensively.
 */
class SpeechApiVoicesTest {

    private suspend fun getVoices(body: String): List<TtsVoice> {
        val engine = MockEngine {
            respond(
                content = body,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) { install(ContentNegotiation) { json(librechatJson) } }
        return SpeechApi(client, librechatJson).getVoices()
    }

    @Test
    fun `plain voice names decode with the name as id`() = runTest {
        // A live answer (OpenAI provider).
        val voices = getVoices("""["alloy","echo","fable","onyx","nova","shimmer"]""")

        assertThat(voices.map { it.id }).containsExactly("alloy", "echo", "fable", "onyx", "nova", "shimmer").inOrder()
        assertThat(voices.first().name).isEqualTo("alloy")
    }

    @Test
    fun `voice objects still decode`() = runTest {
        val voices = getVoices("""[{"id":"v1","name":"Rachel","provider":"elevenlabs"}]""")

        assertThat(voices).containsExactly(TtsVoice(id = "v1", name = "Rachel", provider = "elevenlabs"))
    }

    @Test
    fun `an empty list decodes`() = runTest {
        assertThat(getVoices("[]")).isEmpty()
    }

    @Test
    fun `a 500 from a server without TTS is not retried`() = runTest {
        // What a server with no `speech.tts` answers (getVoices.js), every time.
        var attempts = 0
        val engine = MockEngine {
            attempts++
            respond(
                content = """{"error":"Failed to get voices: Configuration or TTS schema is missing"}""",
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(librechatJson) }
            install(HttpRequestRetry) { configureRetryPolicy() }
            expectSuccess = true
        }

        assertFails { SpeechApi(client, librechatJson).getVoices() }
        assertThat(attempts).isEqualTo(1)
    }

    private suspend fun sttRequest(language: String?): Pair<String, String> {
        var body = ""
        var path = ""
        val engine = MockEngine { request ->
            body = String(request.body.toByteArray())
            path = request.url.encodedPath
            respond(
                content = """{"text":"hi"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = HttpClient(engine) { install(ContentNegotiation) { json(librechatJson) } }
        SpeechApi(client, librechatJson).speechToText(byteArrayOf(1, 2, 3), "audio/ogg", language)
        return path to body
    }

    @Test
    fun `speechToText sends the language as a form field`() = runTest {
        val (path, body) = sttRequest(language = "de-DE")

        assertThat(path).isEqualTo("/api/files/speech/stt")
        assertThat(body).containsMatch("name=\"?audio\"?")
        assertThat(body).containsMatch("(?s)name=\"?language\"?.*?\r\n\r\nde-DE\r\n")
    }

    @Test
    fun `speechToText omits the language field for auto-detect`() = runTest {
        val (_, body) = sttRequest(language = null)

        assertThat(body).containsMatch("name=\"?audio\"?")
        assertThat(body).doesNotContainMatch("name=\"?language\"?")
    }
}
