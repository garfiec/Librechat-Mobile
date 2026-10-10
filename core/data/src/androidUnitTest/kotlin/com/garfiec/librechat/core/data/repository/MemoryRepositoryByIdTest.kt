package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.BackendBuildClass
import com.garfiec.librechat.core.common.DetectedBackend
import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.Memory
import com.garfiec.librechat.core.model.request.UpdateMemoryRequest
import com.garfiec.librechat.core.network.api.MemoriesApi
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * How a memory is addressed. The by-id routes (v0.8.8-rc2) exist because the key-addressed ones
 * cannot reach a row whose key the content filter blanked — so the fallback logic is the feature,
 * not an optimisation.
 */
class MemoryRepositoryByIdTest {

    private val api = mockk<MemoriesApi>(relaxUnitFun = true)
    private val configRepository = mockk<ConfigRepository>()

    private fun repository(detected: DetectedBackend?): MemoryRepository {
        every { configRepository.detectedBackend } returns MutableStateFlow(detected)
        return MemoryRepositoryImpl(api, configRepository)
    }

    private fun rc3() = DetectedBackend("0.8.8-rc3", BackendBuildClass.RC, "2026-09-17")
    private fun v087() = DetectedBackend("0.8.7", BackendBuildClass.OFFICIAL, "2026-06-26")

    private val plain = Memory(key = "likes_tea", value = "yes", id = "mem-1")
    private val redacted = Memory(key = "", value = "", id = "mem-2", contentFilterBlocked = true)

    private fun notFound() = ApiException(statusCode = 404, message = "Memory not found.")

    @Test
    fun `a supporting server is addressed by id`() = runTest {
        repository(rc3()).deleteMemory(plain)

        coVerify(exactly = 1) { api.deleteMemoryById("mem-1", null) }
        coVerify(exactly = 0) { api.deleteMemory(any(), any()) }
    }

    @Test
    fun `a tagged build below the routes goes straight to the key`() = runTest {
        // `_id` has been on every list response since long before the routes existed, so its
        // presence is not evidence the routes are there.
        repository(v087()).deleteMemory(plain)

        coVerify(exactly = 0) { api.deleteMemoryById(any(), any()) }
        coVerify(exactly = 1) { api.deleteMemory("likes_tea", null) }
    }

    @Test
    fun `a 404 on the by-id route falls back to the key`() = runTest {
        coEvery { api.deleteMemoryById("mem-1", null) } throws notFound()

        val result = repository(rc3()).deleteMemory(plain)

        assertThat(result).isInstanceOf(Result.Success::class.java)
        coVerify(exactly = 1) { api.deleteMemory("likes_tea", null) }
    }

    @Test
    fun `a redacted entry reports the 404 rather than addressing the wrong row`() = runTest {
        // Its key is blank, so the key-addressed route would either 404 or — worse — resolve to a
        // different row. There is no fallback to make and saying so is the honest answer.
        coEvery { api.deleteMemoryById("mem-2", null) } throws notFound()

        val result = repository(rc3()).deleteMemory(redacted)

        assertThat(result).isInstanceOf(Result.Error::class.java)
        coVerify(exactly = 0) { api.deleteMemory(any(), any()) }
    }

    @Test
    fun `an entry with no id uses the key`() = runTest {
        repository(rc3()).deleteMemory(plain.copy(id = null))

        coVerify(exactly = 0) { api.deleteMemoryById(any(), any()) }
        coVerify(exactly = 1) { api.deleteMemory("likes_tea", null) }
    }

    /**
     * A redaction blanks `value`, so an edit dialog can only seed an empty field — and `_id`
     * survives the redaction, so `updateMemoryById` would address the row perfectly and write that
     * blank over content the user was never shown, reporting a 200.
     *
     * Asserted against the REPOSITORY, deliberately. The two screens that omit the row's
     * `clickable` today prove only that those two screens are careful; this pins that no caller
     * can reach the route at all, which is what makes them belt-and-braces rather than the whole
     * enforcement.
     */
    @Test
    fun `a redacted entry cannot be updated through either route`() = runTest {
        val result = repository(rc3()).updateMemory(redacted, UpdateMemoryRequest(value = ""))

        assertThat(result).isInstanceOf(Result.Error::class.java)
        coVerify(exactly = 0) { api.updateMemoryById(any(), any(), any()) }
        coVerify(exactly = 0) { api.updateMemory(any(), any(), any()) }
    }

    /**
     * Deleting one is still allowed: the row is unreadable, so removing it is the only action left
     * that means anything, and it destroys nothing the user could otherwise recover.
     */
    @Test
    fun `a redacted entry can still be deleted`() = runTest {
        repository(rc3()).deleteMemory(redacted)

        coVerify(exactly = 1) { api.deleteMemoryById("mem-2", null) }
    }

    @Test
    fun `an update keeps the partition on both routes`() = runTest {
        val scoped = plain.copy(agentId = "agent_7")
        val request = UpdateMemoryRequest(value = "no")
        coEvery { api.updateMemoryById("mem-1", request, "agent_7") } returns scoped

        repository(rc3()).updateMemory(scoped, request)

        coVerify(exactly = 1) { api.updateMemoryById("mem-1", request, "agent_7") }
    }
}
