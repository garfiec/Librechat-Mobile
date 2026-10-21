package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.model.Principal
import com.garfiec.librechat.core.model.PrincipalType
import com.garfiec.librechat.core.model.UpdateResourcePermissionsRequest
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
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/** Pins the `/api/permissions` wire shapes the access editor depends on. */
class PermissionsApiTest {

    // The app's client settings that matter here: defaults and nulls are left off the wire.
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

    private fun jsonClient(engine: MockEngine): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) { json(json) }
        defaultRequest {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
        }
    }

    private fun respondJson(body: String) = MockEngine {
        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
    }

    @Test
    fun `searchPrincipals unwraps the results from the search envelope`() = runTest {
        val engine = respondJson(
            """
                {
                  "query": "ali",
                  "limit": 20,
                  "types": ["user", "group"],
                  "results": [
                    {"id":"u1","type":"user","name":"Alice","email":"alice@example.com","source":"local"},
                    {"id":"g1","type":"group","name":"Alignment","source":"local","memberCount":4}
                  ],
                  "count": 2,
                  "sources": {"local": 2, "entra": 0}
                }
            """.trimIndent(),
        )

        val results = PermissionsApi(jsonClient(engine)).searchPrincipals("ali")

        assertThat(results.map { it.id }).containsExactly("u1", "g1").inOrder()
        assertThat(results[1].type).isEqualTo(PrincipalType.GROUP)
        assertThat(results[1].memberCount).isEqualTo(4)
    }

    @Test
    fun `an update without public leaves the public field off the wire`() = runTest {
        var sent: JsonObject? = null
        val engine = MockEngine { request ->
            sent = request.jsonBody()
            respond("""{"message":"ok","results":{"principals":[]}}""", HttpStatusCode.OK, jsonHeaders)
        }

        PermissionsApi(jsonClient(engine)).updateResourcePermissions(
            "sharedLink",
            "link1",
            UpdateResourcePermissionsRequest(updated = listOf(Principal(type = PrincipalType.USER, id = "u1"))),
        )

        assertThat(sent!!.containsKey("public")).isFalse()
    }

    @Test
    fun `turning public off sends public false`() = runTest {
        var sent: JsonObject? = null
        val engine = MockEngine { request ->
            sent = request.jsonBody()
            respond("""{"message":"ok","results":{"principals":[],"public":false}}""", HttpStatusCode.OK, jsonHeaders)
        }

        PermissionsApi(jsonClient(engine)).updateResourcePermissions(
            "sharedLink",
            "link1",
            UpdateResourcePermissionsRequest(public = false),
        )

        assertThat(sent!!["public"]!!.jsonPrimitive.boolean).isFalse()
    }

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun HttpRequestData.jsonBody(): JsonObject {
        val text = (body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
        return json.parseToJsonElement(text).jsonObject
    }
}
