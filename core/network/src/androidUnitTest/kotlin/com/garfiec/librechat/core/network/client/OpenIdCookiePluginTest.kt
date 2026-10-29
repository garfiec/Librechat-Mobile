package com.garfiec.librechat.core.network.client

import com.garfiec.librechat.core.logging.redact.LogRedactor
import com.garfiec.librechat.core.model.auth.AuthCookies
import com.garfiec.librechat.core.model.auth.OpenIdMarkers
import com.garfiec.librechat.core.model.auth.RequestAuth
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test

class OpenIdCookiePluginTest {
    private companion object {
        const val SERVER = "https://chat.example.com/librechat"
        val OLD = OpenIdMarkers("old-marker")
        val NEW = OpenIdMarkers("new-marker")
    }

    private class Tokens : TokenManager {
        var auth = RequestAuth("old-access", OLD)
        var refreshes = 0
        override val isAuthenticated = true
        override val sessionExpiredFlow = MutableSharedFlow<SessionEndReason>()
        override suspend fun getAccessToken(): String? = auth.bearer
        override suspend fun getAccessTokenFor(accountId: String): String? = auth.bearer
        override suspend fun getRequestAuthFor(accountId: String?): RequestAuth = auth
        override suspend fun getStagedAccessToken(): String? = null
        override suspend fun setTokens(accessToken: String, refreshToken: String) = Unit
        override suspend fun clearTokens() = Unit
        override suspend fun clearStagedTokens() = Unit
        override suspend fun selectAccount(accountId: String) = Unit
        override suspend fun removeAccount(accountId: String) = Unit
        override suspend fun onAccountResolved(accountId: String) = Unit
        override fun emitSessionExpired(expiredAccountId: String?, reason: SessionEndReason) = Unit
        override suspend fun refreshAccessToken(usedAccessToken: String?): RefreshResult = RefreshResult.Transient
        override suspend fun refreshAccessTokenFor(accountId: String, baseUrl: String, usedAccessToken: String?): RefreshResult {
            refreshes++
            auth = RequestAuth("new-access", NEW)
            return RefreshResult.Refreshed
        }
    }

    private fun client(
        engine: MockEngine,
        tokens: Tokens = Tokens(),
        credentials: RefreshCookieCredentials = RefreshCookieCredentials { "current-refresh" },
    ): HttpClient {
        val factory = object : HttpClientEngineFactory<MockEngineConfig> {
            override fun create(block: MockEngineConfig.() -> Unit): HttpClientEngine = engine
        }
        return LibreChatHttpClient.create(
            factory, Json { ignoreUnknownKeys = true }, tokens,
            object : ServerUrlProvider { override fun getBaseUrl(): String = SERVER },
            LogRedactor(), credentials,
        )
    }

    private fun identity(markers: OpenIdMarkers? = OLD, pending: Boolean = false) = RequestIdentity(
        baseUrl = SERVER, accountId = if (pending) null else "A", bearer = "old-access", isPending = pending,
        customHeaders = mapOf("Cookie" to "gateway=keep; token_provider=stale; openid_user_id=stale"),
        openId = markers,
    )

    private fun HttpRequestData.cookies(): List<String> = headers.getAll(HttpHeaders.Cookie).orEmpty()

    @Test
    fun `bearer requests merge markers and gateway cookies into one header`() = runTest {
        val engine = MockEngine { request ->
            assertThat(request.cookies()).containsExactly("gateway=keep; token_provider=openid; openid_user_id=old-marker")
            assertThat(request.headers[HttpHeaders.Authorization]).isEqualTo("Bearer old-access")
            respond("{}")
        }
        client(engine).use { it.get("$SERVER/api/user") { attributes.put(RequestIdentityKey, identity()) } }
    }

    @Test
    fun `normal and pending probes never inherit pasted OpenId markers`() = runTest {
        val engine = MockEngine { request ->
            assertThat(request.cookies()).containsExactly("gateway=keep")
            respond("{}")
        }
        client(engine).use { http ->
            http.get("$SERVER/api/user") { attributes.put(RequestIdentityKey, identity(null)) }
            http.get("$SERVER/api/config") { attributes.put(RequestIdentityKey, identity(null, pending = true)) }
        }
    }

    @Test
    fun `pending authenticated profile uses its own markers`() = runTest {
        val engine = MockEngine { request ->
            assertThat(request.cookies().single()).contains("openid_user_id=old-marker")
            respond("{}")
        }
        client(engine).use { it.get("$SERVER/api/user") { attributes.put(RequestIdentityKey, identity(pending = true)) } }
    }

    @Test
    fun `401 recovery replaces both bearer and marker`() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        val tokens = Tokens()
        val engine = MockEngine { request ->
            seen += request
            respond("{}", if (seen.size == 1) HttpStatusCode.Unauthorized else HttpStatusCode.OK)
        }
        client(engine, tokens).use { it.get("$SERVER/api/user") { attributes.put(RequestIdentityKey, identity()) } }
        assertThat(tokens.refreshes).isEqualTo(1)
        assertThat(seen.last().headers[HttpHeaders.Authorization]).isEqualTo("Bearer new-access")
        assertThat(seen.last().cookies()).containsExactly("gateway=keep; token_provider=openid; openid_user_id=new-marker")
    }

    @Test
    fun `external authority sibling deployment and downgrade receive no markers`() = runTest {
        val engine = MockEngine { request ->
            assertThat(request.cookies().joinToString()).doesNotContain("openid")
            respond("{}")
        }
        client(engine).use { http ->
            listOf("https://cdn.example.com/file", "https://chat.example.com/other/api/user", "http://chat.example.com/librechat/api/user")
                .forEach { url -> http.get(url) { attributes.put(RequestIdentityKey, identity()) } }
        }
    }

    @Test
    fun `redirect removes cookies outside the server and restores them on return`() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            seen += request
            when (seen.size) {
                1 -> respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://cdn.example.com/file"))
                2 -> respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "$SERVER/api/file"))
                else -> respond("{}")
            }
        }
        client(engine).use { it.get("$SERVER/api/file") { attributes.put(RequestIdentityKey, identity()) } }
        assertThat(seen[1].cookies()).isEmpty()
        assertThat(seen[2].cookies()).containsExactly("gateway=keep; token_provider=openid; openid_user_id=old-marker")
    }

    @Test
    fun `explicit auth cookies are stripped on an external redirect`() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            seen += request
            if (seen.size == 1) {
                respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://cdn.example.com/file"))
            } else {
                respond("{}")
            }
        }
        client(engine).use { http ->
            http.get("$SERVER/api/file") {
                attributes.put(RequestIdentityKey, identity())
                attachAuthCookies(AuthCookies("explicit-refresh", OLD))
            }
        }
        assertThat(seen.first().cookies().single()).contains("refreshToken=explicit-refresh")
        assertThat(seen.last().cookies()).isEmpty()
    }

    @Test
    fun `an image redirected onto API paths keeps the markers and loses the refresh token`() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            seen += request
            when (seen.size) {
                1 -> respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "$SERVER/api/files/a"))
                2 -> respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "$SERVER/api/files/b"))
                else -> respond("{}")
            }
        }
        val credentials = object : RefreshCookieCredentials {
            override suspend fun refreshTokenFor(accountId: String?): String = "current-refresh"
            override suspend fun authCookiesFor(accountId: String?) = AuthCookies("current-refresh", OLD)
        }
        client(engine, credentials = credentials).use {
            it.get("$SERVER/images/u/x.png") { attributes.put(RequestIdentityKey, identity()) }
        }
        assertThat(seen).hasSize(3)
        assertThat(seen[0].cookies().single()).contains("refreshToken=current-refresh")
        // No later hop may pick the image's refresh token back up from its explicit bundle.
        seen.drop(1).forEach { hop ->
            assertThat(hop.cookies().single()).doesNotContain("refreshToken")
            assertThat(hop.cookies().single()).contains("token_provider=openid; openid_user_id=old-marker")
        }
    }
}
