package com.garfiec.librechat.core.network.client

import com.garfiec.librechat.core.common.result.AccessGatewayException
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertFailsWith

/**
 * Detection of an access gateway answering instead of the server (issue #287).
 *
 * The case that matters is the one no status check can see: the gateway 302s, Ktor follows it, and
 * the sign-in page comes back as a perfectly valid **200 `text/html`**. Nothing fails until a typed
 * call tries to decode it — and Ktor's decode error quotes the request URL, so a Cloudflare Access
 * `meta=` JWT would render in an error banner.
 */
class GatewayDetectionPluginTest {

    private companion object {
        const val SERVER = "https://chat.example.com"
        const val ACCESS_LOGIN =
            "https://team.cloudflareaccess.com/cdn-cgi/access/login/chat.example.com?meta=eyJhbGci"
        const val CF_CHALLENGE =
            "Cloudflare-Access resource_metadata=\"https://chat.example.com/.well-known\""
        const val AUTHELIA_PORTAL =
            "https://auth.example.com/?rd=https%3A%2F%2Fchat.example.com%2Fapi%2Fconfig&rm=GET"
    }

    private fun clientWith(engine: MockEngine): HttpClient = HttpClient(engine) {
        install(GatewayDetectionPlugin)
    }

    /**
     * Ktor follows the 302 to a 200 sign-in page, so without this plugin the call "succeeds" and
     * blows up later with a URL-quoting decode error.
     */
    @Test
    fun `a Cloudflare Access rejection surfaces as a typed gateway error`() = runTest {
        val engine = MockEngine { request ->
            if (request.url.host == "chat.example.com") {
                respond(
                    content = "",
                    status = HttpStatusCode.Found,
                    headers = headersOf(
                        HttpHeaders.Location to listOf(ACCESS_LOGIN),
                        HttpHeaders.WWWAuthenticate to listOf(CF_CHALLENGE),
                        HttpHeaders.ContentType to listOf("text/html; charset=UTF-8"),
                    ),
                )
            } else {
                respond("<html>sign in</html>", HttpStatusCode.OK)
            }
        }

        val failure = assertFailsWith<AccessGatewayException> {
            clientWith(engine).get("$SERVER/api/config")
        }

        // The gateway's URL and JWT must not ride along on the exception the UI classifies.
        assertThat(failure.message.orEmpty()).doesNotContain("cloudflareaccess")
        assertThat(failure.message.orEmpty()).doesNotContain("meta=")
    }

    /** An ordinary successful call must be untouched — this interceptor runs on every response. */
    @Test
    fun `a normal response passes through`() = runTest {
        val engine = MockEngine { respond("""{"ok":true}""", HttpStatusCode.OK) }

        assertThat(clientWith(engine).get("$SERVER/api/config").bodyAsText())
            .isEqualTo("""{"ok":true}""")
    }

    /**
     * The server legitimately redirects downloads off-authority to object storage. Keying on the
     * redirect alone would break every file download, which is why detection keys on the
     * `WWW-Authenticate` scheme instead.
     */
    @Test
    fun `an off-authority download redirect is not mistaken for a gateway`() = runTest {
        val engine = MockEngine { request ->
            if (request.url.host == "chat.example.com") {
                respond(
                    content = "",
                    status = HttpStatusCode.Found,
                    headers = headersOf(
                        HttpHeaders.Location to listOf("https://objectstore.example.net/file.png"),
                    ),
                )
            } else {
                respond("binary-image-bytes", HttpStatusCode.OK)
            }
        }

        assertThat(clientWith(engine).get("$SERVER/api/files/download/1").bodyAsText())
            .isEqualTo("binary-image-bytes")
    }

    /** A 401 carrying a normal auth challenge is the app's own concern, not a gateway. */
    @Test
    fun `an ordinary WWW-Authenticate challenge is not a gateway`() = runTest {
        val engine = MockEngine {
            respond(
                content = "unauthorized",
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.WWWAuthenticate to listOf("Bearer realm=\"api\"")),
            )
        }

        assertThat(clientWith(engine).get("$SERVER/api/config").status)
            .isEqualTo(HttpStatusCode.Unauthorized)
    }

    /**
     * Both plugins installed in the real client's order, because the ordering is the bug: Ktor runs
     * the first-installed `HttpSend` interceptor outermost, so `HttpRequestRetry` sees the thrown
     * exception and re-sends. A test that installs only the detection plugin passes either way.
     */
    @Test
    fun `a gateway rejection is not retried by the retry plugin`() = runTest {
        var requests = 0
        val engine = MockEngine {
            requests++
            respond(
                content = "",
                status = HttpStatusCode.Found,
                headers = headersOf(
                    HttpHeaders.WWWAuthenticate to listOf(CF_CHALLENGE),
                    HttpHeaders.Location to listOf(ACCESS_LOGIN),
                ),
            )
        }
        val client = HttpClient(engine) {
            install(HttpRequestRetry) { configureRetryPolicy() }
            install(GatewayDetectionPlugin)
        }

        assertFailsWith<AccessGatewayException> { client.get("$SERVER/api/config") }

        assertThat(requests).isEqualTo(1)
    }

    /**
     * Ktor's `headers[name]` hands back only the first line. `AccessGatewaySignalTest` cannot reach
     * this: the predicate is given one already-chosen value, and choosing the wrong one is the bug.
     */
    @Test
    fun `the challenge is found when it arrives on a second header line`() = runTest {
        val engine = MockEngine {
            respond(
                content = "",
                status = HttpStatusCode.Found,
                headers = headersOf(
                    HttpHeaders.WWWAuthenticate to listOf("Bearer realm=\"api\"", CF_CHALLENGE),
                    HttpHeaders.Location to listOf(ACCESS_LOGIN),
                ),
            )
        }

        assertFailsWith<AccessGatewayException> { clientWith(engine).get("$SERVER/api/config") }
    }

    /**
     * Authelia's forward-auth answers a JSON request with a 401 and its portal in `Location`, not a
     * redirect. Read as LibreChat's 401 it drives a session refresh that the gate refuses too, and
     * the account is signed out over a block it can't clear.
     */
    @Test
    fun `a forward-auth 401 pointing at a portal is a gateway, with the portal kept`() = runTest {
        val engine = MockEngine {
            respond(
                content = "<a href=\"$AUTHELIA_PORTAL\">401 Unauthorized</a>",
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(
                    HttpHeaders.Location to listOf(AUTHELIA_PORTAL),
                    HttpHeaders.ContentType to listOf("text/html; charset=utf-8"),
                ),
            )
        }

        val failure = assertFailsWith<AccessGatewayException> { clientWith(engine).get("$SERVER/api/config") }

        assertThat(failure.portalUrl).isEqualTo(AUTHELIA_PORTAL)
    }

    /** A POST is answered with a 303 to the same portal; Ktor doesn't follow it, so the hop is all there is. */
    @Test
    fun `a forward-auth 303 to a portal with a return address is a gateway`() = runTest {
        val engine = MockEngine {
            respond(
                content = "",
                status = HttpStatusCode.SeeOther,
                headers = headersOf(HttpHeaders.Location to listOf(AUTHELIA_PORTAL)),
            )
        }

        val failure = assertFailsWith<AccessGatewayException> {
            clientWith(engine).post("$SERVER/api/auth/refresh")
        }

        assertThat(failure.portalUrl).isEqualTo(AUTHELIA_PORTAL)
    }

    /** Authelia's answer to an expired or revoked bearer token. */
    @Test
    fun `a 407 is a gateway`() = runTest {
        val engine = MockEngine {
            respond(
                content = "407 Proxy Authentication Required",
                status = HttpStatusCode.ProxyAuthenticationRequired,
                headers = headersOf("Proxy-Authenticate" to listOf("Bearer error=\"invalid_token\"")),
            )
        }

        assertFailsWith<AccessGatewayException> { clientWith(engine).get("$SERVER/api/config") }
    }

    /** LibreChat's own refresh failure redirects to a server-rooted `/login`: that is the server, not a gate. */
    @Test
    fun `a same-server redirect is not a gateway`() = runTest {
        val engine = MockEngine {
            respond(
                content = "",
                status = HttpStatusCode.Found,
                headers = headersOf(HttpHeaders.Location to listOf("/login?rd=x")),
            )
        }

        assertThat(clientWith(engine).post("$SERVER/api/auth/refresh").status).isEqualTo(HttpStatusCode.Found)
    }
}
