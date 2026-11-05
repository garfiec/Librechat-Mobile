package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.model.code.CodeEnvironmentMode
import com.garfiec.librechat.core.model.code.CodeWorkspaceSelection
import com.garfiec.librechat.core.model.code.CodeWorkspaceUnavailableReason
import com.garfiec.librechat.core.network.api.CodeEnvironmentsApi
import com.garfiec.librechat.core.network.api.ConversationsApi
import com.garfiec.librechat.core.network.di.librechatJson
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestData
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
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CodeEnvironmentRepositoryTest {
    private val requests = mutableListOf<HttpRequestData>()

    private fun repo(status: HttpStatusCode, body: String): CodeEnvironmentRepositoryImpl {
        val client = HttpClient(
            MockEngine { request ->
                requests += request
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        ) {
            install(ContentNegotiation) { json(librechatJson) }
            HttpResponseValidator {
                validateResponse { response ->
                    if (!response.status.isSuccess()) {
                        val text = response.bodyAsText()
                        throw ApiException(response.status.value, "error", body = text.ifBlank { null })
                    }
                }
            }
            defaultRequest {
                url("https://chat.example.com")
                contentType(ContentType.Application.Json)
            }
        }
        return CodeEnvironmentRepositoryImpl(CodeEnvironmentsApi(client), ConversationsApi(client))
    }

    private val from = listOf(CodeWorkspaceSelection("garfie-mbp", "switchboard"))

    @Test
    fun aMoveSendsFromAndToUnwrappedAndReturnsTheServerDecision() = runTest {
        val repository = repo(HttpStatusCode.OK, """{"conversationId":"c1","codeEnvironmentMode":"without_attached"}""")
        val result = repository.moveDecision("c1", from, emptyList())
        assertThat(result).isEqualTo(CodeDecisionMove.Moved(SavedCodeDecision(CodeEnvironmentMode.WITHOUT_ATTACHED, null)))
        val request = requests.single()
        assertThat(request.method).isEqualTo(HttpMethod.Patch)
        assertThat(request.url.encodedPath).isEqualTo("/api/code-environments/conversations/c1/decision")
        val sent = (request.body as TextContent).text
        assertThat(sent).doesNotContain("\"arg\"")
        assertThat(sent).contains("\"from\":[{\"environmentId\":\"garfie-mbp\",\"workspaceId\":\"switchboard\"}]")
        assertThat(sent).contains("\"to\":[]")
    }

    @Test
    fun aStaleDecisionIsRefusedAsLocked() = runTest {
        val repository = repo(
            HttpStatusCode.Conflict,
            """{"error":"stale","code":"code_workspace_unavailable","reason":"locked"}""",
        )
        assertThat(repository.moveDecision("c1", from, emptyList()))
            .isEqualTo(CodeDecisionMove.Refused(CodeWorkspaceUnavailableReason.LOCKED))
    }

    @Test
    fun aConflictWithoutACodeMeansARunIsGoing() = runTest {
        val repository = repo(HttpStatusCode.Conflict, """{"error":"Wait for the current response to finish"}""")
        assertThat(repository.moveDecision("c1", from, emptyList())).isEqualTo(CodeDecisionMove.Busy)
    }

    @Test
    fun forbiddenNotFoundAndServerErrorsMapToTheirOutcomes() = runTest {
        assertThat(repo(HttpStatusCode.Forbidden, """{"error":"disabled"}""").moveDecision("c1", from, emptyList()))
            .isEqualTo(CodeDecisionMove.Disabled)
        assertThat(repo(HttpStatusCode.NotFound, """{"error":"gone"}""").moveDecision("c1", from, emptyList()))
            .isEqualTo(CodeDecisionMove.NotFound)
        assertThat(repo(HttpStatusCode.ServiceUnavailable, """{"error":"policy"}""").moveDecision("c1", from, emptyList()))
            .isEqualTo(CodeDecisionMove.Failed(unknownOutcome = true))
    }
}
