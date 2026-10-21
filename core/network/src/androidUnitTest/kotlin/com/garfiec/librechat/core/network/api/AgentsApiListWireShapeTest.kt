package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.network.di.librechatJson
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Pins the query string of `GET /api/agents` against upstream `getListAgentsHandler`, which reads
 * `limit`, `cursor`, `search`, `category`, `promoted` and `requiredPermission`, and nothing else.
 *
 * A parameter the route doesn't read is dropped without an error: sent as `pageNumber`/`pageSize`,
 * every "next page" comes back as the first page again.
 */
class AgentsApiListWireShapeTest {

    private lateinit var lastRequest: HttpRequestData

    private val response = """
        {"object":"list","data":[{"id":"agent_1","name":"One"}],"first_id":"agent_1","last_id":"agent_1",
        "has_more":true,"after":"eyJ1cGRhdGVkQXQiOiIyMDI2In0="}
    """.trimIndent().replace("\n", "")

    private fun api(): AgentsApi {
        val engine = MockEngine { request ->
            lastRequest = request
            respond(
                content = response,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        return AgentsApi(HttpClient(engine) { install(ContentNegotiation) { json(librechatJson) } })
    }

    private fun param(name: String): String? = lastRequest.url.parameters[name]

    @Test
    fun `a first page sends limit and no cursor or page number`() = runTest {
        api().getAgentsPaginated(limit = 20)

        assertThat(lastRequest.url.encodedPath).endsWith("api/agents")
        assertThat(param("limit")).isEqualTo("20")
        assertThat(lastRequest.url.parameters.names()).containsExactly("limit")
    }

    @Test
    fun `a next page sends the previous page's after as cursor`() = runTest {
        api().getAgentsPaginated(limit = 20, cursor = "eyJ1cGRhdGVkQXQiOiIyMDI2In0=")

        assertThat(param("cursor")).isEqualTo("eyJ1cGRhdGVkQXQiOiIyMDI2In0=")
        assertThat(param("pageNumber")).isNull()
        assertThat(param("pageSize")).isNull()
    }

    /** The route matches the string `'1'` exactly; `true` would read as no filter at all. */
    @Test
    fun `promoted is sent as 1`() = runTest {
        api().getAgentsPaginated(limit = 20, promoted = true)

        assertThat(param("promoted")).isEqualTo("1")
        assertThat(param("category")).isNull()
    }

    @Test
    fun `promoted false is omitted rather than sent as 0`() = runTest {
        // `promoted=0` is a filter of its own upstream (everything NOT promoted).
        api().getAgentsPaginated(limit = 20, promoted = false)

        assertThat(param("promoted")).isNull()
    }

    @Test
    fun `search, category and requiredPermission are sent when given`() = runTest {
        api().getAgentsPaginated(limit = 20, search = "math", category = "education", requiredPermission = 2)

        assertThat(param("search")).isEqualTo("math")
        assertThat(param("category")).isEqualTo("education")
        assertThat(param("requiredPermission")).isEqualTo("2")
    }

    @Test
    fun `the response's after is decoded for the next request`() = runTest {
        val page = api().getAgentsPaginated(limit = 1)

        assertThat(page.hasMore).isTrue()
        assertThat(page.after).isEqualTo("eyJ1cGRhdGVkQXQiOiIyMDI2In0=")
        assertThat(page.data.map { it.id }).containsExactly("agent_1")
    }
}
