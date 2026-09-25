package com.garfiec.librechat.core.network.api

import com.garfiec.librechat.core.model.Memory
import com.garfiec.librechat.core.model.MemoryPreferences
import com.garfiec.librechat.core.model.request.CreateMemoryRequest
import com.garfiec.librechat.core.model.request.UpdateMemoryPreferencesRequest
import com.garfiec.librechat.core.model.request.UpdateMemoryRequest
import com.garfiec.librechat.core.model.response.MemoriesResponse
import com.garfiec.librechat.core.model.response.MemoryMutationResponse
import com.garfiec.librechat.core.model.response.MemoryPreferencesResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.encodeURLPathPart
import io.ktor.http.path

/**
 * Every route here answers with an envelope, never the bare entity: the list comes back under
 * `memories` next to the pool's usage totals, the writes under `memory`/`preferences` next to a
 * redundant `created`/`updated`/`deleted` flag. Decoding a response straight into [Memory] fails
 * on the envelope, so each call unwraps explicitly.
 */
class MemoriesApi constructor(
    private val client: HttpClient,
) {

    suspend fun getMemories(): List<Memory> =
        client.get {
            url { path("api/memories") }
        }.body<MemoriesResponse>().memories

    suspend fun createMemory(request: CreateMemoryRequest): Memory =
        client.post {
            url { path("api/memories") }
            setBody(request)
        }.body<MemoryMutationResponse>().memory
            ?: error("Memory created but not returned by the server")

    suspend fun updatePreferences(request: UpdateMemoryPreferencesRequest): MemoryPreferences =
        client.patch {
            url { path("api/memories/preferences") }
            setBody(request)
        }.body<MemoryPreferencesResponse>().preferences

    /**
     * [agentId] selects the agent-partitioned entry with this key; null targets the shared
     * personal pool. Keys are only unique *within* a partition, so omitting the parameter on
     * an agent-scoped memory would edit the shared entry of the same name (or 404).
     */
    suspend fun updateMemory(key: String, request: UpdateMemoryRequest, agentId: String? = null): Memory =
        client.patch {
            url { path("api/memories/${key.encodeURLPathPart()}") }
            if (agentId != null) parameter("agentId", agentId)
            setBody(request)
        }.body<MemoryMutationResponse>().memory
            ?: error("Memory updated but not returned by the server")

    /**
     * `PATCH /api/memories/id/:id` (v0.8.8-rc2) — addresses the row by its stable `_id` instead of
     * by key.
     *
     * The key-addressed route cannot reach a row whose key the content filter blanked, and it is
     * ambiguous by construction (keys are unique only *within* a partition). 409 when the new key
     * collides inside the partition, 404 when the id does not resolve. The returned memory is run
     * through the same content-filter projection as the list, so it can come back redacted.
     */
    suspend fun updateMemoryById(id: String, request: UpdateMemoryRequest, agentId: String? = null): Memory =
        client.patch {
            url { path("api/memories/id/${id.encodeURLPathPart()}") }
            if (agentId != null) parameter("agentId", agentId)
            setBody(request)
        }.body<MemoryMutationResponse>().memory
            ?: error("Memory updated but not returned by the server")

    /** `DELETE /api/memories/id/:id` (v0.8.8-rc2). See [updateMemoryById]; answers `{deleted:true}`. */
    suspend fun deleteMemoryById(id: String, agentId: String? = null) {
        client.delete {
            url { path("api/memories/id/${id.encodeURLPathPart()}") }
            if (agentId != null) parameter("agentId", agentId)
        }
    }

    /** See [updateMemory] for how [agentId] selects the partition. */
    suspend fun deleteMemory(key: String, agentId: String? = null) {
        // Answers `{ deleted: true }`; the status carries the same verdict, so the body is dropped
        // rather than decoded (there is no entity to return).
        client.delete {
            url { path("api/memories/${key.encodeURLPathPart()}") }
            if (agentId != null) parameter("agentId", agentId)
        }
    }
}
