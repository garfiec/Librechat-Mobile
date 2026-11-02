package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.model.request.BranchMessageRequest
import com.garfiec.librechat.core.model.request.ForkConversationRequest
import com.garfiec.librechat.core.network.di.librechatJson
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
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Wire shape of the v0.8.9-rc1 tool-call preview opt-in and the full-part route. */
class ToolCallPreviewsApiTest {

    private val requests = mutableListOf<HttpRequestData>()

    private fun client(body: String): HttpClient = HttpClient(
        MockEngine { request ->
            requests += request
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        },
    ) {
        install(ContentNegotiation) { json(librechatJson) }
        defaultRequest {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
        }
    }

    @Test
    fun `a load asks for previews only when told to`() = runTest {
        val api = MessagesApi(client("[]"))
        api.getMessages("conv-1", toolPreviews = true)
        api.getMessages("conv-1", toolPreviews = false)
        assertThat(requests[0].url.encodedPath).isEqualTo("/api/messages/conv-1")
        assertThat(requests[0].url.parameters["toolPreviews"]).isEqualTo("1")
        assertThat(requests[1].url.parameters.contains("toolPreviews")).isFalse()
    }

    @Test
    fun `the part route encodes every id and omits empty scope`() = runTest {
        val api = MessagesApi(
            client("""{"conversationId":"c/1","messageId":"m 1","partIndex":3,"tool_call":{"id":"call|x","output":"full"}}"""),
        )
        val response = api.getToolCallPart(
            conversationId = "c/1",
            messageId = "m 1",
            partIndex = 3,
            toolCallId = "call|x&y",
            stepId = "",
            agentId = null,
        )
        val request = requests.single()
        assertThat(request.url.encodedPath).isEqualTo("/api/messages/c%2F1/m%201/parts/3")
        assertThat(request.url.parameters["toolCallId"]).isEqualTo("call|x&y")
        assertThat(request.url.parameters.contains("stepId")).isFalse()
        assertThat(request.url.parameters.contains("agentId")).isFalse()
        assertThat(response.toolCall.output).isEqualTo("full")
    }

    @Test
    fun `the part route sends step and agent when present`() = runTest {
        val api = MessagesApi(client("""{"tool_call":{"id":"a"}}"""))
        api.getToolCallPart("c", "m", 0, "a", stepId = "step_1", agentId = "agent_x____1")
        val parameters = requests.single().url.parameters
        assertThat(parameters["stepId"]).isEqualTo("step_1")
        assertThat(parameters["agentId"]).isEqualTo("agent_x____1")
    }

    @Test
    fun `fork, duplicate and shared fork opt in to previews`() = runTest {
        val convos = ConversationsApi(client("""{"conversation":{"conversationId":"c2"},"messages":[]}"""))
        convos.forkConversation(ForkConversationRequest(conversationId = "c", messageId = "m"))
        ConversationsApi(client("""{"conversationId":"c3"}""")).duplicateConversation("c", null)
        ShareApi(client("""{"conversation":{"conversationId":"c4"},"messages":[]}""")).forkSharedConversation("share-1")
        assertThat(requests.map { it.url.parameters["toolPreviews"] }).containsExactly("1", "1", "1")
    }

    @Test
    fun `branch opts in to previews`() = runTest {
        val api = MessagesApi(client("""{"messageId":"m2","conversationId":"c"}"""))
        api.branchMessage(BranchMessageRequest(conversationId = "c", messageId = "m", agentId = "a"))
        assertThat(requests.single().url.parameters["toolPreviews"]).isEqualTo("1")
    }
}
