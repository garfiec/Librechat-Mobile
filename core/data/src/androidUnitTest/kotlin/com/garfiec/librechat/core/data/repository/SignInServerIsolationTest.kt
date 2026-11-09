package com.garfiec.librechat.core.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.garfiec.librechat.core.common.identity.AccountState.Resolved
import com.garfiec.librechat.core.common.identity.InMemoryActiveAccountProvider
import com.garfiec.librechat.core.common.identity.deriveAccountId
import com.garfiec.librechat.core.common.identity.deriveServerId
import com.garfiec.librechat.core.data.datastore.ACTIVE_ACCOUNT_KEY
import com.garfiec.librechat.core.data.datastore.AccountEntry
import com.garfiec.librechat.core.data.datastore.AccountRoster
import com.garfiec.librechat.core.data.datastore.AccountScopedPrefsPurger
import com.garfiec.librechat.core.data.datastore.FakeTokenStore
import com.garfiec.librechat.core.data.datastore.ServerDataStore
import com.garfiec.librechat.core.data.datastore.accessKeyOf
import com.garfiec.librechat.core.data.datastore.refreshClientOf
import com.garfiec.librechat.core.data.datastore.refreshKeyOf
import com.garfiec.librechat.core.data.datastore.unconfinedMockEngine
import com.garfiec.librechat.core.network.client.AuthInterceptorPlugin
import com.garfiec.librechat.core.network.client.SwitchBarrierPlugin
import com.garfiec.librechat.core.network.client.SwitchGate
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertFailsWith

/** Real token storage, roster and HTTP plugins exercise both ways to reach issue #660. */
@OptIn(ExperimentalCoroutinesApi::class)
class SignInServerIsolationTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val serverA = "https://a.example.com"
    private val serverB = "https://b.example.com"
    private val accountA = deriveAccountId(deriveServerId(serverA), "user-a")
    private val accountB = deriveAccountId(deriveServerId(serverB), "user-b")
    private val sent = mutableListOf<Pair<String, String?>>()
    private val refreshPosts = mutableListOf<String>()

    private inner class Harness(scope: TestScope) {
        private val dispatcher = UnconfinedTestDispatcher(scope.testScheduler)
        private val preferences = PreferenceDataStoreFactory.create(scope = scope.backgroundScope) {
            tmp.newFile("session.preferences_pb")
        }
        val roster = AccountRoster(preferences, Json)
        val server = ServerDataStore(preferences, scope.backgroundScope, dispatcher)
        val provider = InMemoryActiveAccountProvider(Resolved(accountA))
        val tokens = FakeTokenStore(
            refreshClientOf(unconfinedMockEngine { request ->
                refreshPosts += request.url.toString()
                respond("{}", HttpStatusCode.Unauthorized)
            }),
            seed = mapOf(
                ACTIVE_ACCOUNT_KEY to accountA.value,
                accessKeyOf(accountA.value) to "a-access",
                refreshKeyOf(accountA.value) to "a-refresh",
                accessKeyOf(accountB.value) to "b-access",
                refreshKeyOf(accountB.value) to "b-refresh",
            ),
        )
        val gate = SwitchGate(provider, server, tokens, accountReadyGate = null)
        val purger = mockk<AccountDataPurger>(relaxed = true)
        val switcher = AccountSwitcher(
            roster, server, tokens, provider, gate,
            claimReconciler = mockk(relaxed = true),
            switchCacheCleaner = mockk(relaxed = true),
            accountDataPurger = purger,
            prefsPurger = AccountScopedPrefsPurger(preferences),
            sessionCacheCleaner = mockk(relaxed = true),
        )
        val client = HttpClient(unconfinedMockEngine { request ->
            sent += request.url.toString() to request.headers[HttpHeaders.Authorization]
            respond("Unauthorized", HttpStatusCode.Unauthorized)
        }) {
            install(SwitchBarrierPlugin) { switchGate = gate }
            install(AuthInterceptorPlugin) {
                tokenManager = tokens
                serverUrlProvider = server
            }
        }

        suspend fun seed() {
            server.setServerUrl(serverA)
            server.awaitBaseUrl()
            roster.upsertAndActivate(AccountEntry(accountA.value, serverA, "Account A", lastActiveAt = 1))
        }
    }

    @Test
    fun `out of band URL change cannot send the old bearer or refresh token`() = runTest {
        val h = Harness(this)
        h.seed()
        h.server.setServerUrl(serverB)

        try {
            assertFailsWith<IllegalStateException> { h.client.get("/api/convos") }

            assertThat(sent).isEmpty()
            assertThat(refreshPosts).isEmpty()
            assertThat(h.tokens.getAccessTokenFor(accountA.value)).isEqualTo("a-access")
        } finally {
            h.client.close()
        }
    }

    @Test
    fun `normal sign in detaches a live account before connecting to a different server`() = runTest {
        val h = Harness(this)
        h.seed()
        h.switcher.beginAdd(serverB)

        h.switcher.prepareForSignIn(serverB)

        assertThat(h.provider.state.value).isEqualTo(Resolved(null))
        assertThat(h.roster.snapshot().activeId).isNull()
        assertThat(h.roster.snapshot().entries.map { it.accountId }).containsExactly(accountA.value)
        assertThat(h.tokens.getAccessTokenFor(accountA.value)).isNull()
        assertThat(h.tokens.getAccessToken()).isNull()
        assertThat(h.tokens.store[refreshKeyOf(accountA.value)]).isNull()
        assertThat(h.tokens.store[refreshKeyOf(accountB.value)]).isEqualTo("b-refresh")
        assertThat(h.switcher.pendingAdd).isNull()
        coVerify(exactly = 0) { h.purger.purge(any()) }
        assertThat(h.switcher.signInReauthenticatesActive()).isFalse()

        try {
            val response = h.client.get("/api/convos")

            assertThat(response.status).isEqualTo(HttpStatusCode.Unauthorized)
            assertThat(sent).containsExactly("$serverB/api/convos" to null)
            assertThat(refreshPosts).isEmpty()
        } finally {
            h.client.close()
        }
    }
}
