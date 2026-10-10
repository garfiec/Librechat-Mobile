package com.garfiec.librechat.core.network.sse

import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.common.identity.AccountState
import com.garfiec.librechat.core.common.identity.InMemoryActiveAccountProvider
import com.garfiec.librechat.core.model.StreamErrorCodes
import com.garfiec.librechat.core.model.StreamEvent
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.url
import io.ktor.utils.io.ClosedByteChannelException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test

/**
 * An HTTP status on the stream GET, in the form it actually arrives.
 *
 * The transport reports a status by cancelling the byte channel with it, and Ktor hands it back
 * either raw or inside a `ClosedByteChannelException` — which one is a race between the pump and the
 * parse loop. Read type-exactly, the wrapped 404 of a resume whose run already finished became a
 * "connection error": the client retried on a backoff ladder instead of ending the stream, so the
 * finished reply was never refetched and a new chat showed only the user's message.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SseClientStatusTest {

    private companion object {
        const val SERVER = "https://chat.example.com"
    }

    private fun clientThrowing(error: () -> Throwable, onRequest: () -> Unit = {}): SseClient {
        val engine = MockEngine(
            MockEngineConfig().apply {
                dispatcher = Dispatchers.Unconfined
                addHandler {
                    onRequest()
                    throw error()
                }
            },
        )
        return SseClient(
            json = Json { ignoreUnknownKeys = true },
            transport = SseHttpTransport(HttpClient(engine) { defaultRequest { url(SERVER) } }),
            activeAccountProvider = InMemoryActiveAccountProvider(AccountState.Resolved(AccountId("srv-1:user-a"))),
        )
    }

    @Test
    fun `a wrapped 404 ends the stream at once, like a raw one`() = runTest(UnconfinedTestDispatcher()) {
        var requests = 0
        val events = clientThrowing({ ClosedByteChannelException(SseHttpStatusException(404)) }) { requests++ }
            .connect("api/agents/chat/stream/abc", resume = true)
            .toList()

        // No error and no retry: a 404 means the job is gone, and the caller's clean-end path is
        // what refetches the conversation.
        assertThat(events.filterIsInstance<StreamEvent.Error>()).isEmpty()
        assertThat(events.filterIsInstance<StreamEvent.Retrying>()).isEmpty()
        assertThat(requests).isEqualTo(1)
    }

    @Test
    fun `a wrapped 401 is reported as unauthorized, not retried as a network drop`() = runTest(UnconfinedTestDispatcher()) {
        var requests = 0
        val events = clientThrowing({ ClosedByteChannelException(SseHttpStatusException(401)) }) { requests++ }
            .connect("api/agents/chat/stream/abc")
            .toList()

        assertThat(events.filterIsInstance<StreamEvent.Error>().single().code).isEqualTo("401")
        assertThat(requests).isEqualTo(1)
    }

    /**
     * A status the loop has no special meaning for (a proxy's 502) is retried, and once the retries
     * run out the stream must say so — ending silently let the caller read it as a finished run and
     * reload without a word, over a reply that never came.
     */
    @Test
    fun `an unexpected status reports an error once the retries run out`() = runTest(UnconfinedTestDispatcher()) {
        var requests = 0
        val events = clientThrowing({ ClosedByteChannelException(SseHttpStatusException(502)) }) { requests++ }
            .connect("api/agents/chat/stream/abc")
            .toList()

        val error = events.filterIsInstance<StreamEvent.Error>().single()
        // Something answered: not a connectivity problem, so no connectivity observer is armed.
        assertThat(error.isNetworkError).isFalse()
        assertThat(error.message).contains("502")
        // Tagged for the consumer to ask whether the run is still live before reporting it.
        assertThat(error.code).isEqualTo(StreamErrorCodes.STATUS_RETRY_EXHAUSTED)
        assertThat(events.last()).isEqualTo(error)
        // The initial attempt plus five retries, and no "retrying 6 of 5" on the way out.
        assertThat(requests).isEqualTo(6)
        val retries = events.filterIsInstance<StreamEvent.Retrying>()
        assertThat(retries).hasSize(5)
        assertThat(retries.all { it.attempt <= it.maxAttempts }).isTrue()
    }

    /**
     * An I/O failure is a network error however it arrives. The iOS transport closes with one from
     * the pump side, which can reach the loop wrapped; read type-exactly, the wrapped form ended as a
     * plain error, and with no isNetworkError the stream never resumed when the network came back.
     */
    @Test
    fun `a wrapped stream I-O error ends as a network error`() = runTest(UnconfinedTestDispatcher()) {
        var requests = 0
        val events = clientThrowing({ ClosedByteChannelException(SseStreamException("network error (posix 57)")) }) {
            requests++
        }
            .connect("api/agents/chat/stream/abc")
            .toList()

        val error = events.filterIsInstance<StreamEvent.Error>().single()
        assertThat(error.isNetworkError).isTrue()
        assertThat(error.code).isEqualTo(StreamErrorCodes.RETRY_EXHAUSTED)
        assertThat(requests).isEqualTo(6)
    }
}
