package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.network.ConnectivityObserver
import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.response.ChatAbortResponse
import com.garfiec.librechat.core.network.api.ChatApi
import com.garfiec.librechat.core.network.sse.SseClient
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test

/**
 * The abort route's "ask me again" answers. On generation protocol v1 (mobile advertises no version)
 * a stop whose partial failed to save is a 409 `ABORT_PERSISTENCE_FAILED` with `Retry-After: 1`;
 * the run is kept for that retry, so reporting a stop failure would strand it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatRepositoryAbortRetryTest {

    private val chatApi = mockk<ChatApi>()

    private fun repository(scheduler: kotlinx.coroutines.test.TestCoroutineScheduler) = ChatRepositoryImpl(
        chatApi = chatApi,
        sseClient = mockk<SseClient>(relaxed = true),
        connectivityObserver = mockk<ConnectivityObserver>(relaxed = true),
        dispatcher = UnconfinedTestDispatcher(scheduler),
        json = Json,
        projectChanges = ProjectChangeSignal(),
    )

    private fun conflict(code: String) = ApiException(
        statusCode = 409,
        message = "Conflict",
        body = """{"code":"$code","generationProtocolVersion":1}""",
        retryAfterSeconds = 1,
    )

    @Test
    fun `a persistence failure is retried and the stop goes through`() = runTest {
        var attempts = 0
        coEvery { chatApi.abortChat(any(), any()) } answers {
            attempts++
            if (attempts == 1) throw conflict("ABORT_PERSISTENCE_FAILED") else ChatAbortResponse(aborted = "conv-1")
        }

        val result = repository(testScheduler).abortChat("conv-1", isTemporary = false, claimSteers = {})

        assertThat(attempts).isEqualTo(2)
        assertThat((result as Result.Success).data.aborted).isEqualTo("conv-1")
    }

    @Test
    fun `a replaced run is reported rather than retried`() = runTest {
        var attempts = 0
        coEvery { chatApi.abortChat(any(), any()) } answers {
            attempts++
            throw conflict("RUN_REPLACED")
        }

        val result = repository(testScheduler).abortChat("conv-1", isTemporary = false, claimSteers = {})

        assertThat(attempts).isEqualTo(1)
        assertThat(result).isInstanceOf(Result.Error::class.java)
    }

    @Test
    fun `the v2 settled fields decode`() {
        val decoded = Json { ignoreUnknownKeys = true }.decodeFromString<ChatAbortResponse>(
            """{"success":false,"settled":true,"code":"RUN_ALREADY_SETTLED","streamId":"c","terminalStatus":"completed"}""",
        )

        assertThat(decoded.success).isFalse()
        assertThat(decoded.settled).isTrue()
        assertThat(decoded.terminalStatus).isEqualTo("completed")
        assertThat(decoded.code).isEqualTo("RUN_ALREADY_SETTLED")
        assertThat(decoded.persistenceFailed).isFalse()
    }
}
