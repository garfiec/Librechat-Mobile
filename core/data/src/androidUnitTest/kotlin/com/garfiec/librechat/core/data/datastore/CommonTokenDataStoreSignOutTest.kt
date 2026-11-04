package com.garfiec.librechat.core.data.datastore

import com.garfiec.librechat.core.network.client.RefreshResult
import com.garfiec.librechat.core.network.client.SessionEndReason
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * A sign-out revokes the server session before the local teardown runs. A request that fails its
 * refresh in that window is a consequence of the sign-out, not an expiry, and must not report one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CommonTokenDataStoreSignOutTest {

    private companion object {
        const val SERVER = "https://chat.example.com"
    }

    // The logout POST has revoked the session, so the refresh is refused.
    private val revokedRefresh: Lazy<HttpClient> = lazy {
        HttpClient(MockEngine { respondError(HttpStatusCode.Unauthorized) }) {
            install(ContentNegotiation) { json() }
            defaultRequest {
                url(SERVER)
                contentType(ContentType.Application.Json)
            }
        }
    }

    private fun twoAccountStore() = FakeTokenStore(
        revokedRefresh,
        seed = mapOf(
            "active_account_id" to "acctA",
            accessKeyOf("acctA") to "A-access",
            refreshKeyOf("acctA") to "A-refresh",
            accessKeyOf("acctB") to "B-access",
            refreshKeyOf("acctB") to "B-refresh",
        ),
    )

    private fun TestScope.collectReasons(store: FakeTokenStore): MutableList<SessionEndReason> {
        val reasons = mutableListOf<SessionEndReason>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            store.sessionExpiredFlow.collect { reasons += it }
        }
        return reasons
    }

    @Test
    fun `a refresh failing between the logout POST and the teardown reports no expiry`() = runTest {
        val store = twoAccountStore()
        val reasons = collectReasons(store)

        store.beginSignOut("acctA")
        val result = store.refreshAccessTokenFor("acctA", SERVER)
        store.emitSessionExpired("acctA")

        assertThat(result).isEqualTo(RefreshResult.HardExpired)
        assertThat(reasons).isEmpty()
    }

    @Test
    fun `the sign-out's own signal still goes out after the teardown`() = runTest {
        val store = twoAccountStore()
        val reasons = collectReasons(store)

        store.beginSignOut("acctA")
        store.onAccountCleared()
        store.removeAccount("acctA")
        store.emitSessionExpired(reason = SessionEndReason.SIGNED_OUT)

        assertThat(reasons).containsExactly(SessionEndReason.SIGNED_OUT)
    }

    @Test
    fun `signing back in re-arms the account's expiry report`() = runTest {
        val store = twoAccountStore()
        val reasons = collectReasons(store)

        store.beginSignOut("acctA")
        store.onAccountCleared()
        store.setTokens("A-access-2", "A-refresh-2")
        store.onAccountResolved("acctA")
        store.emitSessionExpired("acctA")

        assertThat(reasons).containsExactly(SessionEndReason.EXPIRED)
    }

    @Test
    fun `the account the sign-out moves to still reports its own expiry`() = runTest {
        val store = twoAccountStore()
        val reasons = collectReasons(store)

        store.beginSignOut("acctA")
        store.selectAccount("acctB")
        store.removeAccount("acctA")
        store.emitSessionExpired("acctB")

        assertThat(reasons).containsExactly(SessionEndReason.EXPIRED)
    }

    @Test
    fun `signing out an account that is no longer active leaves the live one's report armed`() = runTest {
        val store = twoAccountStore()
        val reasons = collectReasons(store)

        store.selectAccount("acctB")
        store.beginSignOut("acctA")
        store.emitSessionExpired("acctB")

        assertThat(reasons).containsExactly(SessionEndReason.EXPIRED)
    }
}
