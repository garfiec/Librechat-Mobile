package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.model.mcp.McpUserVar
import com.garfiec.librechat.core.network.di.librechatJson
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/** Per-user MCP variables: declared on the server list, saved through web's plugin-auth route. */
class McpApiUserVarsTest {

    private val requests = mutableListOf<HttpRequestData>()

    private fun api(response: String = "") = McpApi(
        HttpClient(
            MockEngine { request ->
                requests += request
                respond(response, headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
            },
        ) {
            install(ContentNegotiation) { json(librechatJson) }
            // Mirrors production's defaultRequest, which is what makes the body serialize as JSON.
            defaultRequest { contentType(ContentType.Application.Json) }
        },
        librechatJson,
    )

    private fun HttpRequestData.json(): JsonObject = Json.parseToJsonElement((body as TextContent).text).jsonObject

    @Test
    fun `declared variables keep their order and are masked unless marked otherwise`() = runTest {
        val server = api(
            """{"gh":{"type":"streamable-http","url":"https://gh.example.test/mcp","customUserVars":{""" +
                """"TOKEN":{"title":"Token","description":"A <a href='https://x.test'>token</a>"},""" +
                """"USER":{"title":"Username","description":"","sensitive":false}}}}""",
        ).listServers().single()

        assertThat(server.customUserVars).containsExactly(
            McpUserVar("TOKEN", "Token", "A <a href='https://x.test'>token</a>", sensitive = true),
            McpUserVar("USER", "Username", "", sensitive = false),
        ).inOrder()
    }

    @Test
    fun `which variables are set comes from the auth-values route`() = runTest {
        val flags = api("""{"success":true,"serverName":"my gh","authValueFlags":{"TOKEN":true,"USER":false}}""")
            .getUserVarFlags("my gh")

        assertThat(flags).containsExactly("TOKEN", true, "USER", false)
        assertThat(requests.single().url.encodedPath).isEqualTo("/api/mcp/my%20gh/auth-values")
    }

    @Test
    fun `saving posts web's install body, not wrapped in arg`() = runTest {
        api().saveUserVars("gh", mapOf("TOKEN" to "secret"))

        val request = requests.single()
        assertThat(request.url.encodedPath).isEqualTo("/api/user/plugins")
        val body = request.json()
        assertThat(body.keys).containsExactly("pluginKey", "action", "auth")
        assertThat(body["pluginKey"]!!.jsonPrimitive.content).isEqualTo("mcp_gh")
        assertThat(body["action"]!!.jsonPrimitive.content).isEqualTo("install")
        assertThat(body["auth"]!!.jsonObject["TOKEN"]!!.jsonPrimitive.content).isEqualTo("secret")
    }

    @Test
    fun `removing uninstalls every variable of the server`() = runTest {
        api().removeUserVars("gh")

        val body = requests.single().json()
        assertThat(body["pluginKey"]!!.jsonPrimitive.content).isEqualTo("mcp_gh")
        assertThat(body["action"]!!.jsonPrimitive.content).isEqualTo("uninstall")
        assertThat(body["auth"]!!.jsonObject).isEmpty()
    }
}
