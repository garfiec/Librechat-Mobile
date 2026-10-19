package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.network.api.FilesApi
import com.garfiec.librechat.core.network.client.ServerUrlProvider
import com.garfiec.librechat.core.network.client.ServerUrlReadyPlugin
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test

/** Which route a generated file downloads from, decided by the shape of its filepath. */
class FileRepositoryDownloadAttachmentTest {

    private val filesApi = mockk<FilesApi>()
    private var baseUrl = "https://chat.example.com"
    private val repository = FileRepositoryImpl(
        filesApi,
        mockk(),
        mockk(),
        mockk(),
        object : ServerUrlProvider {
            override fun getBaseUrl() = baseUrl
        },
    )
    private val bytes = byteArrayOf(1, 2, 3)

    private suspend fun fetchedUrl(attachment: Attachment): String {
        val url = slot<String>()
        coEvery { filesApi.downloadFromUrl(capture(url)) } returns bytes
        assertThat((repository.downloadAttachment(attachment, userId = null) as Result.Success).data).isEqualTo(bytes)
        return url.captured
    }

    @Test
    fun `a fallback downloads from the sandbox route on the server origin`() = runTest {
        val url = fetchedUrl(Attachment(filename = "big.csv", filepath = "/api/files/code/download/sess/file"))

        assertThat(url).isEqualTo("https://chat.example.com/api/files/code/download/sess/file")
        coVerify(exactly = 0) { filesApi.downloadFile(any(), any()) }
    }

    @Test
    fun `a subpath deployment's prefix is not doubled`() = runTest {
        baseUrl = "https://host.example/librechat/"

        val url = fetchedUrl(
            Attachment(filename = "big.csv", filepath = "/librechat/api/files/code/download/sess/file"),
        )

        assertThat(url).isEqualTo("https://host.example/librechat/api/files/code/download/sess/file")
    }

    @Test
    fun `the execution-route query is kept verbatim`() = runTest {
        val path = "/api/files/code/download/sess/file?execution_profile=stateful&execution_route_key=stateful%3Aabc"

        val url = fetchedUrl(Attachment(filename = "out.txt", filepath = path))

        assertThat(url).isEqualTo("https://chat.example.com$path")
    }

    @Test
    fun `a stored file downloads by its id`() = runTest {
        coEvery { filesApi.getDownloadUrl("u1", "f1") } throws IllegalStateException("501")
        coEvery { filesApi.downloadFile("u1", "f1") } returns bytes

        val result = repository.downloadAttachment(
            Attachment(fileId = "f1", filename = "chart.png", filepath = "/images/u1/chart.png"),
            userId = "u1",
        )

        assertThat((result as Result.Success).data).isEqualTo(bytes)
        coVerify(exactly = 0) { filesApi.downloadFromUrl(any()) }
    }

    @Test
    fun `a stored file without a user is not requested`() = runTest {
        val result = repository.downloadAttachment(Attachment(fileId = "f1"), userId = null)

        assertThat(result).isInstanceOf(Result.Error::class.java)
        coVerify(exactly = 0) { filesApi.downloadFile(any(), any()) }
    }

    // ─── Through a real client ──────────────────────────────────────
    // The cases above only see the string handed to a mock; these see the request Ktor sends,
    // through the same URL resolution the production client runs.

    private fun sentRequestUrl(base: String, filepath: String): Url {
        var sent: Url? = null
        val provider = object : ServerUrlProvider {
            override fun getBaseUrl() = base
        }
        val client = HttpClient(
            MockEngine { request ->
                sent = request.url
                respond(bytes, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/octet-stream"))
            },
        ) {
            install(ServerUrlReadyPlugin) { serverUrlProvider = provider }
        }
        val realRepository = FileRepositoryImpl(FilesApi(client, Json), mockk(), mockk(), mockk(), provider)
        runTest {
            val result = realRepository.downloadAttachment(Attachment(filename = "out.txt", filepath = filepath), userId = null)
            assertThat((result as Result.Success).data).isEqualTo(bytes)
        }
        return checkNotNull(sent)
    }

    @Test
    fun `the sent request keeps the execution-route query encoded as the server gave it`() {
        val url = sentRequestUrl(
            "https://chat.example.com",
            "/api/files/code/download/sess/file?execution_profile=stateful&execution_route_key=stateful%3Aabc",
        )

        assertThat(url.encodedQuery).isEqualTo("execution_profile=stateful&execution_route_key=stateful%3Aabc")
        assertThat(url.encodedPath).isEqualTo("/api/files/code/download/sess/file")
    }

    @Test
    fun `the sent request on a subpath deployment carries its prefix once`() {
        val url = sentRequestUrl(
            "https://host.example/librechat/",
            "/librechat/api/files/code/download/sess/file?execution_route_key=stateful%3Aabc",
        )

        assertThat(url.host).isEqualTo("host.example")
        assertThat(url.encodedPath).isEqualTo("/librechat/api/files/code/download/sess/file")
        assertThat(url.encodedQuery).isEqualTo("execution_route_key=stateful%3Aabc")
    }
}
