package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.common.identity.AccountState
import com.garfiec.librechat.core.common.identity.ActiveAccountProvider
import com.garfiec.librechat.core.common.identity.SessionManager
import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.AccountEntry
import com.garfiec.librechat.core.data.datastore.AccountRegistry
import com.garfiec.librechat.core.data.datastore.AccountRoster
import com.garfiec.librechat.core.data.datastore.RosterSnapshot
import com.garfiec.librechat.core.data.util.SessionTaskRunner
import com.garfiec.librechat.core.network.api.AuthApi
import com.garfiec.librechat.core.network.api.UserApi
import com.garfiec.librechat.core.network.client.AuthInterceptorPlugin
import com.garfiec.librechat.core.network.client.EmptyServerHeadersProvider
import com.garfiec.librechat.core.network.client.ImageCookiePlugin
import com.garfiec.librechat.core.network.client.RefreshCookieCredentials
import com.garfiec.librechat.core.network.client.RequestIdentity
import com.garfiec.librechat.core.network.client.ServerHeadersPlugin
import com.garfiec.librechat.core.network.client.ServerHeadersProvider
import com.garfiec.librechat.core.network.client.ServerUrlProvider
import com.garfiec.librechat.core.network.client.SwitchBarrierPlugin
import com.garfiec.librechat.core.network.client.SwitchGate
import com.garfiec.librechat.core.network.client.TokenManager
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.request.HttpRequestData
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * Server-side revocation on logout and account removal, over the real request plugins that apply a
 * pinned identity's server, bearer and gateway headers.
 */
class AuthRepositoryImplLogoutTest {

    private val tokenManager = mockk<TokenManager>(relaxed = true)
    private val accountRegistry = mockk<AccountRegistry>(relaxed = true)
    private val activeAccountProvider = mockk<ActiveAccountProvider>(relaxed = true)
    private val sessionManager = mockk<SessionManager>(relaxed = true)
    private val accountSwitcher = mockk<AccountSwitcher>(relaxed = true)
    private val switchGate = mockk<SwitchGate>()
    private val roster = mockk<AccountRoster>()
    private val activeState = MutableStateFlow<AccountState>(AccountState.Resolved(AccountId(ACCOUNT_A)))

    /** Refresh tokens by account. Mutable so a renewal can rotate one, as the real store does. */
    private val refreshTokens = mutableMapOf<String, String>(ACCOUNT_A to "rt-a", ACCOUNT_B to "rt-b")
    private val credentials = RefreshCookieCredentials { id -> id?.let { refreshTokens[it] } }

    private val headersProvider = object : ServerHeadersProvider {
        override suspend fun awaitWarm() = Unit
        override fun headersFor(baseUrl: String): Map<String, String> =
            if (baseUrl == SERVER_B) mapOf("X-Gateway" to "gw-b") else emptyMap()
    }

    private var status = HttpStatusCode.OK
    private val engine = MockEngine { respond("""{"message":"Logout successful"}""", status) }

    @Before
    fun setUp() {
        every { activeAccountProvider.state } returns activeState
        coEvery { switchGate.captureSnapshot(any()) } returns
            RequestIdentity(baseUrl = SERVER_A, accountId = ACCOUNT_A, bearer = "bearer-a")
        coEvery { tokenManager.ensureFreshAccessToken(any(), any(), any()) } answers { thirdArg() }
        coEvery { tokenManager.getAccessTokenFor(ACCOUNT_B) } returns "bearer-b"
        coEvery { roster.snapshot() } returns RosterSnapshot(
            entries = listOf(entry(ACCOUNT_A, SERVER_A), entry(ACCOUNT_B, SERVER_B)),
            activeId = ACCOUNT_A,
        )
    }

    private fun entry(id: String, url: String) =
        AccountEntry(accountId = id, serverUrl = url, displayLabel = id, lastActiveAt = 0)

    private fun repo(): AuthRepositoryImpl {
        // A real gate for the client: under a pinned identity it short-circuits to it before reading
        // any of these collaborators, which is the path these tests exercise.
        val clientGate = SwitchGate(
            activeAccountProvider = activeAccountProvider,
            serverUrlProvider = mockk(relaxed = true),
            tokenManager = tokenManager,
            accountReadyGate = null,
        )
        // The main client's cookie- and header-writing plugins in production order (LibreChatHttpClient),
        // live server A, so the image cookie plugin and host-scoping both run against the logout POST.
        val liveServer = object : ServerUrlProvider {
            override fun getBaseUrl(): String = SERVER_A
        }
        val client = HttpClient(engine) {
            install(AuthInterceptorPlugin) {
                this.tokenManager = this@AuthRepositoryImplLogoutTest.tokenManager
                serverUrlProvider = liveServer
            }
            install(ImageCookiePlugin) {
                this.credentials = this@AuthRepositoryImplLogoutTest.credentials
                serverUrlProvider = liveServer
            }
            install(ServerHeadersPlugin) {
                serverHeadersProvider = EmptyServerHeadersProvider
                serverUrlProvider = liveServer
            }
            install(SwitchBarrierPlugin) { switchGate = clientGate }
            HttpResponseValidator {
                validateResponse { response ->
                    if (!response.status.isSuccess()) {
                        throw ApiException(response.status.value, response.bodyAsText().ifBlank { "error" })
                    }
                }
            }
        }
        val authApi = AuthApi(client)
        return AuthRepositoryImpl(
            authApi = authApi,
            userApi = mockk<UserApi>(relaxed = true),
            tokenManager = tokenManager,
            sessionCacheCleaner = mockk<SessionCacheCleaner>(relaxed = true),
            sessionTaskRunner = mockk<SessionTaskRunner>(relaxed = true),
            accountSessionEstablisher = mockk<AccountSessionEstablisher>(relaxed = true),
            accountRegistry = accountRegistry,
            activeAccountProvider = activeAccountProvider,
            sessionManager = sessionManager,
            accountSwitcher = accountSwitcher,
            switchGate = switchGate,
            serverSessionRevoker = ServerSessionRevoker(
                authApi = authApi,
                tokenManager = tokenManager,
                refreshCookieCredentials = credentials,
                roster = roster,
                serverHeadersProvider = headersProvider,
            ),
        )
    }

    private fun HttpRequestData.cookie() = headers.getAll(HttpHeaders.Cookie)
    private fun HttpRequestData.bearer() = headers[HttpHeaders.Authorization]

    @Test
    fun `logout revokes the captured account's session even after a switch lands mid-logout`() = runTest {
        coEvery { switchGate.captureSnapshot(any()) } answers {
            activeState.value = AccountState.Resolved(AccountId(ACCOUNT_B))
            RequestIdentity(baseUrl = SERVER_A, accountId = ACCOUNT_A, bearer = "bearer-a")
        }

        repo().logout()

        val request = engine.requestHistory.single()
        assertThat(request.url.toString()).isEqualTo("$SERVER_A/api/auth/logout")
        assertThat(request.bearer()).isEqualTo("Bearer bearer-a")
        assertThat(request.cookie()).containsExactly("refreshToken=rt-a")
        coVerify(exactly = 1) { accountSwitcher.remove(ACCOUNT_A) }
        coVerify(exactly = 0) { sessionManager.endCurrentSession() }
    }

    @Test
    fun `logout sends nothing when the account holds no refresh token`() = runTest {
        refreshTokens.remove(ACCOUNT_A)

        val result = repo().logout()

        assertThat(engine.requestHistory).isEmpty()
        assertThat(result).isInstanceOf(Result.Success::class.java)
        coVerify(exactly = 0) { tokenManager.ensureFreshAccessToken(any(), any(), any()) }
        coVerify(exactly = 1) { accountSwitcher.remove(ACCOUNT_A) }
        coVerify(exactly = 1) { sessionManager.endCurrentSession() }
    }

    @Test
    fun `logout renews a stale bearer and sends the rotated refresh token`() = runTest {
        coEvery { tokenManager.ensureFreshAccessToken(ACCOUNT_A, SERVER_A, "bearer-a") } answers {
            refreshTokens[ACCOUNT_A] = "rt-a-rotated"
            "bearer-a-renewed"
        }

        repo().logout()

        val request = engine.requestHistory.single()
        assertThat(request.bearer()).isEqualTo("Bearer bearer-a-renewed")
        assertThat(request.cookie()).containsExactly("refreshToken=rt-a-rotated")
    }

    @Test
    fun `a rejected logout still tears the account down and reports the error`() = runTest {
        status = HttpStatusCode.Unauthorized

        val result = repo().logout()

        assertThat(result).isInstanceOf(Result.Error::class.java)
        coVerify(exactly = 1) { accountSwitcher.remove(ACCOUNT_A) }
    }

    @Test
    fun `removing an inactive account revokes it on its own server`() = runTest {
        repo().removeAccount(ACCOUNT_B)

        val request = engine.requestHistory.single()
        assertThat(request.url.toString()).isEqualTo("$SERVER_B/api/auth/logout")
        assertThat(request.bearer()).isEqualTo("Bearer bearer-b")
        assertThat(request.cookie()).containsExactly("refreshToken=rt-b")
        assertThat(request.headers["X-Gateway"]).isEqualTo("gw-b")
        coVerify(exactly = 1) { accountSwitcher.remove(ACCOUNT_B) }
        coVerify(exactly = 0) { sessionManager.endCurrentSession() }
    }

    @Test
    fun `removing an account a switch just made inactive never signs out the switched-to one`() = runTest {
        // The live provider still reads A, but the snapshot the removal acts on already shows B.
        coEvery { switchGate.captureSnapshot(any()) } returns
            RequestIdentity(baseUrl = SERVER_B, accountId = ACCOUNT_B, bearer = "bearer-b")
        coEvery { tokenManager.getAccessTokenFor(ACCOUNT_A) } returns "bearer-a"

        repo().removeAccount(ACCOUNT_A)

        val request = engine.requestHistory.single()
        assertThat(request.url.toString()).isEqualTo("$SERVER_A/api/auth/logout")
        assertThat(request.cookie()).containsExactly("refreshToken=rt-a")
        coVerify(exactly = 1) { accountSwitcher.remove(ACCOUNT_A) }
        coVerify(exactly = 0) { accountSwitcher.remove(ACCOUNT_B) }
        coVerify(exactly = 0) { sessionManager.endCurrentSession() }
    }

    @Test
    fun `removing an inactive account still removes it when revocation fails`() = runTest {
        status = HttpStatusCode.InternalServerError

        repo().removeAccount(ACCOUNT_B)

        assertThat(engine.requestHistory).hasSize(1)
        coVerify(exactly = 1) { accountSwitcher.remove(ACCOUNT_B) }
    }

    @Test
    fun `removing the active account signs it out like logout`() = runTest {
        repo().removeAccount(ACCOUNT_A)

        val request = engine.requestHistory.single()
        assertThat(request.url.toString()).isEqualTo("$SERVER_A/api/auth/logout")
        assertThat(request.cookie()).containsExactly("refreshToken=rt-a")
        coVerify(exactly = 1) { accountSwitcher.remove(ACCOUNT_A) }
        coVerify(exactly = 1) { sessionManager.endCurrentSession() }
    }

    private companion object {
        const val ACCOUNT_A = "acct-a"
        const val ACCOUNT_B = "acct-b"
        const val SERVER_A = "https://a.example.com"
        const val SERVER_B = "https://b.example.com"
    }
}
