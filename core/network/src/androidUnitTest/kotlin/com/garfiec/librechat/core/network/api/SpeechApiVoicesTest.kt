package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.model.speech.TtsVoice
import com.garfiec.librechat.core.network.di.librechatJson
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Pins `GET /api/files/speech/tts/voices`. Every server provider answers with plain names; the
 * object shape is accepted defensively.
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
}
