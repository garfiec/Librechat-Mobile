package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.Memory
import com.garfiec.librechat.core.model.MemoryPreferences
import com.garfiec.librechat.core.model.User
import com.garfiec.librechat.core.model.request.CreateMemoryRequest
import com.garfiec.librechat.core.model.request.UpdateMemoryPreferencesRequest
import com.garfiec.librechat.core.model.response.MemoriesResponse
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository for user memory CRUD operations.
 *
 * A memory is identified by its key *within a partition*: `agentId == null` is the shared
 * personal pool, a non-null `agentId` is that agent's private pool. The same key can exist in
 * both, so every mutation must carry the [Memory.agentId] of the entry it targets — omitting it
 * edits or deletes the shared-pool entry of the same name instead (or 404s).
 *
 * Failures carry no text of this layer's own. An HTTP failure is a [Result.Error] whose
 * `exception` is the `ApiException` with its status code (409 duplicate key, 400 over a limit or
 * blocked by a filter, 403 role permission, 404 gone; and the agent-partition refusals, 404
 * [MEMORY_AGENT_NOT_FOUND] and 403 [MEMORY_AGENT_ACCESS_DENIED]); a refusal made here is a typed exception
 * ([MemoryContentFilteredException]). The caller maps either to its own strings.
 */
interface MemoryRepository {

    /**
     * The user's own memory opt-out (`personalization.memories`) for the ACTIVE account, or null
     * while it is not known for that account. One source for every screen that shows or obeys it:
     * set by [refreshMemoriesPreference], by [fetchUserPublishingPreference] (a chat's profile read)
     * and by a successful [updatePreferences], and reset to null when the active account changes.
     */
    val memoriesEnabled: StateFlow<Boolean?>

    /**
     * Reads the opt-out from `GET /api/user` (it is stored on the profile, not returned by the
     * memories routes) and publishes it to [memoriesEnabled]. An absent personalization block is
     * the server default, on.
     */
    suspend fun refreshMemoriesPreference(): Result<Boolean>

    /**
     * Runs [fetchUser], a `GET /api/user` the caller makes for its own reasons (a chat reads the
     * profile for its header), and publishes the opt-out it carries the same way
     * [refreshMemoriesPreference] does: a read that overlapped a preference write, or that lands
     * after an account switch, is not published. Returns [fetchUser]'s result unchanged.
     */
    suspend fun fetchUserPublishingPreference(fetchUser: suspend () -> Result<User>): Result<User>

    /** The list together with the shared pool's usage totals and the per-value character cap. */
    suspend fun getMemories(): Result<MemoriesResponse>

    /** Success with a null entry means the server stored it but could not echo it back. */
    suspend fun createMemory(request: CreateMemoryRequest): Result<Memory?>

    /**
     * Publishes the stored preference to [memoriesEnabled] on success. The request outlives a
     * cancelled caller, so a write the server applied is published even after the screen is gone.
     */
    suspend fun updatePreferences(request: UpdateMemoryPreferencesRequest): Result<MemoryPreferences>

    /**
     * Sets [memory]'s value to [value] and, when [newKey] differs from its key, renames it. The
     * new key is sent only when it changed, on whichever route is used.
     *
     * Addresses the row by its stable `_id` where the server supports it (v0.8.8-rc2), falling
     * back to the key-addressed route — which cannot reach a row whose key the content filter
     * blanked, and is ambiguous across partitions. Success with a null entry means the server
     * stored it but could not echo it back.
     */
    suspend fun updateMemory(memory: Memory, value: String, newKey: String? = null): Result<Memory?>

    /** Deletes [memory]. Addresses it the same way as [updateMemory]. */
    suspend fun deleteMemory(memory: Memory): Result<Unit>
}

/**
 * [MemoryRepository.updateMemory] refused an entry the content filter redacted. Carries no
 * message: the screen says why in its own words.
 */
class MemoryContentFilteredException : Exception()

/**
 * [MemoryRepository.updateMemory] refused an edit only the key route could carry, for a key whose
 * `PATCH /api/memories/:key` path another route owns: the opt-out route `PATCH /preferences` is
 * mounted before `/:key` (`api/server/routes/memories.js`), and Express matches paths ignoring
 * case, so the edit would reach the opt-out handler instead. Carries no message: the screen says
 * why in its own words.
 */
class MemoryKeyRouteShadowedException : Exception()

/**
 * The key-route paths `PATCH /api/memories/:key` cannot reach, compared case-insensitively as
 * Express does. Registered in `scripts/mirrors.json`.
 */
internal val MEMORY_KEY_ROUTE_SHADOWED_KEYS: Set<String> = setOf("preferences")

/**
 * The agent-partition middleware's refusals, under `error` with no code
 * (`createAgentMemoryPartitionMiddleware`, `packages/api/src/memory/authorization.ts`). Matched on
 * the text because that 404 is otherwise indistinguishable from `Memory not found.` and from a
 * missing by-id route, and that 403 from a role refusal. DELETE lets a missing agent through, so
 * only the PATCH routes answer [MEMORY_AGENT_NOT_FOUND]. Registered in `scripts/mirrors.json`.
 */
const val MEMORY_AGENT_NOT_FOUND: String = "Agent not found."
const val MEMORY_AGENT_ACCESS_DENIED: String = "Agent access denied."

/**
 * The memory handlers' own 404 (`packages/api/src/memory/handlers.ts` for the by-id routes,
 * `api/server/routes/memories.js` for the key routes), under `error` with no code. On a by-id
 * route it means the row is gone, not that the route is: a pre-rc2 server answers an unmatched
 * `/id/:id` with Express's default 404, which carries no JSON `error`. Registered in
 * `scripts/mirrors.json`.
 */
const val MEMORY_NOT_FOUND: String = "Memory not found."
