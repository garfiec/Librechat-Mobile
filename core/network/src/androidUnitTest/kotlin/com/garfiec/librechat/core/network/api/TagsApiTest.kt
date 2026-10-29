package com.garfiec.librechat.core.network.api

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
import io.ktor.http.decodeURLPart
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Test

/**
 * The `/api/tags` wire shapes, through a real client with the app's JSON settings
 * (`encodeDefaults = false`, `explicitNulls = false`), so clear-vs-omit is what production sends.
 */
class TagsApiTest {

    private val requests = mutableListOf<HttpRequestData>()

    private fun api(response: String = TAG_JSON): TagsApi {
        val engine = MockEngine { request ->
            requests += request
            respond(
                content = response,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(librechatJson) }
            defaultRequest { contentType(ContentType.Application.Json) }
        }
        return TagsApi(client)
    }

    private fun lastBody(): JsonElement = Json.parseToJsonElement((requests.last().body as TextContent).text)

    private fun json(text: String): JsonElement = Json.parseToJsonElement(text)

    /** What upstream does to the segment: Express decodes the param, then the route decodes it again. */
    private fun serverDecode(encodedPath: String): String =
        encodedPath.removePrefix("/api/tags/").decodeURLPart().decodeURLPart()

    @Test
    fun `createTag posts the bare body without an arg wrapper and omits unset fields`() = runTest {
        val tag = api().createTag("Work")

        assertThat(requests.single().method).isEqualTo(HttpMethod.Post)
        assertThat(requests.single().url.encodedPath).isEqualTo("/api/tags")
        assertThat(lastBody()).isEqualTo(json("""{"tag":"Work"}"""))
        assertThat(tag.tag).isEqualTo("Work")
    }

    @Test
    fun `createTag sends the attach fields when asked`() = runTest {
        api().createTag("Work", description = "Day job", addToConversation = true, conversationId = "c-1")

        assertThat(lastBody()).isEqualTo(
            json("""{"tag":"Work","description":"Day job","addToConversation":true,"conversationId":"c-1"}"""),
        )
    }

    @Test
    fun `updateTag sends an empty description to clear it`() = runTest {
        api().updateTag("Work", description = "")

        assertThat(requests.single().method).isEqualTo(HttpMethod.Put)
        assertThat(requests.single().url.encodedPath).isEqualTo("/api/tags/Work")
        assertThat(lastBody()).isEqualTo(json("""{"description":""}"""))
    }

    @Test
    fun `updateTag omits fields left null`() = runTest {
        api().updateTag("Work", position = 3)
        assertThat(lastBody()).isEqualTo(json("""{"position":3}"""))

        api().updateTag("Work", newTag = "Job")
        assertThat(lastBody()).isEqualTo(json("""{"tag":"Job"}"""))
    }

    @Test
    fun `deleteTag sends DELETE to the tag path`() = runTest {
        api().deleteTag("Work")

        assertThat(requests.single().method).isEqualTo(HttpMethod.Delete)
        assertThat(requests.single().url.encodedPath).isEqualTo("/api/tags/Work")
    }

    @Test
    fun `a tag name is escaped twice to survive the server's double decode`() = runTest {
        api().updateTag("50% off", position = 1)
        assertThat(requests.last().url.encodedPath).isEqualTo("/api/tags/50%2525%2520off")

        api().deleteTag("a/b")
        assertThat(requests.last().url.encodedPath).isEqualTo("/api/tags/a%252Fb")
    }

    @Test
    fun `every awkward name decodes back exactly on the server`() = runTest {
        val names = listOf(
            "a/b", "what?", "#1", "50%", "literal %20", "two words", "Café", "日本語", "😀 fun",
            "a+b", "x&y=z", "semi;colon", "at@sign", "dots..", "~tilde", "back\\slash",
        )
        for (name in names) {
            api().updateTag(name, position = 0)
            val put = requests.last().url.encodedPath
            api().deleteTag(name)
            val delete = requests.last().url.encodedPath

            assertThat(put).startsWith("/api/tags/")
            // One segment: the name's own slash never splits the route.
            assertThat(put.removePrefix("/api/tags/")).doesNotContain("/")
            assertThat(serverDecode(put)).isEqualTo(name)
            assertThat(delete).isEqualTo(put)
        }
    }

    @Test
    fun `updateConversationTags puts the full set and returns the server's list`() = runTest {
        val stored = api(response = """["web-only","Saved"]""")
            .updateConversationTags("c-1", listOf("web-only", "Saved"))

        assertThat(requests.single().method).isEqualTo(HttpMethod.Put)
        assertThat(requests.single().url.encodedPath).isEqualTo("/api/tags/convo/c-1")
        assertThat(lastBody()).isEqualTo(json("""{"tags":["web-only","Saved"]}"""))
        assertThat(stored).containsExactly("web-only", "Saved").inOrder()
    }

    private companion object {
        const val TAG_JSON = """{"_id":"t1","tag":"Work","user":"u1","count":0,"position":1}"""
    }
}
