package com.garfiec.librechat.core.network.sse

import com.garfiec.librechat.core.model.StreamErrorCodes
import com.garfiec.librechat.core.model.StreamEvent
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.url
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test

/**
 * A body that ends without the run's end is a drop, not a finished run.
 *
 * Upstream sends no heartbeat, so a proxy or CDN idle timeout during a long tool call or a
 * human-review pause closes a live stream cleanly — and the server's own `res.destroy()` on a
 * publication failure asks the client to reconnect. Read as terminal, the caller tore the live turn
 * down and reloaded before anything was saved. Only the resume can tell: a sync frame if the run is
 * live, a final frame or a 404 if it is not.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SseClientCleanEndTest {

    private companion object {
        const val SERVER = "https://chat.example.com"
        const val PATH = "api/agents/chat/stream/abc"
        const val EVENT_CAP = 50
        const val CREATED = "data: {\"created\":true}\n\n"
        const val DELTA =
            "data: {\"event\":\"on_message_delta\",\"data\":{\"id\":\"s1\"," +
                "\"delta\":{\"content\":[{\"type\":\"text\",\"text\":\"Alpine \"}]}}}\n\n"
        const val FINAL = "data: {\"final\":true,\"conversation\":{\"conversationId\":\"abc\"}}\n\n"
    }

    /** Answers the n-th request (0-based) with `bodies[n]`, repeating the last one. */
    private fun client(requests: MutableList<HttpRequestData>, vararg bodies: String): SseClient {
        val engine = MockEngine(
            MockEngineConfig().apply {
                // Unconfined for the same reason as SseClientOriginBindingTest: a handler on a real
                // thread lets runTest fast-forward virtual time into the parser's stall watchdog.
                dispatcher = Dispatchers.Unconfined
                addHandler { request ->
                    requests += request
                    respond(bodies[minOf(requests.size - 1, bodies.lastIndex)], HttpStatusCode.OK)
                }
            },
        )
        return SseClient(
            json = Json { ignoreUnknownKeys = true },
            transport = SseHttpTransport(HttpClient(engine) { defaultRequest { url(SERVER) } }),
        )
    }

    @Test
    fun `a stream that closes without the final frame resumes instead of ending`() = runTest(UnconfinedTestDispatcher()) {
        val requests = mutableListOf<HttpRequestData>()
        val events = client(requests, CREATED + DELTA, FINAL).connect(PATH).toList()

        assertThat(requests).hasSize(2)
        assertThat(requests[0].url.parameters["resume"]).isNull()
        assertThat(requests[1].url.parameters["resume"]).isEqualTo("true")
        assertThat(events.filterIsInstance<StreamEvent.Error>()).isEmpty()
        assertThat(events.filterIsInstance<StreamEvent.Retrying>()).hasSize(1)
        assertThat(events.filterIsInstance<StreamEvent.ContentDelta>().single().chunk).isEqualTo("Alpine ")
        assertThat(events.last()).isInstanceOf(StreamEvent.Final::class.java)
    }

    /**
     * A 200 whose body carries no event at all (a captive portal's page) is no progress: it climbs
     * the ladder rather than reconnecting forever, and reports the ceiling for the consumer to
     * adjudicate — not as a network error, since every connection succeeded.
     */
    @Test
    fun `a body with no events climbs the ladder to the retry ceiling`() = runTest(UnconfinedTestDispatcher()) {
        val requests = mutableListOf<HttpRequestData>()
        // Bounded: a client that counted bytes as progress would reconnect forever, and should fail
        // this test rather than hang it.
        val events = client(requests, "<html>sign in to the Wi-Fi</html>").connect(PATH).take(EVENT_CAP).toList()

        assertThat(requests).hasSize(6)
        val error = events.filterIsInstance<StreamEvent.Error>().single()
        assertThat(error.code).isEqualTo(StreamErrorCodes.RETRY_EXHAUSTED)
        assertThat(error.isNetworkError).isFalse()
        assertThat(events.filterIsInstance<StreamEvent.Retrying>().all { it.attempt <= it.maxAttempts }).isTrue()
    }

    /** An in-band error is the run's end: the server closes after it, and there is nothing to resume. */
    @Test
    fun `a stream that reported an error is not resumed`() = runTest(UnconfinedTestDispatcher()) {
        val requests = mutableListOf<HttpRequestData>()
        val events = client(requests, CREATED + "data: {\"error\":\"boom\"}\n\n", FINAL).connect(PATH).toList()

        assertThat(requests).hasSize(1)
        assertThat(events.filterIsInstance<StreamEvent.Error>()).hasSize(1)
        assertThat(events.filterIsInstance<StreamEvent.Retrying>()).isEmpty()
    }
}
