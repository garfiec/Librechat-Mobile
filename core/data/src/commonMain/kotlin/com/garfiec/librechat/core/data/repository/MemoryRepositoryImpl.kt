package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.BackendVersion
import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.common.identity.AccountState
import com.garfiec.librechat.core.common.identity.ActiveAccountProvider
import com.garfiec.librechat.core.common.identity.currentAccountId
import com.garfiec.librechat.core.common.result.ApiException
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.common.result.safeApiCall
import com.garfiec.librechat.core.model.Memory
import com.garfiec.librechat.core.model.MemoryPreferences
import com.garfiec.librechat.core.model.User
import com.garfiec.librechat.core.model.error.ServerErrorCode
import com.garfiec.librechat.core.model.request.CreateMemoryRequest
import com.garfiec.librechat.core.model.request.UpdateMemoryPreferencesRequest
import com.garfiec.librechat.core.model.request.UpdateMemoryRequest
import com.garfiec.librechat.core.model.response.MemoriesResponse
import com.garfiec.librechat.core.network.api.MemoriesApi
import com.garfiec.librechat.core.network.api.UserApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class MemoryRepositoryImpl(
    private val memoriesApi: MemoriesApi,
    private val userApi: UserApi,
    private val configRepository: ConfigRepository,
    private val activeAccountProvider: ActiveAccountProvider,
    private val applicationScope: CoroutineScope,
) : MemoryRepository {

    private val _memoriesEnabled = MutableStateFlow<Boolean?>(null)
    override val memoriesEnabled: StateFlow<Boolean?> = _memoriesEnabled.asStateFlow()

    /**
     * Guards [writeGeneration], [publishedFor] and every write of [_memoriesEnabled]. Reads, PATCHes
     * and the account-switch reset run on different threads, and each decides from one field and
     * writes another, so each decision and its write happen in one critical section. Never held
     * across a request, so a writer waits at most for a few field writes. Fair (first come, first
     * served), so a write's opening bump is never overtaken by a read that started after it.
     */
    private val preferenceLock = Mutex()

    /** The account [_memoriesEnabled] was last published for. Guarded by [preferenceLock]. */
    private var publishedFor: AccountId? = null

    /**
     * Moves when a preference write starts and again when it ends. A read publishes only if it
     * stayed put across the whole GET: one the server answered before a PATCH was applied would
     * otherwise land after it and put the old value back. Guarded by [preferenceLock]: an
     * increment lost between two concurrent writes could move it back to a value a read captured
     * while a write was in flight, and that read's stale answer would then publish over the PATCH.
     */
    private var writeGeneration = 0L

    init {
        // A process-lifetime singleton holding one account's opt-out. Reset when the active
        // account changes (a logout included), so the incoming account never shows or obeys the
        // outgoing one's switch. The first resolution at cold start is not a change. A value the
        // incoming account already published (its refresh beat this collector) is kept; the check
        // and the reset share the lock with publish, so that value can't be published between them
        // and then wiped.
        applicationScope.launch {
            activeAccountProvider.state
                .filterIsInstance<AccountState.Resolved>()
                .map { it.id }
                .distinctUntilChanged()
                .drop(1)
                .collect { incoming ->
                    preferenceLock.withLock {
                        if (publishedFor != incoming || incoming == null) {
                            publishedFor = null
                            _memoriesEnabled.value = null
                        }
                    }
                }
        }
    }

    /**
     * Publishes [enabled] only while the account the request started under is still active (one
     * that lands after a switch describes the outgoing account) and, for a read, only while no
     * preference write has started or ended since [readGeneration] was captured.
     */
    private suspend fun publish(origin: AccountId?, enabled: Boolean, readGeneration: Long? = null) {
        preferenceLock.withLock {
            if (readGeneration != null && readGeneration != writeGeneration) return
            if (activeAccountProvider.currentAccountId() != origin) return
            publishedFor = origin
            _memoriesEnabled.value = enabled
        }
    }

    private suspend fun moveWriteGeneration() {
        preferenceLock.withLock { writeGeneration++ }
    }

    override suspend fun refreshMemoriesPreference(): Result<Boolean> =
        when (val result = fetchUserPublishingPreference { safeApiCall { userApi.getUser() } }) {
            is Result.Success -> Result.Success(result.data.memoriesEnabled())
            is Result.Error -> result
            is Result.Loading -> result
        }

    override suspend fun fetchUserPublishingPreference(fetchUser: suspend () -> Result<User>): Result<User> {
        val origin = activeAccountProvider.currentAccountId()
        val generation = preferenceLock.withLock { writeGeneration }
        val result = fetchUser()
        if (result is Result.Success) publish(origin, result.data.memoriesEnabled(), readGeneration = generation)
        return result
    }

    /** An absent personalization block is the server default, on. */
    private fun User.memoriesEnabled(): Boolean = personalization?.memories ?: true

    override suspend fun getMemories(): Result<MemoriesResponse> =
        safeApiCall { memoriesApi.getMemories() }

    override suspend fun createMemory(request: CreateMemoryRequest): Result<Memory?> =
        safeApiCall { memoriesApi.createMemory(request) }

    /**
     * Runs in the application scope, so a caller cancelled mid-request (the user leaves the
     * Memories page) still publishes what the server stored: the PATCH was already sent, and open
     * chats follow [memoriesEnabled]. Started undispatched so the write generation moves before
     * this returns control, as a read the caller starts next must see it: the lock is free then
     * unless another thread is mid-publish, and a read queued behind that is behind this bump too.
     */
    override suspend fun updatePreferences(request: UpdateMemoryPreferencesRequest): Result<MemoryPreferences> {
        val origin = activeAccountProvider.currentAccountId()
        return applicationScope.async(start = CoroutineStart.UNDISPATCHED) {
            moveWriteGeneration()
            val result = try {
                safeApiCall { memoriesApi.updatePreferences(request) }
            } finally {
                // Even when this scope is cancelled: without the closing bump, a read that started
                // while the PATCH ran would see no change and publish its older answer.
                withContext(NonCancellable) { moveWriteGeneration() }
            }
            if (result is Result.Success) publish(origin, result.data.enabled)
            result
        }.await()
    }

    override suspend fun updateMemory(memory: Memory, value: String, newKey: String?): Result<Memory?> {
        // Redaction blanks what the user is SHOWN, not what is stored. Both PATCH routes reject a
        // blank value with 400, so the empty field an edit dialog seeds from a filtered row cannot
        // itself be saved — but anything the user TYPES over it is non-blank, succeeds with 200,
        // and replaces content they were never allowed to read.
        //
        // Refused here rather than at the screens. `_id` is the one handle redaction does not
        // blank, so `updateMemoryById` addresses a filtered row perfectly well, and the only thing
        // standing between a user and this today is `Modifier.clickable` being omitted in two
        // composables. Any third surface reaching this API — an edit-from-chat affordance, a
        // keyboard or a11y activation, a swipe action, a deep link — is the N+1th caller that
        // reintroduces it.
        if (memory.contentFilterBlocked == true) {
            return Result.Error(exception = MemoryContentFilteredException())
        }
        // Trimmed like the create route trims; the rename route stores the key as sent.
        val renamed = newKey?.trim()?.takeIf { it.isNotEmpty() && it != memory.key }
        val request = UpdateMemoryRequest(value = value, key = renamed)
        val id = memory.byIdHandle()
        if (id != null) {
            val byId = safeApiCall { memoriesApi.updateMemoryById(id, request, memory.agentId) }
            if (!byId.isRouteMissing()) return byId
            // Same rule as [deleteMemory]: a blanked key addresses nothing. `PATCH /api/memories/`
            // matches no route at all — upstream mounts only `patch('/:key')` — so the fallback
            // cannot answer, and reporting the by-id 404 beats a bare one from a path that was
            // never addressable.
            if (memory.key.isBlank()) return byId
        }
        // The key route's path would be another route's: the edit would land on the opt-out
        // handler, which answers an English 400 about a boolean (or a 403) for a memory edit.
        if (memory.key.lowercase() in MEMORY_KEY_ROUTE_SHADOWED_KEYS) {
            return Result.Error(exception = MemoryKeyRouteShadowedException())
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
     * is attempted and falls back on a route-missing 404 ([isRouteMissing]), which costs one
     * request and is invisible to the user.
     * Deliberately unlatched: a process-wide latch would carry one server's verdict onto the next
     * account's.
     */
    private fun Memory.byIdHandle(): String? = id?.takeIf {
        !BackendVersion.featureSupport(
            configRepository.detectedBackend.value,
            minVersion = "0.8.8-rc2",
        ).isRuledOut
    }

    /**
     * A by-id 404 that may mean the route is absent. Two JSON 404s are answers, not a missing
     * route, and are returned as they are:
     * - `Agent not found.` is the agent-partition middleware refusing the write before any route
     *   runs: the key route sits behind the same middleware, so falling back would only repeat the
     *   refusal (and, for a rename, could not help).
     * - `Memory not found.` is the by-id handler saying the row is gone. Falling back would address
     *   whatever row now holds that key — one re-created elsewhere — and overwrite it, or, for a
     *   rename (create-then-delete on the key route), replace it.
     */
    private fun Result<*>.isRouteMissing(): Boolean {
        val api = (this as? Result.Error)?.exception as? ApiException ?: return false
        if (api.statusCode != HTTP_NOT_FOUND) return false
        val error = ServerErrorCode.from(api.body)
        return error != MEMORY_AGENT_NOT_FOUND && error != MEMORY_NOT_FOUND
    }

    private companion object {
        const val HTTP_NOT_FOUND = 404
    }
}
