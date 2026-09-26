package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.Memory
import com.garfiec.librechat.core.model.MemoryPreferences
import com.garfiec.librechat.core.model.request.CreateMemoryRequest
import com.garfiec.librechat.core.model.request.UpdateMemoryPreferencesRequest
import com.garfiec.librechat.core.model.request.UpdateMemoryRequest

/**
 * Repository for user memory CRUD operations.
 *
 * A memory is identified by its key *within a partition*: `agentId == null` is the shared
 * personal pool, a non-null `agentId` is that agent's private pool. The same key can exist in
 * both, so every mutation must carry the [Memory.agentId] of the entry it targets — omitting it
 * edits or deletes the shared-pool entry of the same name instead (or 404s).
 */
interface MemoryRepository {
    suspend fun getMemories(): Result<List<Memory>>
    suspend fun createMemory(request: CreateMemoryRequest): Result<Memory>
    suspend fun updatePreferences(request: UpdateMemoryPreferencesRequest): Result<MemoryPreferences>

    /**
     * Updates [memory]'s value. Addresses the row by its stable `_id` where the server supports it
     * (v0.8.8-rc2), falling back to the key-addressed route — which cannot reach a row whose key
     * the content filter blanked, and is ambiguous across partitions.
     */
    suspend fun updateMemory(memory: Memory, request: UpdateMemoryRequest): Result<Memory>

    /** Deletes [memory]. Addresses it the same way as [updateMemory]. */
    suspend fun deleteMemory(memory: Memory): Result<Unit>
}
