package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.response.FilePreviewResponse
import com.garfiec.librechat.core.network.api.FilesApi
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** `GET /api/files/:id/preview` polling: the stop rules upstream's `previewRefetchInterval` uses. */
class FileRepositoryPreviewPollTest {

    private val filesApi = mockk<FilesApi>()
    private val repository = FileRepositoryImpl(filesApi, mockk(), mockk(), mockk(), mockk())

    private fun preview(status: String, text: String? = null) =
        FilePreviewResponse(fileId = "f1", status = status, text = text)

    @Test
    fun `polls every 2_5 s until the preview is ready`() = runTest {
        coEvery { filesApi.getFilePreview("f1") } returnsMany listOf(
            preview("pending"),
            preview("pending"),
            preview("ready", text = "hello"),
        )

        val result = repository.pollFilePreview("f1")

        assertThat((result as Result.Success).data.text).isEqualTo("hello")
        assertThat(currentTime).isEqualTo(5_000L)
    }

    @Test
    fun `one failed request does not end the poll`() = runTest {
        coEvery { filesApi.getFilePreview("f1") } throws ApiException(503, "Unavailable") andThen
            preview("ready", text = "hello")

        val result = repository.pollFilePreview("f1")

        assertThat((result as Result.Success).data.status).isEqualTo("ready")
    }

    @Test
    fun `stops after five consecutive errors`() = runTest {
        coEvery { filesApi.getFilePreview("f1") } throws ApiException(503, "Unavailable")

        val result = repository.pollFilePreview("f1")

        assertThat(result).isInstanceOf(Result.Error::class.java)
        coVerify(exactly = 5) { filesApi.getFilePreview("f1") }
    }

    @Test
    fun `a success between errors resets the error count`() = runTest {
        val error = ApiException(503, "Unavailable")
        coEvery { filesApi.getFilePreview("f1") } throws error andThenThrows error andThenThrows error andThenThrows
            error andThen preview("pending") andThenThrows error andThenThrows error andThenThrows error andThenThrows
            error andThen preview("ready")

        val result = repository.pollFilePreview("f1")

        assertThat((result as Result.Success).data.status).isEqualTo("ready")
        coVerify(exactly = 10) { filesApi.getFilePreview("f1") }
    }

    @Test
    fun `gives up pending after a 75 s budget of 30 requests`() = runTest {
        coEvery { filesApi.getFilePreview("f1") } returns preview("pending")

        val result = repository.pollFilePreview("f1")

        assertThat((result as Result.Success).data.status).isEqualTo("pending")
        coVerify(exactly = 30) { filesApi.getFilePreview("f1") }
        assertThat(currentTime).isEqualTo(72_500L)
    }
}
