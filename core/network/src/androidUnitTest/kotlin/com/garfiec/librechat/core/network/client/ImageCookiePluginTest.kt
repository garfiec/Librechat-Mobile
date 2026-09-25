package com.garfiec.librechat.core.network.client

import com.garfiec.librechat.core.logging.redact.LogRedactor
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test

/**
 * The refresh-token cookie that authenticates the server's local image mount, and — more importantly —
 * everywhere it must not go.
 *
 * The redirect and merge tests are the load-bearing ones. A `State`-phase authority check passes while
 * the credential still escapes, because Ktor's `HttpRedirect` re-executes below the request pipeline;
 * and a second `Cookie` line is accepted by some proxies and dropped by others, so a suite that only
 * asserts "contains refreshToken" goes green on a build that breaks gateway users.
 */
class ImageCookiePluginTest {

    private companion object {
        const val SERVER = "https://chat.example.com"
        const val ACCOUNT = "srv-1:user-a"
        const val TOKEN = "refresh-token-1"
        const val ROTATED = "refresh-token-2"
    }

    private class FakeCredentials(private vararg val tokens: String?) : ImageCookieCredentials {
        var calls = 0
        val accountsAsked = mutableListOf<String?>()

        override suspend fun refreshTokenFor(accountId: String?): String? {
            accountsAsked += accountId
            return tokens[minOf(calls++, tokens.lastIndex)]
        }
    }

    private class FakeServerUrlProvider(private val baseUrl: String) : ServerUrlProvider {
        override fun getBaseUrl(): String = baseUrl
    }

    private class FakeHeadersProvider(private val headers: Map<String, String>) : ServerHeadersProvider {
        override suspend fun awaitWarm() = Unit
        override fun headersFor(baseUrl: String): Map<String, String> = headers
    }

    /** Inert: this suite never exercises the bearer, only the install order around it. */
    private class InertTokenManager : TokenManager {
        override val sessionExpiredFlow: SharedFlow<SessionEndReason> = MutableSharedFlow()
        override val isAuthenticated: Boolean = true
        override suspend fun getAccessToken(): String? = "bearer-token"
        override suspend fun setTokens(accessToken: String, refreshToken: String) = Unit
        override suspend fun refreshAccessToken(usedAccessToken: String?): RefreshResult = RefreshResult.Transient
        override suspend fun clearTokens() = Unit
        override suspend fun getAccessTokenFor(accountId: String): String? = "bearer-token"
        override suspend fun getStagedAccessToken(): String? = null
        override suspend fun clearStagedTokens() = Unit
        override suspend fun selectAccount(accountId: String) = Unit
        override suspend fun removeAccount(accountId: String) = Unit
        override suspend fun refreshAccessTokenFor(
            accountId: String,
            baseUrl: String,
            usedAccessToken: String?,
        ): RefreshResult = RefreshResult.Transient

        override suspend fun onAccountResolved(accountId: String) = Unit
        override fun emitSessionExpired(expiredAccountId: String?, reason: SessionEndReason) = Unit
    }

    private fun identity(
        baseUrl: String = SERVER,
        accountId: String? = ACCOUNT,
        isPending: Boolean = false,
        customHeaders: Map<String, String> = emptyMap(),
    ) = RequestIdentity(
        baseUrl = baseUrl,
        accountId = accountId,
        bearer = "bearer-token",
        isPending = isPending,
        // The switch barrier reads the gateway headers into the snapshot under its own lock, and
        // ServerHeadersPlugin prefers them over a fresh provider read for exactly that reason.
        customHeaders = customHeaders,
    )

    /**
     * The production install order — `ImageCookiePlugin` before `ServerHeadersPlugin`, so the app's
     * cookie is already on the request when the gateway cookie merges into the same line.
     */
    private fun createClient(
        engine: MockEngine,
        credentials: ImageCookieCredentials,
        serverUrl: String = SERVER,
        headersProvider: ServerHeadersProvider = EmptyServerHeadersProvider,
    ): HttpClient = HttpClient(engine) {
        install(ImageCookiePlugin) {
            this.credentials = credentials
            this.serverUrlProvider = FakeServerUrlProvider(serverUrl)
        }
        install(ServerHeadersPlugin) {
            this.serverHeadersProvider = headersProvider
            this.serverUrlProvider = FakeServerUrlProvider(serverUrl)
        }
    }

    /** The already-built [engine], for the one test that goes through the real client factory. */
    private fun factoryOf(engine: MockEngine) = object : HttpClientEngineFactory<MockEngineConfig> {
        override fun create(block: MockEngineConfig.() -> Unit): HttpClientEngine = engine
    }

    @Suppress("NoGetOutsideModuleDefinition") // Ktor's Headers.getAll, not Koin's.
    private fun HttpRequestData.cookieLines(): List<String> = headers.getAll(HttpHeaders.Cookie).orEmpty()

    private fun HttpRequestData.cookie(): String? = headers[HttpHeaders.Cookie]

    @Test
    fun `attaches the refresh cookie to an image request`() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            seen += request
            respond("png", HttpStatusCode.OK)
        }
        val credentials = FakeCredentials(TOKEN)

        createClient(engine, credentials).get("$SERVER/images/user-1/img.png") {
            attributes.put(RequestIdentityKey, identity())
        }

        assertThat(seen.single().cookieLines()).containsExactly("refreshToken=$TOKEN")
        assertThat(credentials.accountsAsked).containsExactly(ACCOUNT)
    }

    @Test
    fun `does not attach the cookie to an api request`() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            seen += request
            respond("{}", HttpStatusCode.OK)
        }
        val credentials = FakeCredentials(TOKEN)

        createClient(engine, credentials).get("$SERVER/api/convos") {
            attributes.put(RequestIdentityKey, identity())
        }

        assertThat(seen.single().cookie()).isNull()
        assertThat(credentials.calls).isEqualTo(0)
    }

    /** Upstream computes `imagesPath = ${basePath}/images`; a bare `/images/` prefix match misses these. */
    @Test
    fun `matches the image mount of a base-path deployment`() = runTest {
        val base = "$SERVER/librechat"
        val seen = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            seen += request
            respond("png", HttpStatusCode.OK)
        }

        createClient(engine, FakeCredentials(TOKEN), serverUrl = base)
            .get("$base/images/user-1/img.png") {
                attributes.put(RequestIdentityKey, identity(baseUrl = base))
            }

        assertThat(seen.single().cookieLines()).containsExactly("refreshToken=$TOKEN")
    }

    /** …and the mirror case: host-root `/images/` is not that deployment's mount. */
    @Test
    fun `does not attach outside the deployment's own image mount`() = runTest {
        val base = "$SERVER/librechat"
        val seen = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            seen += request
            respond("png", HttpStatusCode.OK)
        }

        createClient(engine, FakeCredentials(TOKEN), serverUrl = base)
            .get("$SERVER/images/user-1/img.png") {
                attributes.put(RequestIdentityKey, identity(baseUrl = base))
            }

        assertThat(seen.single().cookie()).isNull()
    }

    /** The presigned-CDN arm of `ImageUrlResolver` points off-server; authority gating is what covers it. */
    @Test
    fun `does not attach to a foreign authority`() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            seen += request
            respond("png", HttpStatusCode.OK)
        }

        createClient(engine, FakeCredentials(TOKEN))
            .get("https://d111.cloudfront.net/images/user-1/img.png?sig=xyz") {
                attributes.put(RequestIdentityKey, identity())
            }

        assertThat(seen.single().cookie()).isNull()
    }

    /** `isSameHostAsServer` would pass this; the refresh token must not go out in cleartext. */
    @Test
    fun `does not attach over a same-host http downgrade`() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            seen += request
            respond("png", HttpStatusCode.OK)
        }

        createClient(engine, FakeCredentials(TOKEN))
            .get("http://chat.example.com/images/user-1/img.png") {
                attributes.put(RequestIdentityKey, identity())
            }

        assertThat(seen.single().cookie()).isNull()
    }

    @Test
    fun `attaches nothing when the request has no resolved account`() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            seen += request
            respond("png", HttpStatusCode.OK)
        }
        val credentials = FakeCredentials(TOKEN)

        createClient(engine, credentials).get("$SERVER/images/user-1/img.png") {
            attributes.put(RequestIdentityKey, identity(accountId = null))
        }

        assertThat(seen.single().cookie()).isNull()
        assertThat(credentials.calls).isEqualTo(0)
    }

    /** An add-account probe authenticates against a server the user has not finished signing in to. */
    @Test
    fun `attaches nothing under a pending identity`() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            seen += request
            respond("png", HttpStatusCode.OK)
        }
        val credentials = FakeCredentials(TOKEN)

        createClient(engine, credentials).get("$SERVER/images/user-1/img.png") {
            attributes.put(RequestIdentityKey, identity(accountId = null, isPending = true))
        }

        assertThat(seen.single().cookie()).isNull()
        assertThat(credentials.calls).isEqualTo(0)
    }

    /** No snapshot, no account: fail closed rather than falling back to whatever is active. */
    @Test
    fun `attaches nothing to a request carrying no identity snapshot`() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            seen += request
            respond("png", HttpStatusCode.OK)
        }

        createClient(engine, FakeCredentials(TOKEN)).get("$SERVER/images/user-1/img.png")

        assertThat(seen.single().cookie()).isNull()
    }

    /**
     * `HttpRedirect` copies every header to the new target and strips only `Authorization`, and
     * `stripCustomHeaders` unpicks only the *user's* cookie segments — so without the plugin's own
     * `HttpSend` guard a `/images/` 302 off-domain hands the refresh token to a foreign host.
     */
    @Test
    fun `strips the cookie on a cross-authority redirect and re-applies on return`() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            seen += request
            when (seen.size) {
                1 -> respond(
                    content = "",
                    status = HttpStatusCode.Found,
                    headers = headersOf(HttpHeaders.Location, "https://objects.example.net/blob/1"),
                )
                2 -> respond(
                    content = "",
                    status = HttpStatusCode.Found,
                    headers = headersOf(HttpHeaders.Location, "$SERVER/images/user-1/img.png"),
                )
                else -> respond("png", HttpStatusCode.OK)
            }
        }

        createClient(engine, FakeCredentials(TOKEN)).get("$SERVER/images/user-1/img.png") {
            attributes.put(RequestIdentityKey, identity())
        }

        assertThat(seen).hasSize(3)
        assertThat(seen[0].cookieLines()).containsExactly("refreshToken=$TOKEN")
        assertThat(seen[1].cookie()).isNull()
        assertThat(seen[2].cookieLines()).containsExactly("refreshToken=$TOKEN")
    }

    /** A redirect that stays on the server but leaves the mount is no longer the mount. */
    @Test
    fun `strips the cookie on a same-authority redirect off the image mount`() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            seen += request
            if (seen.size == 1) {
                respond(
                    content = "",
                    status = HttpStatusCode.Found,
                    headers = headersOf(HttpHeaders.Location, "$SERVER/api/files/download/u1/f1"),
                )
            } else {
                respond("png", HttpStatusCode.OK)
            }
        }

        createClient(engine, FakeCredentials(TOKEN)).get("$SERVER/images/user-1/img.png") {
            attributes.put(RequestIdentityKey, identity())
        }

        assertThat(seen).hasSize(2)
        assertThat(seen[1].cookie()).isNull()
    }

    /**
     * One `Cookie` line, the user's gateway segments first, and the app's `refreshToken` winning a
     * name collision — built through the real client factory so the test observes the production
     * install order rather than this file's idea of it.
     */
    @Test
    fun `merges with a gateway cookie into one line and keeps the app's segment`() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            seen += request
            respond("png", HttpStatusCode.OK)
        }
        val gatewayCookie = mapOf(HttpHeaders.Cookie to "cf_authorization=gw-value; refreshToken=stale-devtools-copy")

        val client = LibreChatHttpClient.create(
            engineFactory = factoryOf(engine),
            json = Json { ignoreUnknownKeys = true },
            tokenManager = InertTokenManager(),
            serverUrlProvider = FakeServerUrlProvider(SERVER),
            redactor = LogRedactor(),
            imageCookieCredentials = FakeCredentials(TOKEN),
            serverHeadersProvider = FakeHeadersProvider(gatewayCookie),
        )

        client.get("$SERVER/images/user-1/img.png") {
            attributes.put(RequestIdentityKey, identity(customHeaders = gatewayCookie))
        }

        assertThat(seen.single().cookieLines())
            .containsExactly("cf_authorization=gw-value; refreshToken=$TOKEN")
    }

    /**
     * rc3 matches the cookie against `session.refreshTokenHash`, which every refresh overwrites — so a
     * token read microseconds before a concurrent rotation comes back 403, not 401.
     */
    @Test
    fun `retries a 403 once with the rotated token`() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            seen += request
            if (seen.size == 1) respondError(HttpStatusCode.Forbidden) else respond("png", HttpStatusCode.OK)
        }

        val response = createClient(engine, FakeCredentials(TOKEN, ROTATED))
            .get("$SERVER/images/user-1/img.png") {
                attributes.put(RequestIdentityKey, identity())
            }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        assertThat(seen).hasSize(2)
        assertThat(seen[0].cookieLines()).containsExactly("refreshToken=$TOKEN")
        assertThat(seen[1].cookieLines()).containsExactly("refreshToken=$ROTATED")
    }

    /** An unchanged token means the server rejected that value on its merits. Resending it is noise. */
    @Test
    fun `does not retry a 403 when the token has not rotated`() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            seen += request
            respondError(HttpStatusCode.Forbidden)
        }
        val credentials = FakeCredentials(TOKEN)

        val response = createClient(engine, credentials).get("$SERVER/images/user-1/img.png") {
            attributes.put(RequestIdentityKey, identity())
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.Forbidden)
        assertThat(seen).hasSize(1)
        assertThat(credentials.calls).isEqualTo(2)
    }

    /** One shot, even when the token keeps rotating underneath. */
    @Test
    fun `retries a 403 at most once`() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            seen += request
            respondError(HttpStatusCode.Forbidden)
        }

        val response = createClient(engine, FakeCredentials(TOKEN, ROTATED, "refresh-token-3"))
            .get("$SERVER/images/user-1/img.png") {
                attributes.put(RequestIdentityKey, identity())
            }

        assertThat(response.status).isEqualTo(HttpStatusCode.Forbidden)
        assertThat(seen).hasSize(2)
    }

    /** A CDN 403 arrives on a hop we stripped for; there is no cookie of ours to rotate. */
    @Test
    fun `does not retry a 403 from a foreign authority`() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            seen += request
            respondError(HttpStatusCode.Forbidden)
        }
        val credentials = FakeCredentials(TOKEN)

        createClient(engine, credentials).get("https://d111.cloudfront.net/images/x.png") {
            attributes.put(RequestIdentityKey, identity())
        }

        assertThat(seen).hasSize(1)
        assertThat(credentials.calls).isEqualTo(0)
    }

    /** Read at attach time, never cached: a second image picks up a token rotated since the first. */
    @Test
    fun `reads the token afresh on every request`() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            seen += request
            respond("png", HttpStatusCode.OK)
        }
        val client = createClient(engine, FakeCredentials(TOKEN, ROTATED))

        client.get("$SERVER/images/user-1/a.png") { attributes.put(RequestIdentityKey, identity()) }
        client.get("$SERVER/images/user-1/b.png") { attributes.put(RequestIdentityKey, identity()) }

        assertThat(seen[0].cookieLines()).containsExactly("refreshToken=$TOKEN")
        assertThat(seen[1].cookieLines()).containsExactly("refreshToken=$ROTATED")
    }
}
