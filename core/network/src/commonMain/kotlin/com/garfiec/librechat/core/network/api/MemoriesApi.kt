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
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.path

/**
 * Every route here answers with an envelope, never the bare entity: the list comes back under
 * `memories` next to the pool's usage totals, the writes under `memory`/`preferences` next to a
 * redundant `created`/`updated`/`deleted` flag. Decoding a response straight into [Memory] fails
 * on the envelope. The list is returned with its envelope, because the usage totals are what the
 * page shows; the writes are unwrapped to their entry.
 *
 * A write that answers 2xx has succeeded even when its `memory` is null: the server re-reads the
 * partition and re-finds the key to fill it, which can come back empty. Those calls return null
 * rather than throwing, so a caller never reports a stored write as a failure (and a retry then
 * hits 409 on the entry that is already there).
 */
class MemoriesApi constructor(
    private val client: HttpClient,
) {

    suspend fun getMemories(): MemoriesResponse =
        client.get {
            url { path("api/memories") }
        }.body()

    suspend fun createMemory(request: CreateMemoryRequest): Memory? =
        client.post {
            url { path("api/memories") }
            setBody(request)
        }.body<MemoryMutationResponse>().memory

    suspend fun updatePreferences(request: UpdateMemoryPreferencesRequest): MemoryPreferences =
        client.patch {
            url { path("api/memories/preferences") }
            setBody(request)
        }.body<MemoryPreferencesResponse>().preferences

    /**
     * [agentId] selects the agent-partitioned entry with this key; null targets the shared
     * personal pool. Keys are only unique *within* a partition, so omitting the parameter on
     * an agent-scoped memory would edit the shared entry of the same name (or 404).
     *
     * [key] addresses the entry as it is now; [UpdateMemoryRequest.key] renames it.
     */
    suspend fun updateMemory(key: String, request: UpdateMemoryRequest, agentId: String? = null): Memory? =
        client.patch {
            url { memoryPath(key) }
            if (agentId != null) parameter("agentId", agentId)
            setBody(request)
        }.body<MemoryMutationResponse>().memory

    /**
     * `PATCH /api/memories/id/:id` (v0.8.8-rc2) — addresses the row by its stable `_id` instead of
     * by key.
     *
     * The key-addressed route cannot reach a row whose key the content filter blanked, and it is
     * ambiguous by construction (keys are unique only *within* a partition). 409 when the new key
     * collides inside the partition, 404 when the id does not resolve. The returned memory is run
     * through the same content-filter projection as the list, so it can come back redacted.
     */
    suspend fun updateMemoryById(id: String, request: UpdateMemoryRequest, agentId: String? = null): Memory? =
        client.patch {
            url { memoryPath("id", id) }
            if (agentId != null) parameter("agentId", agentId)
            setBody(request)
        }.body<MemoryMutationResponse>().memory

    /** `DELETE /api/memories/id/:id` (v0.8.8-rc2). See [updateMemoryById]; answers `{deleted:true}`. */
    suspend fun deleteMemoryById(id: String, agentId: String? = null) {
        client.delete {
            url { memoryPath("id", id) }
            if (agentId != null) parameter("agentId", agentId)
        }
    }

    /** See [updateMemory] for how [agentId] selects the partition. */
    suspend fun deleteMemory(key: String, agentId: String? = null) {
        // Answers `{ deleted: true }`; the status carries the same verdict, so the body is dropped
        // rather than decoded (there is no entity to return).
        client.delete {
            url { memoryPath(key) }
            if (agentId != null) parameter("agentId", agentId)
        }
    }

    /**
     * `api/memories/[...segments]`. A key is ONE segment, appended with `encodeSlash = true` so the
     * builder escapes it exactly once: `path()` escapes an already-encoded key a second time
     * (`%20` -> `%2520`), and Express decodes the param only once, so a legacy key with a space or a
     * slash (keys predating the `^[a-z_]+$` rule) would never match its row.
     */
    private fun URLBuilder.memoryPath(vararg segments: String) {
        path("api/memories")
        appendPathSegments(segments.toList(), encodeSlash = true)
    }
}
