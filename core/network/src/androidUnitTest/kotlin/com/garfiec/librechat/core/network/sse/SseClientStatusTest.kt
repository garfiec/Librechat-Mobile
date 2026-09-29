package com.garfiec.librechat.core.network.sse

import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.common.identity.AccountState
import com.garfiec.librechat.core.common.identity.InMemoryActiveAccountProvider
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
}
