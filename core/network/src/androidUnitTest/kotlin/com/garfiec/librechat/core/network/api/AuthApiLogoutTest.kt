package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.network.di.librechatJson
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AuthApiLogoutTest {

    @Test
    fun `logout sends the refresh token as the only cookie and no body`() = runTest {
        val engine = MockEngine { respond("""{"message":"Logout successful"}""", HttpStatusCode.OK) }
        val api = AuthApi(
            HttpClient(engine) {
                install(ContentNegotiation) { json(librechatJson) }
                defaultRequest { url("https://chat.example.com") }
            },
        )

        api.logout(refreshToken = "rt-1")

        val request = engine.requestHistory.single()
        assertThat(request.method).isEqualTo(HttpMethod.Post)
        assertThat(request.url.encodedPath).isEqualTo("/api/auth/logout")
        assertThat(request.headers.getAll(HttpHeaders.Cookie)).containsExactly("refreshToken=rt-1")
        assertThat(request.body.toByteArray()).isEmpty()
    }
}
