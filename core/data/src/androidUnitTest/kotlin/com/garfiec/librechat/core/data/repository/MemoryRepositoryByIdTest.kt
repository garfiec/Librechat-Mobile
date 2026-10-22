package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.BackendBuildClass
import com.garfiec.librechat.core.common.DetectedBackend
import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.common.identity.InMemoryActiveAccountProvider
import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.Memory
import com.garfiec.librechat.core.model.request.UpdateMemoryRequest
import com.garfiec.librechat.core.network.api.MemoriesApi
import com.garfiec.librechat.core.network.api.UserApi
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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

    private fun TestScope.repository(detected: DetectedBackend?): MemoryRepository {
        every { configRepository.detectedBackend } returns MutableStateFlow(detected)
        return MemoryRepositoryImpl(
            memoriesApi = api,
            userApi = mockk<UserApi>(),
            configRepository = configRepository,
            activeAccountProvider = InMemoryActiveAccountProvider().apply { set(AccountId("srv:user-a")) },
            applicationScope = CoroutineScope(StandardTestDispatcher(testScheduler)),
        )
    }

    private fun rc3() = DetectedBackend("0.8.8-rc3", BackendBuildClass.RC, "2026-09-17")
    private fun v087() = DetectedBackend("0.8.7", BackendBuildClass.OFFICIAL, "2026-06-26")

    private val plain = Memory(key = "likes_tea", value = "yes", id = "mem-1")
    private val redacted = Memory(key = "", value = "", id = "mem-2", contentFilterBlocked = true)

    /** A pre-rc2 server's answer to `/id/:id`: Express's default 404, HTML with no JSON `error`. */
    private fun routeMissing() = ApiException(
        statusCode = 404,
        message = "Not Found",
        body = ROUTE_MISSING_BODY,
    )

    /** The by-id handler's own 404: the route exists and the row is gone. */
    private fun memoryNotFound() = ApiException(
        statusCode = 404,
        message = MEMORY_NOT_FOUND,
        body = MEMORY_NOT_FOUND_BODY,
        serverAuthored = true,
    )

    /** LibreChat's own answer to an unmatched `/api` path (`apiNotFound`). */
    private fun endpointNotFound() = ApiException(
        statusCode = 404,
        message = "Endpoint not found",
        body = ENDPOINT_NOT_FOUND_BODY,
        serverAuthored = true,
    )

    private fun agentNotFound() = ApiException(
        statusCode = 404,
        message = MEMORY_AGENT_NOT_FOUND,
        body = AGENT_NOT_FOUND_BODY,
        serverAuthored = true,
    )

    private companion object {
        const val AGENT_NOT_FOUND_BODY = """{"error":"Agent not found."}"""
        const val MEMORY_NOT_FOUND_BODY = """{"error":"Memory not found."}"""
        const val ENDPOINT_NOT_FOUND_BODY = """{"message":"Endpoint not found"}"""
        const val ROUTE_MISSING_BODY =
            "<!DOCTYPE html><html><body><pre>Cannot PATCH /api/memories/id/mem-1</pre></body></html>"
    }

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
        coEvery { api.deleteMemoryById("mem-1", null) } throws routeMissing()

        val result = repository(rc3()).deleteMemory(plain)

        assertThat(result).isInstanceOf(Result.Success::class.java)
        coVerify(exactly = 1) { api.deleteMemory("likes_tea", null) }
    }

    @Test
    fun `a redacted entry reports the 404 rather than addressing the wrong row`() = runTest {
        // Its key is blank, so the key-addressed route would either 404 or — worse — resolve to a
        // different row. There is no fallback to make and saying so is the honest answer.
        coEvery { api.deleteMemoryById("mem-2", null) } throws routeMissing()

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
        val result = repository(rc3()).updateMemory(redacted, value = "")

        assertThat(result).isInstanceOf(Result.Error::class.java)
        // Typed, with no text of its own: the screen words it.
        assertThat((result as Result.Error).exception).isInstanceOf(MemoryContentFilteredException::class.java)
        assertThat(result.message).isNull()
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
    fun `an update keeps the partition on the by-id route`() = runTest {
        val scoped = plain.copy(agentId = "agent_7")
        val request = UpdateMemoryRequest(value = "no")
        coEvery { api.updateMemoryById("mem-1", request, "agent_7") } returns scoped

        repository(rc3()).updateMemory(scoped, value = "no")

        coVerify(exactly = 1) { api.updateMemoryById("mem-1", request, "agent_7") }
    }

    /** Without the partition the key route would overwrite the user's personal memory of that key. */
    @Test
    fun `an update keeps the partition on the key route`() = runTest {
        val scoped = plain.copy(agentId = "agent_7")
        val request = UpdateMemoryRequest(value = "no")
        coEvery { api.updateMemory("likes_tea", request, "agent_7") } returns scoped

        repository(v087()).updateMemory(scoped, value = "no")

        coVerify(exactly = 1) { api.updateMemory("likes_tea", request, "agent_7") }
        coVerify(exactly = 0) { api.updateMemory(any(), any(), null) }
    }

    @Test
    fun `an update that falls back from a by-id 404 keeps the partition`() = runTest {
        val scoped = plain.copy(agentId = "agent_7")
        val request = UpdateMemoryRequest(value = "no")
        coEvery { api.updateMemoryById("mem-1", any(), "agent_7") } throws routeMissing()
        coEvery { api.updateMemory("likes_tea", request, "agent_7") } returns scoped

        val result = repository(rc3()).updateMemory(scoped, value = "no")

        assertThat(result).isInstanceOf(Result.Success::class.java)
        coVerify(exactly = 1) { api.updateMemory("likes_tea", request, "agent_7") }
        coVerify(exactly = 0) { api.updateMemory(any(), any(), null) }
    }

    @Test
    fun `a rename that falls back from a by-id 404 keeps the partition`() = runTest {
        val scoped = plain.copy(agentId = "agent_7")
        val rename = UpdateMemoryRequest(value = "yes", key = "loves_tea")
        coEvery { api.updateMemoryById("mem-1", any(), "agent_7") } throws routeMissing()
        coEvery { api.updateMemory("likes_tea", rename, "agent_7") } returns scoped.copy(key = "loves_tea")

        val result = repository(rc3()).updateMemory(scoped, value = "yes", newKey = "loves_tea")

        assertThat(result).isInstanceOf(Result.Success::class.java)
        coVerify(exactly = 1) { api.updateMemory("likes_tea", rename, "agent_7") }
        coVerify(exactly = 0) { api.updateMemory(any(), any(), null) }
    }

    @Test
    fun `a rename carries the new key on the by-id route`() = runTest {
        val rename = UpdateMemoryRequest(value = "yes", key = "loves_tea")
        coEvery { api.updateMemoryById("mem-1", rename, null) } returns plain.copy(key = "loves_tea")

        val result = repository(rc3()).updateMemory(plain, value = "yes", newKey = "loves_tea")

        assertThat(result).isInstanceOf(Result.Success::class.java)
        coVerify(exactly = 1) { api.updateMemoryById("mem-1", rename, null) }
    }

    @Test
    fun `a rename carries the new key on the key route`() = runTest {
        val rename = UpdateMemoryRequest(value = "yes", key = "loves_tea")
        coEvery { api.updateMemory("likes_tea", rename, null) } returns plain.copy(key = "loves_tea")

        repository(v087()).updateMemory(plain, value = "yes", newKey = "loves_tea")

        coVerify(exactly = 1) { api.updateMemory("likes_tea", rename, null) }
    }

    @Test
    fun `a rename that falls back from a by-id 404 keeps the new key`() = runTest {
        val rename = UpdateMemoryRequest(value = "yes", key = "loves_tea")
        coEvery { api.updateMemoryById("mem-1", rename, null) } throws routeMissing()
        coEvery { api.updateMemory("likes_tea", rename, null) } returns plain.copy(key = "loves_tea")

        repository(rc3()).updateMemory(plain, value = "yes", newKey = "loves_tea")

        coVerify(exactly = 1) { api.updateMemory("likes_tea", rename, null) }
    }

    /** The routes read `bodyKey || urlKey`: an unchanged key is left off rather than re-sent. */
    @Test
    fun `an unchanged or blank new key is not sent`() = runTest {
        val valueOnly = UpdateMemoryRequest(value = "no")
        coEvery { api.updateMemoryById("mem-1", valueOnly, null) } returns plain
        val repo = repository(rc3())

        repo.updateMemory(plain, value = "no", newKey = "likes_tea")
        repo.updateMemory(plain, value = "no", newKey = " likes_tea ")
        repo.updateMemory(plain, value = "no", newKey = "  ")
        repo.updateMemory(plain, value = "no")

        coVerify(exactly = 4) { api.updateMemoryById("mem-1", valueOnly, null) }
    }

    @Test
    fun `a new key is trimmed before it is sent`() = runTest {
        val rename = UpdateMemoryRequest(value = "yes", key = "loves_tea")
        coEvery { api.updateMemoryById("mem-1", rename, null) } returns plain

        repository(rc3()).updateMemory(plain, value = "yes", newKey = " loves_tea ")

        coVerify(exactly = 1) { api.updateMemoryById("mem-1", rename, null) }
    }

    /**
     * The agent-partition middleware's 404 runs before any route, so the key route would only
     * repeat it. The refusal itself comes back, body intact, for the caller to word.
     */
    @Test
    fun `an agent-partition 404 on the by-id route does not fall back`() = runTest {
        val scoped = plain.copy(agentId = "agent_gone")
        coEvery { api.updateMemoryById("mem-1", any(), "agent_gone") } throws agentNotFound()

        val result = repository(rc3()).updateMemory(scoped, value = "no", newKey = "loves_tea")

        assertThat(result).isInstanceOf(Result.Error::class.java)
        val exception = (result as Result.Error).exception as ApiException
        assertThat(exception.statusCode).isEqualTo(404)
        assertThat(exception.body).isEqualTo(AGENT_NOT_FOUND_BODY)
        coVerify(exactly = 0) { api.updateMemory(any(), any(), any()) }
    }

    @Test
    fun `an agent-partition 404 on a by-id delete does not fall back either`() = runTest {
        val scoped = plain.copy(agentId = "agent_gone")
        coEvery { api.deleteMemoryById("mem-1", "agent_gone") } throws agentNotFound()

        val result = repository(rc3()).deleteMemory(scoped)

        assertThat(result).isInstanceOf(Result.Error::class.java)
        coVerify(exactly = 0) { api.deleteMemory(any(), any()) }
    }

    /**
     * DELETE lets a missing agent through (`allowMissingAgent`), so a memory whose agent is gone
     * (no `agentName`) is still removed by id, in its own partition.
     */
    @Test
    fun `an orphaned agent memory is deleted by id in its partition`() = runTest {
        val orphan = plain.copy(agentId = "agent_gone", agentName = null)

        val result = repository(rc3()).deleteMemory(orphan)

        assertThat(result).isInstanceOf(Result.Success::class.java)
        coVerify(exactly = 1) { api.deleteMemoryById("mem-1", "agent_gone") }
        coVerify(exactly = 0) { api.deleteMemory(any(), any()) }
    }

    @Test
    fun `an orphaned agent memory is deleted by key on a server without the by-id routes`() = runTest {
        val orphan = plain.copy(agentId = "agent_gone", agentName = null)

        val result = repository(v087()).deleteMemory(orphan)

        assertThat(result).isInstanceOf(Result.Success::class.java)
        coVerify(exactly = 1) { api.deleteMemory("likes_tea", "agent_gone") }
    }

    /** A 404 with no JSON `error` from the memory handlers (the route is absent) falls back. */
    @Test
    fun `a by-id 404 with no memory handler error falls back`() = runTest {
        coEvery { api.updateMemoryById("mem-1", any(), null) } throws routeMissing()
        coEvery { api.updateMemory("likes_tea", any(), null) } returns plain.copy(value = "no")

        val result = repository(rc3()).updateMemory(plain, value = "no")

        assertThat(result).isInstanceOf(Result.Success::class.java)
        coVerify(exactly = 1) { api.updateMemory("likes_tea", UpdateMemoryRequest(value = "no"), null) }
    }

    /**
     * The row was deleted elsewhere and its key may now belong to a re-created row: the key route
     * would delete that one instead.
     */
    @Test
    fun `a by-id delete answered Memory not found does not fall back`() = runTest {
        coEvery { api.deleteMemoryById("mem-1", null) } throws memoryNotFound()

        val result = repository(rc3()).deleteMemory(plain)

        val error = (result as Result.Error).exception as ApiException
        assertThat(error.statusCode).isEqualTo(404)
        coVerify(exactly = 0) { api.deleteMemory(any(), any()) }
    }

    @Test
    fun `a by-id edit answered Memory not found does not fall back`() = runTest {
        coEvery { api.updateMemoryById("mem-1", any(), null) } throws memoryNotFound()

        val result = repository(rc3()).updateMemory(plain, value = "no")

        val error = (result as Result.Error).exception as ApiException
        assertThat(error.statusCode).isEqualTo(404)
        coVerify(exactly = 0) { api.updateMemory(any(), any(), any()) }
    }

    /** The key route's rename is create-then-delete: falling back would replace the other row. */
    @Test
    fun `a by-id rename answered Memory not found does not fall back`() = runTest {
        val rename = UpdateMemoryRequest(value = "yes", key = "loves_tea")
        coEvery { api.updateMemoryById("mem-1", rename, null) } throws memoryNotFound()

        val result = repository(rc3()).updateMemory(plain, value = "yes", newKey = "loves_tea")

        assertThat(result).isInstanceOf(Result.Error::class.java)
        coVerify(exactly = 0) { api.updateMemory(any(), any(), any()) }
    }

    /**
     * LibreChat answers an unmatched `/api` path with its own JSON 404 (`apiNotFound`, mounted on
     * `/api` in `api/server/index.js`), not Express's HTML one: a proxy or a build that routes
     * `/api` through it is what a pre-rc2 server's by-id path reaches. It names no memory handler,
     * so it is the route missing.
     */
    @Test
    fun `a by-id 404 with LibreChat's JSON Endpoint not found falls back`() = runTest {
        coEvery { api.updateMemoryById("mem-1", any(), null) } throws endpointNotFound()
        coEvery { api.updateMemory("likes_tea", any(), null) } returns plain.copy(value = "no")
        coEvery { api.deleteMemoryById("mem-1", null) } throws endpointNotFound()
        val repo = repository(rc3())

        val update = repo.updateMemory(plain, value = "no")
        val delete = repo.deleteMemory(plain)

        assertThat(update).isInstanceOf(Result.Success::class.java)
        assertThat(delete).isInstanceOf(Result.Success::class.java)
        coVerify(exactly = 1) { api.updateMemory("likes_tea", UpdateMemoryRequest(value = "no"), null) }
        coVerify(exactly = 1) { api.deleteMemory("likes_tea", null) }
    }

    /**
     * `PATCH /api/memories/preferences` is the opt-out route, mounted before `/:key`: an edit of a
     * memory keyed `preferences` sent there would reach the opt-out handler. Refused, typed, on a
     * server without the by-id routes; by id it is an ordinary edit.
     */
    @Test
    fun `an edit of a memory keyed preferences never goes to the key route`() = runTest {
        val shadowed = plain.copy(key = "preferences")
        val upper = plain.copy(key = "Preferences", id = null)

        val old = repository(v087()).updateMemory(shadowed, value = "no", newKey = "prefs")
        val noId = repository(rc3()).updateMemory(upper, value = "no")

        assertThat((old as Result.Error).exception).isInstanceOf(MemoryKeyRouteShadowedException::class.java)
        assertThat(old.message).isNull()
        assertThat((noId as Result.Error).exception).isInstanceOf(MemoryKeyRouteShadowedException::class.java)
        coVerify(exactly = 0) { api.updateMemory(any(), any(), any()) }
    }

    @Test
    fun `an edit of a memory keyed preferences that falls back from by-id is refused too`() = runTest {
        val shadowed = plain.copy(key = "preferences")
        coEvery { api.updateMemoryById("mem-1", any(), null) } throws routeMissing()

        val result = repository(rc3()).updateMemory(shadowed, value = "no")

        assertThat((result as Result.Error).exception).isInstanceOf(MemoryKeyRouteShadowedException::class.java)
        coVerify(exactly = 0) { api.updateMemory(any(), any(), any()) }
    }

    @Test
    fun `a memory keyed preferences is still edited by id and deleted by key`() = runTest {
        val shadowed = plain.copy(key = "preferences")
        coEvery { api.updateMemoryById("mem-1", any(), null) } returns shadowed.copy(value = "no")

        val byId = repository(rc3()).updateMemory(shadowed, value = "no")
        // No DELETE route shares the path, so the key route reaches the memory handler.
        val delete = repository(v087()).deleteMemory(shadowed)

        assertThat(byId).isInstanceOf(Result.Success::class.java)
        assertThat(delete).isInstanceOf(Result.Success::class.java)
        coVerify(exactly = 1) { api.deleteMemory("preferences", null) }
    }

    /** The server stored the write and could not echo it back: still a success. */
    @Test
    fun `a 2xx update with no memory echoed is a success`() = runTest {
        coEvery { api.updateMemoryById("mem-1", any(), null) } returns null

        val result = repository(rc3()).updateMemory(plain, value = "no")

        assertThat(result).isEqualTo(Result.Success(null))
    }
}
