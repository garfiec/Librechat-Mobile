package com.garfiec.librechat.core.network.api

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
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Wire shapes of the v0.8.9-rc1 project instructions and reference-file routes. */
class ProjectContextApiTest {

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

    private fun HttpRequestData.bodyText(): String =
        (body as OutgoingContent.ByteArrayContent).bytes().decodeToString()

    private val project = """{"_id":"p1","name":"Grant","instructions":"Be brief","contextRevision":4,
        "file_ids":["f1","f2"],"conversationCount":2}"""

    @Test
    fun `instructions save sends only instructions and the revision`() = runTest {
        val api = ProjectsApi(client(project))
        val saved = api.updateInstructions("p1", "Be brief", 3)
        val request = requests.single()
        assertThat(request.method).isEqualTo(HttpMethod.Patch)
        assertThat(request.url.encodedPath).isEqualTo("/api/projects/p1")
        assertThat(request.bodyText()).isEqualTo("""{"instructions":"Be brief","contextRevision":3}""")
        assertThat(saved.contextRevision).isEqualTo(4)
        assertThat(saved.fileIds).containsExactly("f1", "f2").inOrder()
    }

    @Test
    fun `a name edit never carries a revision or instructions`() = runTest {
        val api = ProjectsApi(client(project))
        api.updateProject("p1", name = "Grant", description = "")
        assertThat(requests.single().bodyText()).isEqualTo("""{"name":"Grant","description":""}""")
    }

    @Test
    fun `file list decodes an unknown availability without failing`() = runTest {
        val api = ProjectsApi(
            client(
                """[{"file_id":"f1","filename":"a.pdf","type":"application/pdf","bytes":10,"availability":"ready"},
                   {"file_id":"f2","availability":"unavailable"},{"file_id":"f3","availability":"quarantined"}]""",
            ),
        )
        val files = api.listProjectFiles("p1")
        assertThat(requests.single().url.encodedPath).isEqualTo("/api/projects/p1/files")
        assertThat(files.map { it.isReady }).containsExactly(true, false, false).inOrder()
        assertThat(files[1].filename).isNull()
    }

    @Test
    fun `available files omit an absent cursor and a blank search`() = runTest {
        val api = ProjectsApi(client("""{"files":[{"file_id":"f9","filename":"b.txt"}],"nextCursor":"c2"}"""))
        val first = api.listAvailableProjectFiles("p1", cursor = null, limit = 20, search = " ")
        api.listAvailableProjectFiles("p1", cursor = "c2", limit = 20, search = "budget")
        assertThat(requests[0].url.encodedPath).isEqualTo("/api/projects/p1/files/available")
        assertThat(requests[0].url.parameters.names()).containsExactly("limit")
        assertThat(requests[1].url.parameters["cursor"]).isEqualTo("c2")
        assertThat(requests[1].url.parameters["search"]).isEqualTo("budget")
        assertThat(first.files.single().fileId).isEqualTo("f9")
        assertThat(first.nextCursor).isEqualTo("c2")
    }

    @Test
    fun `add posts the file id and remove deletes by path`() = runTest {
        val api = ProjectsApi(client(project))
        api.addProjectFile("p1", "f3")
        api.removeProjectFile("p1", "f3")
        assertThat(requests[0].method).isEqualTo(HttpMethod.Post)
        assertThat(requests[0].url.encodedPath).isEqualTo("/api/projects/p1/files")
        assertThat(requests[0].bodyText()).isEqualTo("""{"file_id":"f3"}""")
        assertThat(requests[1].method).isEqualTo(HttpMethod.Delete)
        assertThat(requests[1].url.encodedPath).isEqualTo("/api/projects/p1/files/f3")
    }
}
