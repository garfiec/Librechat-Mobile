package com.garfiec.librechat.core.model.response

import com.garfiec.librechat.core.model.Memory
import com.garfiec.librechat.core.model.MemoryPreferences
import kotlinx.serialization.Serializable

/**
 * `GET /api/memories`. The rows arrive wrapped in an envelope alongside the shared pool's usage
 * totals — the list is never the top-level body.
 *
 * [totalTokens] / [tokenLimit] / [usagePercentage] count the SHARED personal pool only (the server
 * excludes agent-partitioned entries, because the limit applies per partition). [charLimit] is the
 * per-value cap the create/update endpoints enforce. [tokenLimit] and [usagePercentage] are null
 * unless the deployment configures a token limit; [charLimit] falls back to 10,000 server-side when
 * unconfigured, and is null here only from a server that predates the field.
 *
 * Returned whole by `MemoriesApi.getMemories` and `MemoryRepository.getMemories`, so the Memories
 * page can show the pool's usage next to the list.
 */
@Serializable
data class MemoriesResponse(
    val memories: List<Memory> = emptyList(),
    val totalTokens: Int? = null,
    val tokenLimit: Int? = null,
    val charLimit: Int? = null,
    val usagePercentage: Int? = null,
)

/**
 * `POST /api/memories` (`{ created: true, memory }`), `PATCH /api/memories/:key` and
 * `PATCH /api/memories/id/:id` (both `{ updated: true, memory }`). The flag carries no information
 * the HTTP status doesn't, so only the entry is modeled. [memory] is nullable because the key routes
 * resolve it by re-reading the partition and re-finding the key, which can come back empty; the
 * by-id route echoes the stored record, content-filter projected.
 */
@Serializable
data class MemoryMutationResponse(
    val memory: Memory? = null,
)

/** `PATCH /api/memories/preferences` → `{ updated: true, preferences: { memories } }`. */
@Serializable
data class MemoryPreferencesResponse(
    val preferences: MemoryPreferences = MemoryPreferences(),
)
