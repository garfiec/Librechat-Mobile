package com.garfiec.librechat.core.network.client

import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Characterization of the Ktor behaviour that [ServerHeadersPlugin]'s redirect guard exists to
 * contain. Nothing here is our code — it pins a third-party contract.
 *
 * Without it, the redirect test in `ServerHeadersPluginTest` is unfalsifiable: if a future Ktor
 * stopped forwarding custom headers across a cross-authority redirect, that test would keep passing
 * while proving nothing, and the guard could be deleted on a green suite. This one fails instead,
 * which is the signal to re-derive whether the guard is still needed.
 */
class KtorRedirectContractTest {

    @Test
    fun `ktor forwards custom headers across a cross-authority redirect and strips only Authorization`() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            seen += request
            if (request.url.host == "chat.example.com") {
                respond(
                    content = "",
                    status = HttpStatusCode.Found,
                    headers = headersOf(HttpHeaders.Location, "https://evil.example.net/steal"),
                )
            } else {
                respond("{}", HttpStatusCode.OK)
            }
        }

        // A bare client: none of our plugins, so this measures Ktor's stock redirect handling.
        HttpClient(engine).get("https://chat.example.com/api/files/download/1") {
            header("CF-Access-Client-Id", "id-value")
            header(HttpHeaders.Authorization, "Bearer session-token")
        }

        assertThat(seen).hasSize(2)
        val redirected = seen[1]
        assertThat(redirected.url.host).isEqualTo("evil.example.net")
        // Ktor's HttpRedirect strips exactly one header...
        assertThat(redirected.headers[HttpHeaders.Authorization]).isNull()
        // ...and forwards everything else, including a credential it has no way to recognise as one.
        assertThat(redirected.headers["CF-Access-Client-Id"]).isEqualTo("id-value")
    }

    /**
     * Why a server that redirects breaks sign-in: the probe's GET follows, every POST gets the raw 3xx
     * back. [ServerProbePlugin] and the validator's redirect message exist because of this.
     */
    @Test
    fun `ktor does not follow a redirect for a POST`() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            seen += request
            respond("", HttpStatusCode.PermanentRedirect, headersOf(HttpHeaders.Location, "https://www.example.com/api/auth/login"))
        }

        val response = HttpClient(engine).post("https://example.com/api/auth/login")

        assertThat(response.status).isEqualTo(HttpStatusCode.PermanentRedirect)
        assertThat(seen).hasSize(1)
    }
}
