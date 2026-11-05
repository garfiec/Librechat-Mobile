package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.common.identity.AccountState
import com.garfiec.librechat.core.common.identity.ActiveAccountProvider
import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.network.api.PullRequestsApi
import com.garfiec.librechat.core.network.di.librechatJson
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PullRequestConnectionRepositoryTest {

    private val accountState = MutableStateFlow<AccountState>(AccountState.Resolved(AccountId("a")))
    private val accounts = mockk<ActiveAccountProvider> { every { state } returns accountState }
    private val config = mockk<ConfigRepository> { every { serverUrl } returns flowOf("https://chat.example.com") }
    private val requests = mutableListOf<HttpRequestData>()

    private class CountingPullRequests : PullRequestRepository {
        var invalidations = 0
        override val entries = MutableStateFlow<Map<String, PullRequestEntry>>(emptyMap())
        override val revision = MutableStateFlow(0L)
        override suspend fun refresh(conversationId: String) = Unit
        override suspend fun refreshBatch(conversationIds: List<String>, maxConcurrent: Int?) = Unit
        override fun invalidate() {
            invalidations++
        }
    }

    private val pullRequests = CountingPullRequests()

    private fun repo(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): PullRequestConnectionRepository {
        val client = HttpClient(
            MockEngine { request ->
                requests += request
                handler(request)
            },
        ) {
            install(ContentNegotiation) { json(librechatJson) }
            HttpResponseValidator {
                validateResponse { response ->
                    if (!response.status.isSuccess()) throw ApiException(response.status.value, response.bodyAsText().ifBlank { "error" })
                }
            }
            defaultRequest {
                url("https://chat.example.com")
                contentType(ContentType.Application.Json)
            }
        }
        return PullRequestConnectionRepository(PullRequestsApi(client), pullRequests, config, accounts)
    }

    private fun MockRequestHandleScope.json(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
        vararg extra: Pair<String, List<String>>,
    ) = respond(body, status, headersOf(HttpHeaders.ContentType to listOf("application/json"), *extra))

    @Test
    fun connectSeedsTheCookiesTheSameResponseSet() = runTest {
        val repository = repo {
            json(
                """{"url":"https://github.com/login/oauth/authorize?redirect_uri=x","expiresInSeconds":600,"flowId":"f1"}""",
                extra = arrayOf(
                    HttpHeaders.SetCookie to listOf(
                        "oauth_csrf=abc; Path=/api/pull-requests/oauth/callback; HttpOnly",
                        "oauth_session=def; Path=/api; HttpOnly",
                        "unrelated=zzz; Path=/",
                    ),
                ),
            )
        }

        val launch = (repository.connect("GitHub") as Result.Success).data
        assertThat(requests.single().url.encodedPath).isEqualTo("/api/pull-requests/oauth/connect")
        assertThat(requests.single().method).isEqualTo(HttpMethod.Get)
        assertThat(launch.flowId).isEqualTo("f1")
        assertThat(launch.launch.serverName).isEqualTo("GitHub")
        assertThat(launch.launch.serverUrl).isEqualTo("https://chat.example.com")
        assertThat(launch.launch.seedCookies.map { it.substringBefore('=') }).containsExactly("oauth_csrf", "oauth_session")
    }

    @Test
    fun aConnectWithoutBindingCookiesFailsRatherThanOpeningAPageBoundToFail() = runTest {
        val repository = repo { json("""{"url":"https://github.com/login/oauth/authorize"}""") }
        val result = repository.connect("GitHub")
        assertThat(result).isInstanceOf(Result.Error::class.java)
        assertThat(PullRequestConnectionRepository.failureOf(result as Result.Error))
            .isEqualTo(PullRequestConnectFailure.FAILED)
    }

    @Test
    fun aNotConfiguredConnectIsToldApart() = runTest {
        val repository = repo { json("""{"code":"NOT_CONFIGURED"}""", HttpStatusCode.NotFound) }
        val result = repository.connect("GitHub") as Result.Error
        assertThat(PullRequestConnectionRepository.failureOf(result)).isEqualTo(PullRequestConnectFailure.NOT_CONFIGURED)
    }

    @Test
    fun aChangeOfCredentialAsksThePullRequestsAgainOnce() = runTest {
        var body = """{"configured":true,"provider":"github","connected":false}"""
        val repository = repo { json(body) }

        repository.status()
        assertThat(pullRequests.invalidations).isEqualTo(1)
        repository.status()
        assertThat(pullRequests.invalidations).isEqualTo(1)

        body = """{"configured":true,"provider":"github","connected":true,"flowId":"f1"}"""
        repository.status()
        assertThat(pullRequests.invalidations).isEqualTo(2)
    }

    @Test
    fun aServerTokenForAnotherProviderIsAChangeOfCredential() = runTest {
        var body = """{"configured":true,"provider":"github","connected":false}"""
        val repository = repo { json(body) }
        repository.status()
        assertThat(pullRequests.invalidations).isEqualTo(1)

        body = """{"configured":true,"provider":"gitlab","connected":false}"""
        repository.status()
        assertThat(pullRequests.invalidations).isEqualTo(2)
    }

    @Test
    fun aStatusAfterADisconnectIsTheCredentialTheDisconnectLeft() = runTest {
        var connected = true
        val repository = repo { request ->
            if (request.method == HttpMethod.Delete) {
                connected = false
                respond("", HttpStatusCode.NoContent)
            } else {
                json("""{"configured":true,"provider":"github","connected":$connected,"flowId":"f1"}""")
            }
        }
        repository.status()
        repository.disconnect()
        assertThat(pullRequests.invalidations).isEqualTo(2)
        repository.status()
        assertThat(pullRequests.invalidations).isEqualTo(2)
    }

    @Test
    fun aDisconnectAsksAgainUnderTheServerToken() = runTest {
        val repository = repo { request ->
            if (request.method == HttpMethod.Delete) {
                respond("", HttpStatusCode.NoContent)
            } else {
                json("""{"configured":true,"provider":"github","connected":true,"flowId":"f1"}""")
            }
        }
        repository.status()
        assertThat(pullRequests.invalidations).isEqualTo(1)

        assertThat(repository.disconnect()).isInstanceOf(Result.Success::class.java)
        assertThat(requests.last().url.encodedPath).isEqualTo("/api/pull-requests/oauth")
        assertThat(pullRequests.invalidations).isEqualTo(2)
    }

    @Test
    fun aStatusThatLandsAfterAnAccountSwitchIsNotTakenForTheNewAccount() = runTest {
        val repository = repo {
            accountState.value = AccountState.Resolved(AccountId("b"))
            json("""{"configured":true,"provider":"github","connected":true}""")
        }
        repository.status()
        assertThat(pullRequests.invalidations).isEqualTo(0)
    }
}
