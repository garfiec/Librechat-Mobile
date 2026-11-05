package com.garfiec.librechat.core.network.client

import com.garfiec.librechat.core.network.api.CodeEnvironmentsApi
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException

/**
 * Locks the retry-scoping invariant: the shared [configureRetryPolicy] must replay only
 * side-effect-free methods, so a retried chat POST / upload can never mint a duplicate server-side
 * job. Exercises the real production policy on a minimal client (the seam both `create()` installs).
 */
class HttpRetryScopingTest {

    // maxRetries = 2 → 1 initial attempt + 2 retries = 3 total for a retry-safe method.
    private companion object {
        const val SAFE_ATTEMPTS = 3
        const val UNSAFE_ATTEMPTS = 1
    }

    private fun countingClient(engine: MockEngine): HttpClient = HttpClient(engine) {
        install(HttpRequestRetry) { configureRetryPolicy() }
    }

    @Test
    fun `retries GET on 5xx but not POST or DELETE`() = runTest {
        val attempts = mutableMapOf<String, Int>()
        val engine = MockEngine { request ->
            attempts.merge(request.method.value, 1, Int::plus)
            respond("boom", HttpStatusCode.InternalServerError)
        }
        val client = countingClient(engine)

        client.get("https://example.com/api/data")
        client.post("https://example.com/api/agents/chat/x")
        client.delete("https://example.com/api/files/f1")

        assertThat(attempts["GET"]).isEqualTo(SAFE_ATTEMPTS)
        assertThat(attempts["POST"]).isEqualTo(UNSAFE_ATTEMPTS)
        assertThat(attempts["DELETE"]).isEqualTo(UNSAFE_ATTEMPTS)
    }

    @Test
    fun `retries GET on exception but not POST or DELETE`() = runTest {
        val attempts = mutableMapOf<String, Int>()
        val engine = MockEngine {
            attempts.merge(it.method.value, 1, Int::plus)
            throw IOException("connection reset")
        }
        val client = countingClient(engine)

        // The call ultimately throws once retries are exhausted (or immediately for unsafe methods).
        runCatching { client.get("https://example.com/api/data") }
        runCatching { client.post("https://example.com/api/agents/chat/x") }
        runCatching { client.delete("https://example.com/api/files/f1") }

        assertThat(attempts["GET"]).isEqualTo(SAFE_ATTEMPTS)
        assertThat(attempts["POST"]).isEqualTo(UNSAFE_ATTEMPTS)
        assertThat(attempts["DELETE"]).isEqualTo(UNSAFE_ATTEMPTS)
    }

    @Test
    fun `does not retry a GET answered 501 Not Implemented`() = runTest {
        var attempts = 0
        val engine = MockEngine {
            attempts++
            respond("Not Implemented", HttpStatusCode.NotImplemented)
        }
        val client = countingClient(engine)

        client.get("https://example.com/api/files/download-url/u1/f1")

        assertThat(attempts).isEqualTo(1)
    }

    @Test
    fun `does not retry a GET whose 501 or 4xx is thrown as an ApiException`() = runTest {
        for (status in listOf(501, 400, 404, 405)) {
            var attempts = 0
            val engine = MockEngine {
                attempts++
                throw ApiException(statusCode = status, message = "HTTP $status")
            }
            val client = countingClient(engine)

            runCatching { client.get("https://example.com/api/files/download-url/u1/f1") }

            assertThat(attempts).isEqualTo(1)
        }
    }

    @Test
    fun `still retries a GET on 503 whether it arrives as a response or as an ApiException`() = runTest {
        var responseAttempts = 0
        val responding = countingClient(
            MockEngine {
                responseAttempts++
                respond("busy", HttpStatusCode.ServiceUnavailable)
            },
        )
        var thrownAttempts = 0
        val throwing = countingClient(
            MockEngine {
                thrownAttempts++
                throw ApiException(statusCode = 503, message = "busy")
            },
        )

        responding.get("https://example.com/api/files/download-url/u1/f1")
        runCatching { throwing.get("https://example.com/api/files/download-url/u1/f1") }

        assertThat(responseAttempts).isEqualTo(SAFE_ATTEMPTS)
        assertThat(thrownAttempts).isEqualTo(SAFE_ATTEMPTS)
    }

    @Test
    fun `sends a machine status read once on 503, as a response or as an ApiException`() = runTest {
        var responseAttempts = 0
        val responding = countingClient(
            MockEngine {
                responseAttempts++
                respond("busy", HttpStatusCode.ServiceUnavailable)
            },
        )
        var thrownAttempts = 0
        val throwing = countingClient(
            MockEngine {
                thrownAttempts++
                throw ApiException(statusCode = 503, message = "busy")
            },
        )

        runCatching { CodeEnvironmentsApi(responding).status("env-1") }
        runCatching { CodeEnvironmentsApi(throwing).status("env-1") }

        assertThat(responseAttempts).isEqualTo(1)
        assertThat(thrownAttempts).isEqualTo(1)
    }

    @Test
    fun `still retries a machine status read that failed in transport`() = runTest {
        var attempts = 0
        val client = countingClient(
            MockEngine {
                attempts++
                throw IOException("connection reset")
            },
        )

        runCatching { CodeEnvironmentsApi(client).status("env-1") }

        assertThat(attempts).isEqualTo(SAFE_ATTEMPTS)
    }

    @Test
    fun `does not retry a GET whose certificate is untrusted`() = runTest {
        var attempts = 0
        val engine = MockEngine {
            attempts++
            throw SSLHandshakeException("Trust anchor for certification path not found.").apply {
                initCause(CertificateException("Trust anchor for certification path not found."))
            }
        }
        val client = countingClient(engine)

        runCatching { client.get("https://example.com/api/config") }

        assertThat(attempts).isEqualTo(1)
    }

    @Test
    fun `retries a GET whose handshake failed for a reason other than the certificate`() = runTest {
        var attempts = 0
        val engine = MockEngine {
            attempts++
            throw SSLHandshakeException("SSL handshake aborted: I/O error during system call, Connection reset by peer")
        }
        val client = countingClient(engine)

        runCatching { client.get("https://example.com/api/config") }

        assertThat(attempts).isEqualTo(SAFE_ATTEMPTS)
    }
}
