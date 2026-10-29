package com.garfiec.librechat.core.data.datastore

import com.garfiec.librechat.core.model.auth.AuthCookies
import com.garfiec.librechat.core.model.auth.OpenIdMarkers
import com.garfiec.librechat.core.model.auth.RequestAuth
import com.garfiec.librechat.core.model.auth.SessionCredentials
import com.garfiec.librechat.core.network.client.RefreshResult
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CommonTokenDataStoreOpenIdTest {
    private val noRefresh: Lazy<HttpClient> = lazy { error("Unexpected refresh") }
    private val original = SessionCredentials("old-access", AuthCookies("old-refresh", OpenIdMarkers("old-marker")))

    @Test
    fun `concurrent reuse refresh callers share one complete rotation`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var posts = 0
        val engine = unconfinedMockEngine {
            posts++
            entered.complete(Unit)
            release.await()
            respond(refreshResponseBody("new-access"), headers = headersOf(
                HttpHeaders.ContentType to listOf("application/json"),
                HttpHeaders.SetCookie to listOf("refreshToken=new-refresh", "openid_user_id=new-marker"),
            ))
        }
        val store = FakeTokenStore(refreshClientOf(engine))
        store.setSession(original)
        store.onAccountResolved("A")
        val refreshes = List(8) { async { store.refreshAccessTokenFor("A", TEST_SERVER, "old-access") } }
        entered.await()
        release.complete(Unit)
        assertThat(refreshes.awaitAll()).containsExactlyElementsIn(List(8) { RefreshResult.Refreshed })
        assertThat(posts).isEqualTo(1)
        assertThat(store.getRequestAuthFor("A")).isEqualTo(RequestAuth("new-access", OpenIdMarkers("new-marker")))
    }

    @Test
    fun `the active slot's request auth is served from memory`() = runTest {
        val store = FakeTokenStore(noRefresh)
        store.setSession(original)
        store.onAccountResolved("A")
        val reads = store.readCount
        repeat(5) {
            assertThat(store.getRequestAuthFor("A")).isEqualTo(RequestAuth("old-access", original.cookies.openId))
        }
        assertThat(store.readCount).isEqualTo(reads)
    }

    @Test
    fun `clearing the staged session with no account bound drops its cached auth`() = runTest {
        val store = FakeTokenStore(noRefresh)
        store.setSession(original)
        store.clearStagedTokens()
        assertThat(store.getStagedRequestAuth()).isEqualTo(RequestAuth(null))
        assertThat(store.isAuthenticated).isFalse()
    }

    @Test
    fun `staging resolving switching and restarting retain account-local bundles`() = runTest {
        val store = FakeTokenStore(noRefresh, mapOf(
            ACTIVE_ACCOUNT_KEY to "A", accessKeyOf("A") to "a-access", refreshKeyOf("A") to "a-refresh",
        ))
        store.setSession(original)
        assertThat(store.getRequestAuthFor("A")).isEqualTo(RequestAuth("a-access"))
        assertThat(store.getStagedRequestAuth()).isEqualTo(RequestAuth("old-access", original.cookies.openId))
        store.onAccountResolved("B")
        assertThat(store.persistedSession("B")).isEqualTo(original)
        assertThat(store.store).doesNotContainKey(refreshKeyOf("B"))
        assertThat(store.store).doesNotContainKey(CommonTokenDataStore.KEY_SESSION)
        store.selectAccount("A")
        assertThat(store.getRequestAuthFor("A")).isEqualTo(RequestAuth("a-access"))
        store.selectAccount("B")
        val restarted = FakeTokenStore(noRefresh, store.store.toMap())
        assertThat(restarted.getAccessToken()).isEqualTo("old-access")
        assertThat(restarted.authCookiesFor("B")).isEqualTo(original.cookies)
    }

    @Test
    fun `background refresh sends and persists the complete rotated session`() = runTest {
        val engine = unconfinedMockEngine { request ->
            assertThat(request.headers.getAll(HttpHeaders.Cookie)).containsExactly(
                "refreshToken=old-refresh; token_provider=openid; openid_user_id=old-marker",
            )
            respond(refreshResponseBody("new-access"), headers = headersOf(
                HttpHeaders.ContentType to listOf("application/json"),
                HttpHeaders.SetCookie to listOf("refreshToken=new-refresh; HttpOnly", "openid_user_id=new-marker; HttpOnly"),
            ))
        }
        val store = FakeTokenStore(refreshClientOf(engine))
        store.setSession(original)
        store.onAccountResolved("A")
        assertThat(store.refreshAccessTokenFor("A", TEST_SERVER)).isEqualTo(RefreshResult.Refreshed)
        val expected = SessionCredentials("new-access", AuthCookies("new-refresh", OpenIdMarkers("new-marker")))
        assertThat(store.persistedSession("A")).isEqualTo(expected)
        assertThat(store.getRequestAuthFor("A")).isEqualTo(RequestAuth("new-access", expected.cookies.openId))
        assertThat(FakeTokenStore(noRefresh, store.store.toMap()).sessionFor("A")).isEqualTo(expected)
    }

    @Test
    fun `incomplete rotation is transient and does not publish a mismatched pair`() = runTest {
        val engine = unconfinedMockEngine {
            respond(refreshResponseBody("new-access"), headers = headersOf(
                HttpHeaders.ContentType to listOf("application/json"),
                HttpHeaders.SetCookie to listOf("refreshToken=new-refresh; HttpOnly"),
            ))
        }
        val store = FakeTokenStore(refreshClientOf(engine))
        store.setSession(original)
        store.onAccountResolved("A")
        assertThat(store.refreshAccessTokenFor("A", TEST_SERVER)).isEqualTo(RefreshResult.Transient)
        assertThat(store.persistedSession("A")).isEqualTo(original)
    }

    @Test
    fun `normal reauthentication clears the previous OpenId markers`() = runTest {
        val store = FakeTokenStore(noRefresh)
        store.setSession(original)
        store.onAccountResolved("A")
        store.setTokens("normal-access", "normal-refresh")
        store.onAccountResolved("A")
        assertThat(store.authCookiesFor("A")).isEqualTo(AuthCookies("normal-refresh"))
        assertThat(store.getRequestAuthFor("A")).isEqualTo(RequestAuth("normal-access"))
    }

    @Test
    fun `removal while refresh waits cannot resurrect any session fields`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val engine = unconfinedMockEngine {
            entered.complete(Unit)
            release.await()
            respond(refreshResponseBody("new-access"), headers = headersOf(
                HttpHeaders.ContentType to listOf("application/json"),
                HttpHeaders.SetCookie to listOf("refreshToken=new-refresh", "openid_user_id=new-marker"),
            ))
        }
        val store = FakeTokenStore(refreshClientOf(engine))
        store.setSession(original)
        store.onAccountResolved("A")
        val refresh = async { store.refreshAccessTokenFor("A", TEST_SERVER) }
        entered.await()
        store.removeAccount("A")
        release.complete(Unit)
        assertThat(refresh.await()).isEqualTo(RefreshResult.Transient)
        assertThat(store.sessionFor("A")).isNull()
        assertThat(store.authCookiesFor("A")).isNull()
        assertThat(store.store).doesNotContainKey(sessionKeyOf("A"))
    }

    @Test
    fun `cancelling a refresh preserves the original session`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val engine = unconfinedMockEngine { entered.complete(Unit); awaitCancellation() }
        val store = FakeTokenStore(refreshClientOf(engine))
        store.setSession(original)
        store.onAccountResolved("A")
        val refresh = async { store.refreshAccessTokenFor("A", TEST_SERVER) }
        entered.await()
        refresh.cancelAndJoin()
        assertThat(store.persistedSession("A")).isEqualTo(original)
        assertThat(store.isAuthenticated).isTrue()
    }

    @Test
    fun `an invalid record does not fall back to stale legacy credentials`() = runTest {
        val store = FakeTokenStore(noRefresh, mapOf(
            ACTIVE_ACCOUNT_KEY to "A", sessionKeyOf("A") to "{broken",
            accessKeyOf("A") to "stale-access", refreshKeyOf("A") to "stale-refresh",
        ))
        assertThat(store.isAuthenticated).isFalse()
        assertThat(store.getRequestAuthFor("A")).isEqualTo(RequestAuth(null))
        assertThat(store.authCookiesFor("A")).isNull()
    }
}
