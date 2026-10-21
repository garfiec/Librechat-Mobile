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
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Pins `POST /api/agents/:id/duplicate` against upstream `duplicateAgentHandler`, which answers
 * `201 {agent, actions}`. Read as a bare agent, the body has no top-level `id` and fails to decode
 * after the server has already made the copy.
 */
class AgentsApiDuplicateWireShapeTest {

    private lateinit var lastRequest: HttpRequestData

    private fun api(body: String): AgentsApi {
        val engine = MockEngine { request ->
            lastRequest = request
            respond(
                content = body,
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        return AgentsApi(HttpClient(engine) { install(ContentNegotiation) { json(librechatJson) } })
    }

    @Test
    fun `the copy is read from the agent field`() = runTest {
        val body = """
            {"agent":{"_id":"6650f0c0","id":"agent_copy","name":"Coding Assistant (1/2/26, 10:00)",
            "provider":"openAI","model":"gpt-5","author":"u1","tools":["execute_code"],
            "createdAt":"2026-10-20T10:00:00.000Z","updatedAt":"2026-10-20T10:00:00.000Z"},
            "actions":[]}
        """.trimIndent().replace("\n", "")

        val response = api(body).duplicateAgent("agent_source")

        assertThat(lastRequest.method).isEqualTo(HttpMethod.Post)
        assertThat(lastRequest.url.encodedPath).endsWith("api/agents/agent_source/duplicate")
        assertThat(response.agent.id).isEqualTo("agent_copy")
    }

    @Test
    fun `an action the client cannot decode does not fail the copy`() = runTest {
        val body = """
            {"agent":{"id":"agent_copy","name":"Copy"},
            "actions":[{"_id":"a1","action_id":42,"metadata":"not an object"}]}
        """.trimIndent().replace("\n", "")

        val response = api(body).duplicateAgent("agent_source")

        assertThat(response.agent.id).isEqualTo("agent_copy")
    }
}
