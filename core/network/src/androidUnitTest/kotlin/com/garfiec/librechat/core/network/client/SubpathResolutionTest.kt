package com.garfiec.librechat.core.network.client

import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.common.identity.AccountState
import com.garfiec.librechat.core.common.identity.InMemoryActiveAccountProvider
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.http.appendPathSegments
import io.ktor.http.path
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Where a request lands for a given stored base URL, through both plugins that resolve one —
 * [SwitchBarrierPlugin] (main and streaming clients) and [ServerUrlReadyPlugin] (the refresh client,
 * and the main client without a gate). A deployment reverse-proxied under a path
 * (`https://host/librechat`) must get every call under that path, whichever form the service wrote
 * the path in; a host-root deployment must land exactly where it always has.
 */
class SubpathResolutionTest {

    private class NoTokens : TokenManager {
        override val isAuthenticated: Boolean get() = false
        override suspend fun getAccessToken(): String? = null
        override suspend fun getAccessTokenFor(accountId: String): String? = null
        override suspend fun setTokens(accessToken: String, refreshToken: String) = Unit
        override suspend fun refreshAccessToken(usedAccessToken: String?): RefreshResult =
            RefreshResult.Transient

        override suspend fun refreshAccessTokenFor(
            accountId: String,
            baseUrl: String,
            usedAccessToken: String?,
        ): RefreshResult = RefreshResult.Transient

        override suspend fun ensureFreshAccessToken(
            accountId: String?,
            baseUrl: String,
            currentAccessToken: String?,
        ): String? = currentAccessToken

        override suspend fun clearTokens() = Unit
        override suspend fun getStagedAccessToken(): String? = null
        override suspend fun clearStagedTokens() = Unit
        override suspend fun selectAccount(accountId: String) = Unit
        override suspend fun removeAccount(accountId: String) = Unit
        override suspend fun onAccountResolved(accountId: String) = Unit
        override fun emitSessionExpired(expiredAccountId: String?, reason: SessionEndReason) = Unit
        override val sessionExpiredFlow: SharedFlow<SessionEndReason> = MutableSharedFlow()
    }

    private class FixedUrlProvider(private val baseUrl: String) : ServerUrlProvider {
        override fun getBaseUrl(): String = baseUrl
    }

    private enum class Resolver { Barrier, Readiness }

    private val sent = mutableListOf<String>()

    private fun client(baseUrl: String, resolver: Resolver): HttpClient {
        val provider = FixedUrlProvider(baseUrl)
        return HttpClient(MockEngine { request -> sent += request.url.toString(); respond("{}", HttpStatusCode.OK) }) {
            when (resolver) {
                Resolver.Barrier -> install(SwitchBarrierPlugin) {
                    switchGate = SwitchGate(
                        activeAccountProvider = InMemoryActiveAccountProvider(
                            AccountState.Resolved(AccountId("acct-a")),
                        ),
                        serverUrlProvider = provider,
                        tokenManager = NoTokens(),
                        accountReadyGate = null,
                    )
                }
                Resolver.Readiness -> install(ServerUrlReadyPlugin) { serverUrlProvider = provider }
            }
            // The production clients still install a defaultRequest (UA, content type); it must not
            // re-merge a URL the resolver has already settled.
            defaultRequest { applyBrowserDefaults() }
        }
    }

    /** Runs [block] against a fresh client per resolver, barrier first, and returns every URL sent. */
    private fun urlsFor(baseUrl: String, block: suspend HttpClient.() -> Unit): List<String> {
        runTest {
            Resolver.entries.forEach { client(baseUrl, it).block() }
        }
        return sent.toList().also { sent.clear() }
    }

    @Test
    fun `a relative service path stays under the subpath`() {
        val urls = urlsFor("https://example.com/librechat") { get { url { path("api/config") } } }

        assertThat(urls).containsExactly(
            "https://example.com/librechat/api/config",
            "https://example.com/librechat/api/config",
        )
    }

    @Test
    fun `an absolute request path stays under the subpath`() {
        val urls = urlsFor("https://example.com/librechat") { post("/api/auth/refresh") }

        assertThat(urls).containsExactly(
            "https://example.com/librechat/api/auth/refresh",
            "https://example.com/librechat/api/auth/refresh",
        )
    }

    @Test
    fun `appended path segments stay under the subpath`() {
        val urls = urlsFor("https://example.com/librechat") {
            get { url { appendPathSegments("api", "skills", "a b") } }
        }

        assertThat(urls).containsExactly(
            "https://example.com/librechat/api/skills/a%20b",
            "https://example.com/librechat/api/skills/a%20b",
        )
    }

    @Test
    fun `a nested subpath keeps every segment`() {
        val urls = urlsFor("https://example.com/apps/librechat") { get { url { path("api/config") } } }

        assertThat(urls).containsExactly(
            "https://example.com/apps/librechat/api/config",
            "https://example.com/apps/librechat/api/config",
        )
    }

    /** The negative control: the host-root shape is byte-identical in every request form. */
    @Test
    fun `a host-root deployment lands exactly where it did`() {
        val urls = urlsFor("https://chat.example.com") {
            get { url { path("api/config") } }
            post("/api/auth/refresh")
            get("api/convos?cursor=abc")
        }

        assertThat(urls).containsExactly(
            "https://chat.example.com/api/config",
            "https://chat.example.com/api/auth/refresh",
            "https://chat.example.com/api/convos?cursor=abc",
            "https://chat.example.com/api/config",
            "https://chat.example.com/api/auth/refresh",
            "https://chat.example.com/api/convos?cursor=abc",
        ).inOrder()
    }

    @Test
    fun `a stored trailing slash does not double the separator`() {
        val urls = urlsFor("https://example.com/librechat/") { get { url { path("api/config") } } }

        assertThat(urls).containsExactly(
            "https://example.com/librechat/api/config",
            "https://example.com/librechat/api/config",
        )
    }

    @Test
    fun `an explicit port on the base is kept`() {
        val urls = urlsFor("http://192.168.1.5:3080/librechat") { get { url { path("api/config") } } }

        assertThat(urls).containsExactly(
            "http://192.168.1.5:3080/librechat/api/config",
            "http://192.168.1.5:3080/librechat/api/config",
        )
    }

    /** A stored pasted web-UI address: the page path and its query are not part of the root. */
    @Test
    fun `a pasted login page resolves to its deployment root`() {
        val hostRoot =
            urlsFor("https://example.com/login?redirect_to=%2Fc%2Fnew") { get { url { path("api/config") } } }
        val subpath = urlsFor("https://example.com/librechat/c/new") { get { url { path("api/config") } } }

        assertThat(hostRoot).containsExactly("https://example.com/api/config", "https://example.com/api/config")
        assertThat(subpath).containsExactly(
            "https://example.com/librechat/api/config",
            "https://example.com/librechat/api/config",
        )
    }

    @Test
    fun `the request keeps its own query and the base's is dropped`() {
        val urls = urlsFor("https://example.com/librechat?x=1") { get("api/agents/chat/stream/s1?resume=true") }

        assertThat(urls).containsExactly(
            "https://example.com/librechat/api/agents/chat/stream/s1?resume=true",
            "https://example.com/librechat/api/agents/chat/stream/s1?resume=true",
        )
    }

    /** A presigned CDN fetch already names its host; the base must not be grafted onto it. */
    @Test
    fun `an absolute cross-host url is left alone`() {
        val urls = urlsFor("https://example.com/librechat") { get("https://cdn.example.net/bucket/f.png?sig=1") }

        assertThat(urls).containsExactly(
            "https://cdn.example.net/bucket/f.png?sig=1",
            "https://cdn.example.net/bucket/f.png?sig=1",
        )
    }
}
