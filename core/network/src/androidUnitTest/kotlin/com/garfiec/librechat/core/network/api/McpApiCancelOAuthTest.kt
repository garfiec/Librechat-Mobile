package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.network.di.librechatJson
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Upstream's route is `POST /api/mcp/oauth/cancel/:serverName`, with the server name last. */
class McpApiCancelOAuthTest {

    @Test
    fun `cancel posts to the server's own path segment`() = runTest {
        var requestPath: String? = null
        var requestMethod: HttpMethod? = null
        val api = McpApi(
            HttpClient(
                MockEngine { request ->
                    requestPath = request.url.encodedPath
                    requestMethod = request.method
                    respond("""{"success":true,"message":"No active OAuth flow to cancel"}""")
                },
            ),
            librechatJson,
        )

        api.cancelOAuth("my server")

        assertThat(requestMethod).isEqualTo(HttpMethod.Post)
        assertThat(requestPath).isEqualTo("/api/mcp/oauth/cancel/my%20server")
    }
}
