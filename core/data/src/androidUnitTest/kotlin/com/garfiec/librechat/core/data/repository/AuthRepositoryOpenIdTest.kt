package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.common.identity.InMemoryActiveAccountProvider
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.data.datastore.ACTIVE_ACCOUNT_KEY
import com.garfiec.librechat.core.data.datastore.FakeTokenStore
import com.garfiec.librechat.core.data.datastore.accessKeyOf
import com.garfiec.librechat.core.data.datastore.refreshKeyOf
import com.garfiec.librechat.core.data.datastore.unconfinedMockEngine
import com.garfiec.librechat.core.model.auth.AuthCookies
import com.garfiec.librechat.core.model.auth.OpenIdMarkers
import com.garfiec.librechat.core.network.api.AuthApi
import com.garfiec.librechat.core.network.api.UserApi
import com.garfiec.librechat.core.network.client.AuthInterceptorPlugin
import com.garfiec.librechat.core.network.client.OpenIdCookiePlugin
import com.garfiec.librechat.core.network.client.PendingRequestIdentity
import com.garfiec.librechat.core.network.client.ServerUrlProvider
import com.garfiec.librechat.core.network.client.SwitchBarrierPlugin
import com.garfiec.librechat.core.network.client.SwitchGate
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AuthRepositoryOpenIdTest {
    @Test
    fun `first sign-in exchanges the capture before fetching the staged profile`() = runTest {
        verifyFlow(addAccount = false)
    }

    @Test
    fun `adding a reuse account leaves the active server credentials untouched`() = runTest {
        verifyFlow(addAccount = true)
    }

    private suspend fun verifyFlow(addAccount: Boolean) {
        val provider = InMemoryActiveAccountProvider().apply { if (addAccount) set(AccountId("A")) else clear() }
        val store = FakeTokenStore(lazy { error("Interactive sign-in must use AuthApi") }, if (addAccount) {
            mapOf(ACTIVE_ACCOUNT_KEY to "A", accessKeyOf("A") to "a-access", refreshKeyOf("A") to "a-refresh")
        } else {
            emptyMap()
        })
        val server = object : ServerUrlProvider { override fun getBaseUrl(): String = "https://a.example.com" }
        val gate = SwitchGate(provider, server, store, null)
        val target = if (addAccount) "b.example.com" else "a.example.com"
        val engine = unconfinedMockEngine { request ->
            assertThat(request.url.host).isEqualTo(target)
            when (request.url.encodedPath) {
                "/api/auth/refresh" -> {
                    assertThat(request.headers.getAll(HttpHeaders.Cookie)).containsExactly(
                        "refreshToken=captured-refresh; token_provider=openid; openid_user_id=captured-marker",
                    )
                    respond("""{"token":"b-access"}""", headers = headersOf(
                        HttpHeaders.ContentType to listOf("application/json"),
                        HttpHeaders.SetCookie to listOf("refreshToken=rotated-refresh", "openid_user_id=rotated-marker"),
                    ))
                }
                "/api/user" -> {
                    assertThat(request.headers[HttpHeaders.Authorization]).isEqualTo("Bearer b-access")
                    assertThat(request.headers.getAll(HttpHeaders.Cookie)).containsExactly(
                        "token_provider=openid; openid_user_id=rotated-marker",
                    )
                    respond("""{"_id":"b-user","email":"b@example.com"}""", headers = headersOf(
                        HttpHeaders.ContentType, "application/json",
                    ))
                }
                else -> error("Unexpected route")
            }
        }
        val switcher = mockk<AccountSwitcher>(relaxed = true)
        every { switcher.pendingAdd } returns if (addAccount) {
            PendingAddSession(
                "https://b.example.com", PendingRequestIdentity(
                    "https://b.example.com", auth = { store.getStagedRequestAuth() },
                ),
            )
        } else {
            null
        }
        val establisher = mockk<AccountSessionEstablisher>(relaxed = true)
        coEvery { establisher.establish(any()) } coAnswers { store.onAccountResolved("B"); provider.set(AccountId("B")); AccountId("B") }
        coEvery { switcher.completeAdd(any()) } coAnswers { store.onAccountResolved("B"); provider.set(AccountId("B")); AccountId("B") }
        HttpClient(engine) {
            install(ContentNegotiation) { json() }
            install(AuthInterceptorPlugin) { tokenManager = store; serverUrlProvider = server }
            install(OpenIdCookiePlugin) { serverUrlProvider = server }
            install(SwitchBarrierPlugin) { switchGate = gate }
            defaultRequest { contentType(ContentType.Application.Json) }
        }.use { client ->
            val repository = AuthRepositoryImpl(
                AuthApi(client), UserApi(client), store, mockk(relaxed = true), mockk(relaxed = true),
                establisher, mockk(relaxed = true), provider, mockk(relaxed = true), switcher, gate, mockk(relaxed = true),
            )
            val result = repository.loginWithOAuthToken(AuthCookies("captured-refresh", OpenIdMarkers("captured-marker")))
            assertThat(result).isInstanceOf(Result.Success::class.java)
            assertThat(store.authCookiesFor("B")).isEqualTo(AuthCookies("rotated-refresh", OpenIdMarkers("rotated-marker")))
            if (addAccount) {
                assertThat(store.getAccessTokenFor("A")).isEqualTo("a-access")
                assertThat(store.authCookiesFor("A")).isEqualTo(AuthCookies("a-refresh"))
            }
        }
    }
}
