package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.BackendBuildClass
import com.garfiec.librechat.core.common.DetectedBackend
import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.background.BackgroundTaskIndex
import com.garfiec.librechat.core.network.api.ConversationsApi
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class BackgroundTaskRepositoryImplTest {

    private val api = mockk<ConversationsApi>()
    private val configRepository = mockk<ConfigRepository>()

    private fun repository(detected: DetectedBackend?): BackgroundTaskRepository {
        every { configRepository.detectedBackend } returns MutableStateFlow(detected)
        return BackgroundTaskRepositoryImpl(api, configRepository)
    }

    private fun notFound() = ApiException(statusCode = 404, message = "Not Found")

    @Test
    fun `an rc4 tag predates the routes and is never asked`() = runTest {
        val repository = repository(DetectedBackend("0.8.8-rc4", BackendBuildClass.RC, "2026-09-23"))

        assertThat(repository.isRuledOutForServer()).isTrue()
        assertThat((repository.getTasks("c1") as Result.Success).data).isNull()
        coVerify(exactly = 0) { api.getBackgroundTasks(any()) }
    }

    @Test
    fun `a dev build dated on the landing day is asked`() = runTest {
        coEvery { api.getBackgroundTasks("c1") } returns BackgroundTaskIndex(conversationId = "c1")
        val repository = repository(DetectedBackend("0.8.8-rc4", BackendBuildClass.DEV, "2026-09-25"))

        assertThat(repository.isRuledOutForServer()).isFalse()
        assertThat((repository.getTasks("c1") as Result.Success).data).isNotNull()
    }

    @Test
    fun `an unplaceable server's 404 latches until clear`() = runTest {
        coEvery { api.getBackgroundTasks(any()) } throws notFound()
        val repository = repository(null)

        assertThat((repository.getTasks("c1") as Result.Success).data).isNull()
        assertThat(repository.isRuledOutForServer()).isTrue()
        repository.getTasks("c2")
        coVerify(exactly = 1) { api.getBackgroundTasks(any()) }

        repository.clear()
        assertThat(repository.isRuledOutForServer()).isFalse()
    }

    @Test
    fun `a 404 from a server confirmed to have the routes does not latch`() = runTest {
        coEvery { api.getBackgroundTasks(any()) } throws notFound()
        val repository = repository(DetectedBackend("0.8.8", BackendBuildClass.OFFICIAL, "2026-09-30"))

        assertThat(repository.getTasks("c1")).isInstanceOf(Result.Error::class.java)
        assertThat(repository.isRuledOutForServer()).isFalse()
    }
}
