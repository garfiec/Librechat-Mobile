package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.model.auth.RequestEmailChangeRequest
import com.garfiec.librechat.core.network.di.librechatJson
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.accept
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Wire shapes of the v0.8.9-rc1 passkey-management and email-change routes. None is `arg`-wrapped. */
class AccountManagementApiTest {

    private val requests = mutableListOf<HttpRequestData>()

    private fun client(body: String): HttpClient = HttpClient(
        MockEngine { request ->
            requests += request
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        },
    ) {
        install(ContentNegotiation) { json(librechatJson) }
        defaultRequest {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
        }
    }

    private fun HttpRequestData.bodyText(): String =
        (body as OutgoingContent.ByteArrayContent).bytes().decodeToString()

    @Test
    fun `list unwraps the passkeys envelope`() = runTest {
        val api = PasskeyApi(
            client(
                """{"passkeys":[{"id":"pk1","name":"Pixel 9 Pro","deviceType":"multiDevice","backedUp":true,
                   "transports":["internal","hybrid"],"createdAt":"2026-09-14T10:00:00.000Z","lastUsedAt":null}]}""",
            ),
        )
        val list = api.listPasskeys()
        assertThat(requests.single().method).isEqualTo(HttpMethod.Get)
        assertThat(requests.single().url.encodedPath).isEqualTo("/api/auth/passkey")
        assertThat(list.single().name).isEqualTo("Pixel 9 Pro")
        assertThat(list.single().backedUp).isTrue()
        assertThat(list.single().lastUsedAt).isNull()
    }

    @Test
    fun `rename patches the id with a bare name body`() = runTest {
        val api = PasskeyApi(client("""{"passkey":{"id":"pk/1","name":"Work key"}}"""))
        val renamed = api.renamePasskey("pk/1", "Work key")
        val request = requests.single()
        assertThat(request.method).isEqualTo(HttpMethod.Patch)
        assertThat(request.url.encodedPath).isEqualTo("/api/auth/passkey/pk%2F1")
        assertThat(request.bodyText()).isEqualTo("""{"name":"Work key"}""")
        assertThat(renamed.name).isEqualTo("Work key")
    }

    @Test
    fun `delete sends the password in a json body, and nothing when there is none`() = runTest {
        val api = PasskeyApi(client("""{"message":"Passkey deleted"}"""))
        api.deletePasskey("pk1", "hunter2")
        api.deletePasskey("pk2", null)
        assertThat(requests[0].method).isEqualTo(HttpMethod.Delete)
        assertThat(requests[0].url.encodedPath).isEqualTo("/api/auth/passkey/pk1")
        assertThat(requests[0].bodyText()).isEqualTo("""{"password":"hunter2"}""")
        assertThat(requests[1].bodyText()).isEqualTo("{}")
    }

    @Test
    fun `email change posts a plain body`() = runTest {
        val api = UserApi(client("""{"message":"sent"}"""))
        val response = api.requestEmailChange(RequestEmailChangeRequest(currentPassword = "pw", newEmail = "a@b.dev"))
        val request = requests.single()
        assertThat(request.method).isEqualTo(HttpMethod.Post)
        assertThat(request.url.encodedPath).isEqualTo("/api/user/email/change")
        assertThat(request.bodyText()).isEqualTo("""{"currentPassword":"pw","newEmail":"a@b.dev"}""")
        assertThat(response.code).isNull()
    }
}
