package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.model.auth.AuthCookies
import com.garfiec.librechat.core.model.auth.OpenIdMarkers
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AuthApiOpenIdTest {
    private fun client(engine: MockEngine): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) { json() }
        defaultRequest { url("https://chat.example.com"); contentType(ContentType.Application.Json) }
    }

    @Test
    fun `reuse capture sends three cookies and returns the rotated bundle`() = runTest {
        val cookies = AuthCookies("idp-refresh", OpenIdMarkers("signed-marker"))
        val engine = MockEngine { request ->
            assertThat(request.headers.getAll(HttpHeaders.Cookie)).containsExactly(
                "refreshToken=idp-refresh; token_provider=openid; openid_user_id=signed-marker",
            )
            respond("""{"token":"new-access"}""", HttpStatusCode.OK, headersOf(
                HttpHeaders.ContentType to listOf("application/json"),
                HttpHeaders.SetCookie to listOf("refreshToken=rotated; HttpOnly", "openid_user_id=new-marker; HttpOnly"),
            ))
        }
        client(engine).use { http ->
            val result = AuthApi(http).refresh(cookies)
            assertThat(result.cookies).isEqualTo(AuthCookies("rotated", OpenIdMarkers("new-marker")))
        }
    }

    @Test
    fun `normal refresh sends only the refresh token`() = runTest {
        val engine = MockEngine { request ->
            assertThat(request.headers.getAll(HttpHeaders.Cookie)).containsExactly("refreshToken=normal")
            respond("""{"token":"access"}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        client(engine).use { assertThat(AuthApi(it).refresh(AuthCookies("normal")).cookies).isEqualTo(AuthCookies("normal")) }
    }

    @Test
    fun `logout sends the complete session`() = runTest {
        val engine = MockEngine { request ->
            assertThat(request.url.encodedPath).isEqualTo("/api/auth/logout")
            assertThat(request.headers.getAll(HttpHeaders.Cookie)).containsExactly(
                "refreshToken=idp-refresh; token_provider=openid; openid_user_id=signed-marker",
            )
            respond("{}")
        }
        client(engine).use { AuthApi(it).logout(AuthCookies("idp-refresh", OpenIdMarkers("signed-marker"))) }
    }
}
