package com.garfiec.librechat.core.network.client

import com.garfiec.librechat.core.common.result.AccessGatewayException
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertFailsWith

class GateCredentialPluginTest {

    private companion object {
        const val SERVER = "https://chat.example.com"
        const val PORTAL = "https://auth.example.com/?rd=https%3A%2F%2Fchat.example.com%2Fapi%2Fconfig&rm=GET"
    }

    private class FakeServerUrlProvider(private val baseUrl: String) : ServerUrlProvider {
        override fun getBaseUrl(): String = baseUrl
    }

    private class FakeGateProvider(
        var current: GateCredential? = GateCredential.Bearer("at-1"),
        var renewal: GateCredential? = GateCredential.Bearer("at-2"),
    ) : GateCredentialProvider {
        val rejected = mutableListOf<GateCredential>()
        override suspend fun credentialFor(baseUrl: String): GateCredential? =
            current.takeIf { baseUrl == SERVER }

        override suspend fun onRejected(baseUrl: String, used: GateCredential): GateCredential? {
            rejected += used
            return renewal?.also { current = it }
        }
    }

    private fun client(engine: MockEngine, provider: GateCredentialProvider): HttpClient = HttpClient(engine) {
        install(GateCredentialPlugin) {
            this.provider = provider
            serverUrlProvider = FakeServerUrlProvider(SERVER)
        }
        install(GatewayDetectionPlugin)
    }

    @Test
    fun `a bearer goes on the server's own requests as Proxy-Authorization`() = runTest {
        var seen: String? = null
        var authorization: String? = "untouched"
        val engine = MockEngine { request ->
            seen = request.headers[HttpHeaders.ProxyAuthorization]
            authorization = request.headers[HttpHeaders.Authorization]
            respond("{}", HttpStatusCode.OK)
        }

        client(engine, FakeGateProvider()).get("$SERVER/api/config")

        assertThat(seen).isEqualTo("Bearer at-1")
        assertThat(authorization).isNull()
    }

    @Test
    fun `nothing is attached to another host`() = runTest {
        var seen: String? = "unset"
        val engine = MockEngine { request ->
            seen = request.headers[HttpHeaders.ProxyAuthorization]
            respond("{}", HttpStatusCode.OK)
        }

        client(engine, FakeGateProvider()).get("https://cdn.example.net/file.png")

        assertThat(seen).isNull()
    }

    /** Ktor's redirect copies every header but `Authorization` to the next host. */
    @Test
    fun `the bearer is stripped from an off-site redirect hop`() = runTest {
        val seenByHost = mutableMapOf<String, String?>()
        val engine = MockEngine { request ->
            seenByHost[request.url.host] = request.headers[HttpHeaders.ProxyAuthorization]
            if (request.url.host == "chat.example.com") {
                respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://store.example.net/f"))
            } else {
                respond("bytes", HttpStatusCode.OK)
            }
        }

        client(engine, FakeGateProvider()).get("$SERVER/api/files/download/1")

        assertThat(seenByHost["chat.example.com"]).isEqualTo("Bearer at-1")
        assertThat(seenByHost["store.example.net"]).isNull()
    }

    @Test
    fun `a session cookie merges into the Cookie header`() = runTest {
        var cookie: String? = null
        val engine = MockEngine { request ->
            cookie = request.headers[HttpHeaders.Cookie]
            respond("{}", HttpStatusCode.OK)
        }

        client(engine, FakeGateProvider(current = GateCredential.Cookie("authelia_session=s1")))
            .get("$SERVER/api/config")

        assertThat(cookie).isEqualTo("authelia_session=s1")
    }

    /** Authelia answers a revoked or expired bearer with 407; one renewal and one retry. */
    @Test
    fun `a refused bearer is renewed once and the request retried`() = runTest {
        val sent = mutableListOf<String?>()
        val engine = MockEngine { request ->
            val header = request.headers[HttpHeaders.ProxyAuthorization]
            sent += header
            if (header == "Bearer at-1") {
                respond("", HttpStatusCode.ProxyAuthenticationRequired)
            } else {
                respond("{}", HttpStatusCode.OK)
            }
        }
        val provider = FakeGateProvider()

        val response = client(engine, provider).get("$SERVER/api/config")

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        assertThat(sent).containsExactly("Bearer at-1", "Bearer at-2").inOrder()
        assertThat(provider.rejected).containsExactly(GateCredential.Bearer("at-1"))
    }

    @Test
    fun `a refusal with no renewal surfaces as a gateway block with the portal`() = runTest {
        val engine = MockEngine {
            respond("", HttpStatusCode.Unauthorized, headersOf(HttpHeaders.Location, PORTAL))
        }
        val provider = FakeGateProvider(current = GateCredential.Cookie("authelia_session=old"), renewal = null)

        val failure = assertFailsWith<AccessGatewayException> { client(engine, provider).get("$SERVER/api/config") }

        assertThat(failure.portalUrl).isEqualTo(PORTAL)
        assertThat(provider.rejected).hasSize(1)
    }

    /** A second refusal after the renewal is not renewed again: one retry per call. */
    @Test
    fun `a renewed credential that is refused again is not retried`() = runTest {
        var requests = 0
        val engine = MockEngine {
            requests++
            respond("", HttpStatusCode.ProxyAuthenticationRequired)
        }
        val provider = FakeGateProvider()

        assertFailsWith<AccessGatewayException> { client(engine, provider).get("$SERVER/api/config") }

        assertThat(requests).isEqualTo(2)
        assertThat(provider.rejected).hasSize(1)
    }

    @Test
    fun `a gate block on a server with no credential is not renewed`() = runTest {
        val engine = MockEngine {
            respond("", HttpStatusCode.Unauthorized, headersOf(HttpHeaders.Location, PORTAL))
        }
        val provider = FakeGateProvider(current = null)

        assertFailsWith<AccessGatewayException> { client(engine, provider).get("$SERVER/api/config") }

        assertThat(provider.rejected).isEmpty()
    }
}
