package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.network.di.librechatJson
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Who may change a server rests on `dbId` and `consumeOnly`, so their decoding is pinned here. */
class McpApiListServersTest {

    private fun client(response: String): HttpClient = HttpClient(
        MockEngine {
            respond(response, headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        },
    ) { install(ContentNegotiation) { json(librechatJson) } }

    @Test
    fun `a stored server carries its record id and flags`() = runTest {
        val api = McpApi(
            client(
                """{"docs":{"type":"streamable-http","url":"https://docs.example.test/mcp",""" +
                    """"dbId":"6650f1","consumeOnly":true,"requestScoped":true}}""",
            ),
            librechatJson,
        )

        val server = api.listServers().single()

        assertThat(server.dbId).isEqualTo("6650f1")
        assertThat(server.consumeOnly).isTrue()
        assertThat(server.requestScoped).isTrue()
    }

    @Test
    fun `a yaml server has no record id`() = runTest {
        val api = McpApi(client("""{"filesystem":{"type":"stdio","command":"npx"}}"""), librechatJson)

        val server = api.listServers().single()

        assertThat(server.dbId).isNull()
        assertThat(server.consumeOnly).isFalse()
        assertThat(server.requestScoped).isFalse()
    }

    @Test
    fun `effective permissions decode as bits per resource id`() = runTest {
        val api = PermissionsApi(client("""{"6650f1":15,"6650f2":1}"""))

        assertThat(api.getAllEffectivePermissions("mcpServer")).containsExactly("6650f1", 15, "6650f2", 1)
    }
}
