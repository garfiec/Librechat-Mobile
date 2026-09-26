package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.model.request.DeleteFileEntry
import com.garfiec.librechat.core.model.request.DeleteFilesRequest
import com.garfiec.librechat.core.model.response.DeleteFilesResponse
import com.garfiec.librechat.core.network.di.librechatJson
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The three bodies `DELETE /api/files` actually sends.
 *
 * The 204 is the one that bit: the route filters out every entry with a falsy `filepath` and, when
 * nothing survives, answers `res.status(204).json(…)` — on which Express drops the body AND the
 * Content-Type, so ContentNegotiation never engages and a plain `.body<>()` throws. The agent
 * unlink answers 200 with only a `message`, naming no ids at all.
 */
class FilesApiDeleteTest {

    private suspend fun delete(
        status: HttpStatusCode,
        body: String,
        contentType: String? = "application/json",
    ): DeleteFilesResponse {
        val engine = MockEngine {
            respond(
                content = body,
                status = status,
                headers = contentType?.let { headersOf(HttpHeaders.ContentType, it) } ?: headersOf(),
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(librechatJson) }
            // As production does: the REQUEST body is serialized off this, never off the response.
            defaultRequest { contentType(ContentType.Application.Json) }
        }
        return FilesApi(client, librechatJson).deleteFiles(
            DeleteFilesRequest(
                files = listOf(DeleteFileEntry(fileId = "file-1", filepath = "/uploads/u1/file-1")),
            ),
        )
    }

    @Test
    fun `a 204 for a request the route filtered away decodes as nothing deleted`() = runTest {
        val response = delete(HttpStatusCode.NoContent, body = "", contentType = null)

        assertThat(response.deletedFileIds).isEmpty()
        assertThat(response.failedFileIds).isEmpty()
    }

    @Test
    fun `an agent unlink answers 200 with only a message`() = runTest {
        val response = delete(
            HttpStatusCode.OK,
            """{"message":"File associations removed successfully from agent"}""",
        )

        assertThat(response.message).isEqualTo("File associations removed successfully from agent")
        assertThat(response.failedFileIds).isEmpty()
    }

    @Test
    fun `a partial delete reports the file it could not remove`() = runTest {
        val response = delete(
            HttpStatusCode.OK,
            """{"message":"Files deleted successfully","deletedFileIds":["file-1"],"failedFileIds":["file-2"]}""",
        )

        assertThat(response.deletedFileIds).containsExactly("file-1")
        assertThat(response.failedFileIds).containsExactly("file-2")
    }
}
