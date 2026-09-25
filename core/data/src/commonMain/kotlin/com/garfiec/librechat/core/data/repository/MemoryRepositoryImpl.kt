package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.BackendVersion
import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.common.result.safeApiCall
import com.garfiec.librechat.core.model.Memory
import com.garfiec.librechat.core.model.MemoryPreferences
import com.garfiec.librechat.core.model.request.CreateMemoryRequest
import com.garfiec.librechat.core.model.request.UpdateMemoryPreferencesRequest
import com.garfiec.librechat.core.model.request.UpdateMemoryRequest
import com.garfiec.librechat.core.network.api.MemoriesApi

class MemoryRepositoryImpl(
    private val memoriesApi: MemoriesApi,
    private val configRepository: ConfigRepository,
) : MemoryRepository {

    override suspend fun getMemories(): Result<List<Memory>> =
        safeApiCall { memoriesApi.getMemories() }

    override suspend fun createMemory(request: CreateMemoryRequest): Result<Memory> =
        safeApiCall { memoriesApi.createMemory(request) }

    override suspend fun updatePreferences(request: UpdateMemoryPreferencesRequest): Result<MemoryPreferences> =
        safeApiCall { memoriesApi.updatePreferences(request) }

    override suspend fun updateMemory(memory: Memory, request: UpdateMemoryRequest): Result<Memory> {
        val id = memory.byIdHandle()
        if (id != null) {
            val byId = safeApiCall { memoriesApi.updateMemoryById(id, request, memory.agentId) }
            if (!byId.isRouteMissing()) return byId
        }
        return safeApiCall { memoriesApi.updateMemory(memory.key, request, memory.agentId) }
    }

    override suspend fun deleteMemory(memory: Memory): Result<Unit> {
        val id = memory.byIdHandle()
        if (id != null) {
            val byId = safeApiCall { memoriesApi.deleteMemoryById(id, memory.agentId) }
            if (!byId.isRouteMissing()) return byId
            // A blanked key addresses nothing, so there is no fallback to make and reporting the
            // 404 is the honest answer — the entry genuinely cannot be reached on this server.
            if (memory.key.isBlank()) return byId
        }
        return safeApiCall { memoriesApi.deleteMemory(memory.key, memory.agentId) }
    }

    /**
     * The row's `_id` when the by-id routes are worth attempting.
     *
     * `_id` has been on every list response since well before the routes existed, so its presence
     * is not a version signal. Suppressed only on a server KNOWN to predate them; everything else
     * is attempted and falls back on a 404, which costs one request and is invisible to the user.
     * Deliberately unlatched: this repository has no account-switch hook, and a process-wide latch
     * would carry one server's verdict onto the next account's.
     */
    private fun Memory.byIdHandle(): String? = id?.takeIf {
        !BackendVersion.featureSupport(
            configRepository.detectedBackend.value,
            minVersion = "0.8.8-rc2",
        ).isRuledOut
    }

    private fun Result<*>.isRouteMissing(): Boolean =
        ((this as? Result.Error)?.exception as? ApiException)?.statusCode == HTTP_NOT_FOUND

    private companion object {
        const val HTTP_NOT_FOUND = 404
    }
}
