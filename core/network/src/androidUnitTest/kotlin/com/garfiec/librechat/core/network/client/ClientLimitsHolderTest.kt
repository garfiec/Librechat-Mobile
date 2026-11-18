package com.garfiec.librechat.core.network.client

import com.garfiec.librechat.core.model.config.ClientLimits
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.request.post
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ClientLimitsHolderTest {

    /** The request and socket timeouts the refresh request was sent with. */
    private suspend fun sentTimeouts(limits: ClientLimitsHolder): Pair<Long?, Long?>? {
        var seen: Pair<Long?, Long?>? = null
        val client = HttpClient(
            MockEngine { request ->
                seen = request.getCapabilityOrNull(HttpTimeoutCapability)
                    ?.let { it.requestTimeoutMillis to it.socketTimeoutMillis }
                respondOk()
            },
        ) {
            install(HttpTimeout) { connectTimeoutMillis = 10_000 }
            install(refreshDeadline(limits))
        }
        client.post("https://example.com/api/auth/refresh")
        return seen
    }

    @Test
    fun the_refresh_deadline_bounds_the_whole_request_and_the_socket_wait() = runTest {
        val fallback = ClientLimits().refreshTimeoutMs
        assertThat(sentTimeouts(ClientLimitsHolder())).isEqualTo(fallback to fallback)

        val served = ClientLimitsHolder().apply { publish(ClientLimits(refreshMs = 45_000)) }
        assertThat(sentTimeouts(served)).isEqualTo(45_000L to 45_000L)
    }
}
