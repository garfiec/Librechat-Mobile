package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.network.di.librechatJson
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.headers
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** The binding cookies are the whole point of the call: they must reach the WebView intact. */
class McpApiBindOAuthTest {

    private var requestPath: String? = null
    private var requestMethod: HttpMethod? = null

    private fun api(vararg setCookies: String) = McpApi(
        HttpClient(
            MockEngine { request ->
                requestPath = request.url.encodedPath
                requestMethod = request.method
                respond(
                    """{"success":true}""",
                    headers = headers {
                        setCookies.forEach { append(HttpHeaders.SetCookie, it) }
                    },
                )
            },
        ),
        librechatJson,
    )

    @Test
    fun `both binding cookies come back with their attributes`() = runTest {
        val cookies = api(
            "oauth_session=abc123; Max-Age=86400; Path=/api; Expires=Thu, 02 Jan 2027 00:00:00 GMT; HttpOnly; Secure; SameSite=Lax",
            "oauth_csrf=def456; Max-Age=600; Path=/api/mcp; Expires=Thu, 01 Jan 2027 00:10:00 GMT; HttpOnly; Secure; SameSite=Lax",
            "unrelated=1; Path=/",
        ).bindOAuth("my server")

        assertThat(requestMethod).isEqualTo(HttpMethod.Post)
        assertThat(requestPath).isEqualTo("/api/mcp/my%20server/oauth/bind")
        assertThat(cookies).hasSize(2)
        val session = cookies.single { it.startsWith("oauth_session=abc123") }
        val csrf = cookies.single { it.startsWith("oauth_csrf=def456") }
        assertThat(session).contains("Path=/api")
        assertThat(session).contains("Max-Age=86400")
        assertThat(session).contains("Secure")
        assertThat(session).contains("HttpOnly")
        assertThat(session).contains("SameSite=Lax")
        assertThat(csrf).contains("Path=/api/mcp")
        assertThat(csrf).contains("Max-Age=600")
    }

    @Test
    fun `no cookies is an empty list, not a failure`() = runTest {
        assertThat(api().bindOAuth("github")).isEmpty()
    }
}
