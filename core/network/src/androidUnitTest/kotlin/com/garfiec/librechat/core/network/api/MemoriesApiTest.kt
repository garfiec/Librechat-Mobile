package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.model.request.CreateMemoryRequest
import com.garfiec.librechat.core.model.request.UpdateMemoryPreferencesRequest
import com.garfiec.librechat.core.model.request.UpdateMemoryRequest
import com.garfiec.librechat.core.network.di.librechatJson
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.accept
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Pins the envelope shapes of the memories routes. Every one of them wraps its payload, so a
 * regression that decodes a response as the bare entity fails here instead of at runtime with an
 * empty memories screen.
 */
class MemoriesApiTest {

    // The shipped instance, so "omitted when null" is pinned against the config the app sends with.
    private fun jsonClient(engine: MockEngine): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) { json(librechatJson) }
        defaultRequest {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
        }
    }

    private fun MockRequestHandleScope.respondJson(
        content: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ): HttpResponseData = respond(
        content = content,
        status = status,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )

    private fun HttpRequestData.bodyText(): String =
        (body as OutgoingContent.ByteArrayContent).bytes().decodeToString()

    @Test
    fun `getMemories returns the list with its usage envelope`() = runTest {
        val engine = MockEngine {
            respond(
                content = """
                    {
                      "memories": [
                        {"key":"likes_tea","value":"yes","updated_at":"2026-07-20T10:00:00.000Z","tokenCount":12},
                        {"key":"tone","value":"terse","agentId":"agent_1","agentName":"Helper"}
                      ],
                      "totalTokens": 12,
                      "tokenLimit": 2000,
                      "charLimit": 10000,
                      "usagePercentage": 1
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val response = MemoriesApi(jsonClient(engine)).getMemories()
        val memories = response.memories

        assertThat(response.totalTokens).isEqualTo(12)
        assertThat(response.tokenLimit).isEqualTo(2000)
        assertThat(response.charLimit).isEqualTo(10000)
        assertThat(response.usagePercentage).isEqualTo(1)
        assertThat(memories).hasSize(2)
        assertThat(memories[0].tokenCount).isEqualTo(12)
        assertThat(memories[1].tokenCount).isNull()
        assertThat(memories[0].updatedAt).isEqualTo("2026-07-20T10:00:00.000Z")
        assertThat(memories[0].agentId).isNull()
        assertThat(memories[1].agentId).isEqualTo("agent_1")
        assertThat(memories[1].agentName).isEqualTo("Helper")
    }

    @Test
    fun `createMemory unwraps the created entry`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"created":true,"memory":{"key":"likes_tea","value":"yes"}}""",
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val memory = MemoriesApi(jsonClient(engine))
            .createMemory(CreateMemoryRequest(key = "likes_tea", value = "yes"))

        assertThat(memory?.key).isEqualTo("likes_tea")
    }

    @Test
    fun `updateMemory unwraps the updated entry and targets the agent partition`() = runTest {
        var capturedUrl: String? = null
        val engine = MockEngine { request ->
            capturedUrl = request.url.toString()
            respond(
                content = """{"updated":true,"memory":{"key":"tone","value":"warm","agentId":"agent_1"}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val memory = MemoriesApi(jsonClient(engine))
            .updateMemory("tone", UpdateMemoryRequest(value = "warm"), agentId = "agent_1")

        assertThat(capturedUrl).contains("agentId=agent_1")
        assertThat(memory?.value).isEqualTo("warm")
        assertThat(memory?.agentId).isEqualTo("agent_1")
    }

    /**
     * Every write route adds the partition on its own line. One that lost it would address the
     * personal memory instead: a by-id delete would 404 and read as "already gone" while the agent's
     * memory stays, and a key-route delete would remove the personal memory of that key.
     */
    @Test
    fun `every write route sends the agent partition`() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            requests += request
            respondJson("""{"updated":true,"deleted":true,"memory":{"key":"tone","value":"warm","agentId":"agent_1"}}""")
        }
        val api = MemoriesApi(jsonClient(engine))

        api.updateMemory("tone", UpdateMemoryRequest(value = "warm"), agentId = "agent_1")
        api.updateMemoryById("mem-1", UpdateMemoryRequest(value = "warm"), agentId = "agent_1")
        api.deleteMemoryById("mem-1", agentId = "agent_1")
        api.deleteMemory("tone", agentId = "agent_1")

        assertThat(requests.map { it.url.encodedPath to it.url.parameters["agentId"] }).containsExactly(
            "/api/memories/tone" to "agent_1",
            "/api/memories/id/mem-1" to "agent_1",
            "/api/memories/id/mem-1" to "agent_1",
            "/api/memories/tone" to "agent_1",
        ).inOrder()
    }

    /** A personal memory carries no partition at all, never an empty `agentId=`. */
    @Test
    fun `a personal write sends no agent partition`() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            requests += request
            respondJson("""{"updated":true,"deleted":true,"memory":{"key":"tone","value":"warm"}}""")
        }
        val api = MemoriesApi(jsonClient(engine))

        api.updateMemory("tone", UpdateMemoryRequest(value = "warm"))
        api.updateMemoryById("mem-1", UpdateMemoryRequest(value = "warm"))
        api.deleteMemoryById("mem-1")
        api.deleteMemory("tone")

        assertThat(requests).hasSize(4)
        requests.forEach { request ->
            assertThat(request.url.parameters.contains("agentId")).isFalse()
            assertThat(request.url.encodedQuery).isEmpty()
        }
    }

    @Test
    fun `updatePreferences sends and reads the memories key`() = runTest {
        var capturedBody: String? = null
        val engine = MockEngine { request ->
            capturedBody = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            respond(
                content = """{"updated":true,"preferences":{"memories":false}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val preferences = MemoriesApi(jsonClient(engine))
            .updatePreferences(UpdateMemoryPreferencesRequest(enabled = false))

        assertThat(capturedBody).isEqualTo("""{"memories":false}""")
        assertThat(preferences.enabled).isFalse()
    }

    @Test
    fun `getMemories leaves the limits null when the server sends none`() = runTest {
        val engine = MockEngine {
            respondJson(
                """{"memories":[],"totalTokens":0,"tokenLimit":null,"charLimit":10000,"usagePercentage":null}""",
            )
        }

        val response = MemoriesApi(jsonClient(engine)).getMemories()

        assertThat(response.tokenLimit).isNull()
        assertThat(response.usagePercentage).isNull()
        assertThat(response.charLimit).isEqualTo(10000)
    }

    /**
     * The server fills `memory` by re-reading the partition after the write, which can come back
     * empty. The write is stored either way, so a 2xx must not read as a failure.
     */
    @Test
    fun `a created 2xx with no memory is a success, not a throw`() = runTest {
        val engine = MockEngine { respondJson("""{"created":true}""", HttpStatusCode.Created) }

        val memory = MemoriesApi(jsonClient(engine))
            .createMemory(CreateMemoryRequest(key = "likes_tea", value = "yes"))

        assertThat(memory).isNull()
    }

    @Test
    fun `an updated 2xx with a null memory is a success on both routes`() = runTest {
        val engine = MockEngine { respondJson("""{"updated":true,"memory":null}""") }
        val api = MemoriesApi(jsonClient(engine))

        assertThat(api.updateMemory("tone", UpdateMemoryRequest(value = "warm"))).isNull()
        assertThat(api.updateMemoryById("mem-1", UpdateMemoryRequest(value = "warm"))).isNull()
    }

    /** Both routes read `bodyKey || urlKey`, so an unchanged key is left out rather than sent. */
    @Test
    fun `an update without a new key omits the key field`() = runTest {
        val bodies = mutableListOf<String>()
        val engine = MockEngine { request ->
            bodies += request.bodyText()
            respondJson("""{"updated":true,"memory":{"key":"tone","value":"warm"}}""")
        }
        val api = MemoriesApi(jsonClient(engine))

        api.updateMemory("tone", UpdateMemoryRequest(value = "warm"))
        api.updateMemoryById("mem-1", UpdateMemoryRequest(value = "warm"))

        assertThat(bodies).containsExactly("""{"value":"warm"}""", """{"value":"warm"}""")
    }

    @Test
    fun `a rename carries the new key on both routes`() = runTest {
        val requests = mutableListOf<Pair<String, String>>()
        val engine = MockEngine { request ->
            requests += request.url.encodedPath to request.bodyText()
            respondJson("""{"updated":true,"memory":{"key":"voice","value":"warm"}}""")
        }
        val api = MemoriesApi(jsonClient(engine))
        val rename = UpdateMemoryRequest(value = "warm", key = "voice")

        api.updateMemory("tone", rename)
        api.updateMemoryById("mem-1", rename)

        assertThat(requests).containsExactly(
            "/api/memories/tone" to """{"value":"warm","key":"voice"}""",
            "/api/memories/id/mem-1" to """{"value":"warm","key":"voice"}""",
        ).inOrder()
    }

    /** A legacy key (from before the `^[a-z_]+$` rule) may hold a space or a slash. */
    @Test
    fun `a key is escaped exactly once as one path segment`() = runTest {
        val paths = mutableListOf<String>()
        val engine = MockEngine { request ->
            paths += request.url.encodedPath
            respondJson("""{"updated":true,"deleted":true,"memory":{"key":"a b/c","value":"v"}}""")
        }
        val api = MemoriesApi(jsonClient(engine))

        api.updateMemory("a b/c", UpdateMemoryRequest(value = "v"))
        api.deleteMemory("a b/c")
        api.updateMemoryById("id 1", UpdateMemoryRequest(value = "v"))
        api.deleteMemoryById("id 1")

        assertThat(paths).containsExactly(
            "/api/memories/a%20b%2Fc",
            "/api/memories/a%20b%2Fc",
            "/api/memories/id/id%201",
            "/api/memories/id/id%201",
        ).inOrder()
    }
}
