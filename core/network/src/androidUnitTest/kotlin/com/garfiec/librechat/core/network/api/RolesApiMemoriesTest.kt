package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.model.request.UpdateRoleMemoriesRequest
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
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Test

/**
 * The role MEMORIES write, serialized through a real client with the app's JSON configuration.
 * Upstream's handler parses the body with `memoryPermissionsSchema.partial()` and merges it onto
 * the role's MEMORIES map: the permission keys sit at the top level (no `arg` wrapper), and a key
 * left out keeps its stored value.
 */
class RolesApiMemoriesTest {

    private val requests = mutableListOf<HttpRequestData>()

    private val roleResponse =
        """{"name":"USER","permissions":{"MEMORIES":""" +
            """{"USE":true,"CREATE":false,"UPDATE":true,"READ":true,"OPT_OUT":false}}}"""

    private fun api(): RolesApi {
        val engine = MockEngine { request ->
            requests += request
            respond(
                content = roleResponse,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(librechatJson) }
            defaultRequest { contentType(ContentType.Application.Json) }
        }
        return RolesApi(client)
    }

    private fun sentBody(): JsonElement = Json.parseToJsonElement((requests.last().body as TextContent).text)

    @Test
    fun `all five flags go flat to the role's memories route`() = runTest {
        val role = api().updateRoleMemories(
            "USER",
            UpdateRoleMemoriesRequest(use = true, create = false, update = true, read = true, optOut = false),
        )

        val request = requests.single()
        assertThat(request.method).isEqualTo(HttpMethod.Put)
        assertThat(request.url.encodedPath).endsWith("/api/roles/USER/memories")
        assertThat(sentBody()).isEqualTo(
            Json.parseToJsonElement("""{"USE":true,"CREATE":false,"UPDATE":true,"READ":true,"OPT_OUT":false}"""),
        )
        assertThat(role.name).isEqualTo("USER")
    }

    @Test
    fun `a partial request sends only the flags it sets`() = runTest {
        api().updateRoleMemories("ADMIN", UpdateRoleMemoriesRequest(optOut = false))

        assertThat(requests.single().url.encodedPath).endsWith("/api/roles/ADMIN/memories")
        assertThat(sentBody()).isEqualTo(Json.parseToJsonElement("""{"OPT_OUT":false}"""))
    }
}
