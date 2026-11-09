package com.garfiec.librechat.core.network.client

import com.garfiec.librechat.core.common.result.AccessGatewayException
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertFailsWith

class ServerProbePluginTest {

    private val seen = mutableListOf<HttpRequestData>()

    /** The main client's order: retry outermost, the probe outside gateway detection. */
    private fun clientRedirectingTo(location: String, extraHeaders: List<Pair<String, String>> = emptyList()) =
        HttpClient(
            MockEngine { request ->
                seen += request
                if (request.url.host == "example.com") {
                    respond(
                        content = "",
                        status = HttpStatusCode.PermanentRedirect,
                        headers = headersOf(
                            *(listOf(HttpHeaders.Location to location) + extraHeaders)
                                .map { (k, v) -> k to listOf(v) }.toTypedArray(),
                        ),
                    )
                } else {
                    respond("{}", HttpStatusCode.OK)
                }
            },
        ) {
            install(HttpRequestRetry) { configureRetryPolicy() }
            install(ServerProbePlugin)
            install(GatewayDetectionPlugin)
        }

    private fun HttpRequestBuilder.probe() {
        attributes.put(ServerProbeKey, Unit)
        header("CF-Access-Client-Id", "id")
    }

    @Test
    fun `a probe that is redirected stops at the redirect and names the target`() = runTest {
        val client = clientRedirectingTo("https://www.example.com/api/config")

        val moved = assertFailsWith<ServerRedirectException> {
            client.get("https://example.com/api/config") { probe() }
        }

        assertThat(moved.location).isEqualTo(Url("https://www.example.com/api/config"))
        // Nothing went to the new address, so the gateway header never left the typed server.
        assertThat(seen.map { it.url.host }).containsExactly("example.com")
    }

    @Test
    fun `a relative location is resolved against the probed address`() = runTest {
        val client = clientRedirectingTo("/lc/api/config")

        val moved = assertFailsWith<ServerRedirectException> {
            client.get("http://example.com/api/config") { probe() }
        }

        assertThat(moved.location).isEqualTo(Url("http://example.com/lc/api/config"))
    }

    @Test
    fun `a request that is not a probe still follows the redirect`() = runTest {
        val client = clientRedirectingTo("https://www.example.com/api/config")

        assertThat(client.get("https://example.com/api/config").status).isEqualTo(HttpStatusCode.OK)
        assertThat(seen).hasSize(2)
    }

    @Test
    fun `a gateway challenge on the redirect is still a gateway error`() = runTest {
        val client = clientRedirectingTo(
            "https://team.cloudflareaccess.com/cdn-cgi/access/login?meta=eyJ",
            listOf(HttpHeaders.WWWAuthenticate to "Cloudflare-Access resource_metadata=\"x\""),
        )

        assertFailsWith<AccessGatewayException> { client.get("https://example.com/api/config") { probe() } }
    }

    @Test
    fun `a redirect from https down to http is not offered as the server's address`() = runTest {
        val client = clientRedirectingTo("http://www.example.com/api/config")

        assertThat(client.get("https://example.com/api/config") { probe() }.status)
            .isEqualTo(HttpStatusCode.PermanentRedirect)
    }

    @Test
    fun `a trailing-slash rewrite of the probed URL is followed`() = runTest {
        val client = HttpClient(
            MockEngine { request ->
                seen += request
                if (request.url.encodedPath == "/api/config") {
                    respond("", HttpStatusCode.PermanentRedirect, headersOf(HttpHeaders.Location, "/api/config/"))
                } else {
                    respond("{}", HttpStatusCode.OK)
                }
            },
        ) {
            install(HttpRequestRetry) { configureRetryPolicy() }
            install(ServerProbePlugin)
            install(GatewayDetectionPlugin)
        }

        assertThat(client.get("https://example.com/api/config") { probe() }.status).isEqualTo(HttpStatusCode.OK)
        assertThat(seen.map { it.url.encodedPath }).containsExactly("/api/config", "/api/config/").inOrder()
    }
}
