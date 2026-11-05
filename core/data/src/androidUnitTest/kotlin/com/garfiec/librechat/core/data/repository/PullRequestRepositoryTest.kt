package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.common.identity.AccountState
import com.garfiec.librechat.core.common.identity.ActiveAccountProvider
import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.network.api.PullRequestsApi
import com.garfiec.librechat.core.network.di.librechatJson
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.HttpTimeout
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
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.Collections
import kotlin.time.Instant

class PullRequestRepositoryTest {

    private val accountState = MutableStateFlow<AccountState>(AccountState.Resolved(AccountId("a")))
    private val accounts = mockk<ActiveAccountProvider> { every { state } returns accountState }

    // The fallback's single lookups run in parallel on the IO dispatcher, so the engine appends concurrently.
    private val requests: MutableList<HttpRequestData> = Collections.synchronizedList(mutableListOf())

    private fun pr(number: Int, checks: String = "running") =
        """{"number":$number,"title":"t","url":"https://github.com/o/r/pull/$number","additions":1,"deletions":2,
           "state":"open","isDraft":false,"mergeable":"clean","checks":"$checks"}"""

    private fun repo(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): PullRequestRepositoryImpl {
        val client = HttpClient(
            MockEngine { request ->
                requests += request
                handler(request)
            },
        ) {
            install(ContentNegotiation) { json(librechatJson) }
            install(HttpTimeout)
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
        return PullRequestRepositoryImpl(PullRequestsApi(client), accounts)
    }

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    @Test
    fun aSuccessThenAFailureKeepsTheValueAndFlagsTheRefresh() = runTest {
        var fail = false
        val repository = repo {
            if (fail) {
                json("""{"code":"RATE_LIMITED"}""", HttpStatusCode.ServiceUnavailable)
            } else {
                json("""{"pullRequest":${pr(7)}}""")
            }
        }

        repository.refresh("c1")
        assertThat(repository.entries.first()["c1"]?.pullRequest?.number).isEqualTo(7)

        fail = true
        repository.refresh("c1")
        val entry = repository.entries.first()["c1"]!!
        assertThat(entry.pullRequest?.number).isEqualTo(7)
        assertThat(entry.refreshFailed).isTrue()
    }

    @Test
    fun aFirstFailureRecordsNothingToShow() = runTest {
        val repository = repo { json("""{"error":"x"}""", HttpStatusCode.InternalServerError) }
        repository.refresh("c1")
        val entry = repository.entries.first()["c1"]!!
        assertThat(entry.pullRequest).isNull()
        assertThat(entry.answered).isFalse()
        assertThat(entry.refreshFailed).isFalse()
    }

    @Test
    fun theBatchBodyIsBareAndEntriesFailAlone() = runTest {
        val repository = repo {
            json(
                """{"results":[{"conversationId":"a","pullRequest":${pr(1)}},{"conversationId":"b","pullRequest":null},
                   {"conversationId":"c","error":{"code":"UPSTREAM_ERROR"}}]}""",
            )
        }
        repository.refreshBatch(listOf("a", "b", "c"), maxConcurrent = 4)

        val body = (requests.single().body as TextContent).text
        assertThat(body).isEqualTo("""{"conversationIds":["a","b","c"]}""")
        assertThat(requests.single().method).isEqualTo(HttpMethod.Post)
        val entries = repository.entries.first()
        assertThat(entries["a"]?.pullRequest?.number).isEqualTo(1)
        assertThat(entries["b"]?.answered).isTrue()
        assertThat(entries["b"]?.pullRequest).isNull()
        assertThat(entries["c"]?.answered).isFalse()
    }

    @Test
    fun theBatchIsChunkedAtFifty() = runTest {
        val repository = repo { json("""{"results":[]}""") }
        repository.refreshBatch((1..120).map { "c$it" }, maxConcurrent = 2)
        assertThat(requests).hasSize(3)
    }

    @Test
    fun aMissingBatchRouteFallsBackToSingleLookups() = runTest {
        val repository = repo { request ->
            if (request.method == HttpMethod.Post) {
                json("""{"error":"not found"}""", HttpStatusCode.NotFound)
            } else {
                json("""{"pullRequest":${pr(3)}}""")
            }
        }
        repository.refreshBatch(listOf("a", "b"), maxConcurrent = null)

        assertThat(requests.count { it.method == HttpMethod.Get }).isEqualTo(2)
        assertThat(repository.entries.first()["b"]?.pullRequest?.number).isEqualTo(3)
    }

    @Test
    fun anotherAccountSeesNone() = runTest {
        val repository = repo { json("""{"pullRequest":${pr(7)}}""") }
        repository.refresh("c1")
        accountState.value = AccountState.Resolved(AccountId("b"))
        assertThat(repository.entries.first()).isEmpty()
    }

    @Test
    fun anAnswerThatLandsAfterAnAccountSwitchIsDropped() = runTest {
        val repository = repo {
            accountState.value = AccountState.Resolved(AccountId("b"))
            json("""{"pullRequest":${pr(7)}}""")
        }
        repository.refresh("c1")
        assertThat(repository.entries.first()).isEmpty()
        accountState.value = AccountState.Resolved(AccountId("a"))
        assertThat(repository.entries.first()).isEmpty()
    }

    @Test
    fun anAnswerThatLandsAfterACredentialChangeIsDropped() = runTest {
        lateinit var repository: PullRequestRepositoryImpl
        var invalidateWhileOut = false
        repository = repo { request ->
            if (invalidateWhileOut) repository.invalidate()
            if (request.method == HttpMethod.Post) {
                json("""{"results":[{"conversationId":"c1","pullRequest":${pr(7)}}]}""")
            } else {
                json("""{"pullRequest":${pr(7)}}""")
            }
        }
        invalidateWhileOut = true
        repository.refresh("c1")
        repository.refreshBatch(listOf("c1"), maxConcurrent = null)
        assertThat(repository.entries.first()).isEmpty()

        invalidateWhileOut = false
        repository.refresh("c1")
        assertThat(repository.entries.first()["c1"]?.pullRequest?.number).isEqualTo(7)
    }

    @Test
    fun invalidateKeepsTheValuesButMarksThemStaleAndBumpsTheRevision() = runTest {
        val repository = repo { json("""{"pullRequest":${pr(7)}}""") }
        repository.refresh("c1")
        val before = repository.revision.value

        repository.invalidate()

        val entry = repository.entries.first()["c1"]!!
        assertThat(entry.pullRequest?.number).isEqualTo(7)
        assertThat(entry.fetchedAt).isEqualTo(Instant.DISTANT_PAST)
        assertThat(repository.revision.value).isEqualTo(before + 1)
    }
}
