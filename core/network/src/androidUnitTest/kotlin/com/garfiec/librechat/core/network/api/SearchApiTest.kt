package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.network.di.librechatJson
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Wire shapes of the two search routes: `/api/search/enable` sends a bare JSON boolean
 * (`res.send(false)`), and `/api/messages?search=` sends `{ messages, nextCursor: null }` whose
 * hits are stored messages overlaid with Meilisearch fields.
 */
class SearchApiTest {

    private val requests = mutableListOf<HttpRequestData>()

    private fun client(body: String, status: HttpStatusCode = HttpStatusCode.OK) = HttpClient(
        MockEngine { request ->
            requests += request
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        },
    ) { install(ContentNegotiation) { json(librechatJson) } }

    @Test
    fun `enable reads a bare true`() = runTest {
        assertThat(SearchApi(client("true")).isSearchEnabled()).isTrue()
        assertThat(requests.single().url.encodedPath).isEqualTo("/api/search/enable")
    }

    @Test
    fun `enable reads a bare false`() = runTest {
        assertThat(SearchApi(client("false")).isSearchEnabled()).isFalse()
    }

    @Test
    fun `enable treats anything else as disabled`() = runTest {
        assertThat(SearchApi(client("<html>maintenance</html>")).isSearchEnabled()).isFalse()
    }

    @Test
    fun `message search sends the query and decodes hydrated hits`() = runTest {
        val body = """
            {"messages":[{"_id":"66","messageId":"m1","conversationId":"c1","parentMessageId":"p",
              "sender":"Claude","text":"Use flow.debounce","isCreatedByUser":false,"model":"claude",
              "endpoint":"agents","title":"Kotlin Flow operators","_formatted":{"text":"<em>flow</em>"},
              "content":[{"type":"text","text":"Use flow.debounce"}],"unfinished":false,"error":false}],
             "nextCursor":null}
        """.trimIndent()

        val response = MessagesApi(client(body)).searchMessages("kotlin flow")

        val request = requests.single()
        assertThat(request.url.encodedPath).isEqualTo("/api/messages")
        assertThat(request.url.parameters["search"]).isEqualTo("kotlin flow")
        assertThat(request.url.parameters["pageSize"]).isEqualTo(MessagesApi.SEARCH_PAGE_SIZE.toString())
        val hit = response.messages.single()
        assertThat(hit.messageId).isEqualTo("m1")
        assertThat(hit.title).isEqualTo("Kotlin Flow operators")
        assertThat(hit.content?.single()?.text).isEqualTo("Use flow.debounce")
        assertThat(response.nextCursor).isNull()
    }
}
