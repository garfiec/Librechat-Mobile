package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.code.CodeEnvironmentPermissionSettings
import com.garfiec.librechat.core.model.code.CodeEnvironmentUserSettings
import com.garfiec.librechat.core.network.api.CodeEnvironmentsApi
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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CodeMachinesRepositoryTest {
    private val requests = mutableListOf<HttpRequestData>()
    private val config = mockk<ConfigRepository> { coEvery { fetchEndpoints() } returns Result.Success(emptyMap()) }

    private fun repo(status: HttpStatusCode, body: String): CodeMachinesRepositoryImpl {
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
                        throw ApiException(response.status.value, "error", body = response.bodyAsText().ifBlank { null })
                    }
                }
            }
            defaultRequest {
                url("https://chat.example.com")
                contentType(ContentType.Application.Json)
            }
        }
        return CodeMachinesRepositoryImpl(CodeEnvironmentsApi(client), config)
    }

    private val attemptId = "B".repeat(43)

    @Test
    fun pairingReturnsTheOneTimeCommandInputsAndRefreshesEndpoints() = runTest {
        val outcome = repo(
            HttpStatusCode.Created,
            """{"environment":{"id":"code-1","name":"VM","canDelete":true},
               "pairing":{"workerId":"code-1","code":"c","expiresAt":"2026-11-04T12:00:00.000Z","endpoint":"https://x"}}""",
        ).pair("VM", "plane")
        assertThat((outcome as CodePairingOutcome.Paired).response.pairing.workerId).isEqualTo("code-1")
        assertThat((requests.single().body as TextContent).text).isEqualTo("""{"name":"VM","controlPlaneId":"plane"}""")
        coVerify { config.fetchEndpoints() }
    }

    @Test
    fun pairingRefusalsAreNamed() = runTest {
        suspend fun error(status: HttpStatusCode, body: String) =
            (repo(status, body).pair("VM", "p") as CodePairingOutcome.Failed).error
        assertThat(error(HttpStatusCode.Forbidden, """{"error":"Personal code worker enrollment is disabled"}"""))
            .isEqualTo(CodePairingError.DISABLED)
        assertThat(error(HttpStatusCode.NotFound, """{"error":"Principal code control plane was not found"}"""))
            .isEqualTo(CodePairingError.CONTROL_PLANE_GONE)
        assertThat(error(HttpStatusCode.Conflict, """{"error":"Personal code environment limit reached"}"""))
            .isEqualTo(CodePairingError.LIMIT_REACHED)
        assertThat(error(HttpStatusCode.Conflict, """{"error":"Account deletion is already in progress"}"""))
            .isEqualTo(CodePairingError.REFUSED)
        assertThat(error(HttpStatusCode.ServiceUnavailable, """{"error":"Code environment pairing is not configured"}"""))
            .isEqualTo(CodePairingError.UNAVAILABLE)
    }

    @Test
    fun permissionsPatchTheSettingsKeyAndRemovalInUseIsNamed() = runTest {
        val saved = repo(HttpStatusCode.OK, """{"environment":{"id":"m"}}""")
            .updatePermissions("m", CodeEnvironmentUserSettings(CodeEnvironmentPermissionSettings(fileWrite = "allow")))
        assertThat(saved).isTrue()
        assertThat(requests.last().method).isEqualTo(HttpMethod.Patch)
        assertThat((requests.last().body as TextContent).text).isEqualTo("""{"settings":{"permissions":{"fileWrite":"allow"}}}""")
        assertThat(repo(HttpStatusCode.Conflict, """{"error":"in use"}""").remove("m")).isEqualTo(CodeRemovalError.IN_USE)
    }

    @Test
    fun denySendsAnEmptyObjectAndANotFoundIsUnavailable() = runTest {
        val body = """{"version":1,"attemptId":"$attemptId","status":"denied","controlPlanes":[]}"""
        val outcome = repo(HttpStatusCode.OK, body).denySetup(attemptId)
        assertThat((outcome as CodeSetupOutcome.Attempt).attempt.status).isEqualTo("denied")
        assertThat((requests.last().body as TextContent).text).isEqualTo("{}")
        assertThat(repo(HttpStatusCode.NotFound, """{"code":"SETUP_NOT_FOUND"}""").setupAttempt(attemptId))
            .isEqualTo(CodeSetupOutcome.Failed(CodeSetupError.UNAVAILABLE))
    }

    @Test
    fun anAnswerForAnotherAttemptIsRefused() = runTest {
        val body = """{"version":1,"attemptId":"${"C".repeat(43)}","status":"pending","controlPlanes":[]}"""
        assertThat(repo(HttpStatusCode.OK, body).setupAttempt(attemptId))
            .isEqualTo(CodeSetupOutcome.Failed(CodeSetupError.FAILED))
    }
}
