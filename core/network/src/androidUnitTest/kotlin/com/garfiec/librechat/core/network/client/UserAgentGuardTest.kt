package com.garfiec.librechat.core.network.client

import com.garfiec.librechat.core.common.network.RequestActivityTracker
import com.garfiec.librechat.core.network.di.NetworkGraphTestFakes
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The two halves of the browser-header invariant, together, because they pull in opposite
 * directions and neither is safe to read alone.
 *
 * **Present:** the stock LibreChat server soft-bans on the first non-browser `User-Agent` hitting a
 * `ua-parser-js` route. [applyBrowserDefaults] is the single site both the main and streaming
 * clients route through, so a regression that drops the header — or mangles the constant the iOS
 * SSE transport also references — is caught here.
 *
 * **Absent:** from v0.8.8-rc3 `requireSameOrigin` guards `POST /api/auth/login`,
 * `POST /api/auth/2fa/verify-temp` and admin local login. Its `isCrossSiteRequest` passes a request
 * carrying *neither* `Origin` nor `Sec-Fetch-Site` — "did not come from a browser page" — and a
 * mobile `Origin` can never match `DOMAIN_CLIENT`. So the half-spoof is exactly what makes login
 * work: completing the browser impersonation breaks it with `403 auth_cross_origin`, and only
 * against rc3+ servers, which reads as a server bug rather than a client regression.
 */
class UserAgentGuardTest {

    private class FakeServerUrlProvider(private val baseUrl: String) : ServerUrlProvider {
        override fun getBaseUrl(): String = baseUrl
    }

    /** Handlers run unconfined so `runTest` cannot fast-forward past the request timeout. */
    private fun engineFactory(handler: MockRequestHandler) =
        object : HttpClientEngineFactory<MockEngineConfig> {
            override fun create(block: MockEngineConfig.() -> Unit): HttpClientEngine =
                MockEngine(
                    MockEngineConfig().apply(block).apply {
                        dispatcher = Dispatchers.Unconfined
                        addHandler(handler)
                    },
                )
        }

    @Test
    fun `applyBrowserDefaults sends the browser UA on every method`() = runTest {
        val captured = mutableMapOf<String, String?>()
        val engine = MockEngine { request ->
            captured[request.method.value] = request.headers[HttpHeaders.UserAgent]
            respond("OK", HttpStatusCode.OK)
        }
        val client = HttpClient(engine) {
            defaultRequest { applyBrowserDefaults(FakeServerUrlProvider("https://chat.example.com")) }
        }

        client.get("/api/agents")
        client.post("/api/agents/chat/x")

        assertThat(captured["GET"]).isEqualTo(LibreChatHttpClient.BROWSER_USER_AGENT)
        assertThat(captured["POST"]).isEqualTo(LibreChatHttpClient.BROWSER_USER_AGENT)
    }

    @Test
    fun `the browser UA constant looks like a real browser`() {
        // ua-parser-js keys off these tokens; a constant edit that drops them re-opens the ban.
        assertThat(LibreChatHttpClient.BROWSER_USER_AGENT).contains("Mozilla")
        assertThat(LibreChatHttpClient.BROWSER_USER_AGENT).contains("Chrome")
    }

    @Test
    fun `the main client sends no browser fetch metadata on the guarded auth routes`() = runTest {
        // Asserted through the real factory, not `applyBrowserDefaults` alone: a plugin is the
        // realistic vector for these headers, and `defaultRequest` is not the only thing that can
        // add them. The iOS SSE transport hand-writes its own header block
        // (`SseHttpTransport.ios.kt`) and cannot be reached from a JVM test — see DISCOVERY.md.
        val captured = mutableListOf<Pair<String?, String?>>()
        val client = NetworkGraphTestFakes.mainClient(
            engineFactory { request ->
                captured += request.headers["Origin"] to request.headers["Sec-Fetch-Site"]
                respond("{}", HttpStatusCode.OK)
            },
            RequestActivityTracker(),
        )

        client.post("https://chat.example.com/api/auth/login")
        client.post("https://chat.example.com/api/auth/2fa/verify-temp")

        assertThat(captured).hasSize(2)
        captured.forEach { (origin, secFetchSite) ->
            assertThat(origin).isNull()
            assertThat(secFetchSite).isNull()
        }
    }
}
