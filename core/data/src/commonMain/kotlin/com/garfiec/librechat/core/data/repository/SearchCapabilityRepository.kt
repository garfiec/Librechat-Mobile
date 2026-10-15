package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.identity.AccountId
import com.garfiec.librechat.core.common.identity.AccountState
import com.garfiec.librechat.core.common.identity.ActiveAccountProvider
import com.garfiec.librechat.core.common.identity.currentAccountId
import com.garfiec.librechat.core.common.result.FailureKind
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.common.result.safeApiCall
import com.garfiec.librechat.core.data.datastore.ConfigCacheDataStore
import com.garfiec.librechat.core.data.util.SessionTask
import com.garfiec.librechat.core.network.api.SearchApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Whether the connected server can answer message searches. */
interface SearchCapabilityRepository {
    /**
     * True when the server's message search is enabled and healthy, false when it isn't (or the
     * server failed the probe with nothing cached), null until it is known for the active account.
     * A probe that couldn't reach the server leaves it unknown.
     */
    val serverSearchEnabled: Flow<Boolean?>

    /**
     * Re-probes the server; the session task calls this on login, cold start and switch, and a
     * search calls it when the connection returns with the answer still unknown.
     */
    suspend fun refresh()
}

/**
 * Probes `GET /api/search/enable`, caching the answer per server so a cold start knows it before
 * the probe returns. The answer is server-wide, so two accounts on one server share it.
 *
 * Every answer is tagged with the account it was probed for, and only shows while that account is
 * active: a probe that lands after a switch, possibly from another server, reads as unknown
 * rather than as the new account's answer.
 *
 * A probe runs in [scope] and concurrent refreshes for one account share it, so a search that
 * asks and is then cancelled by the next keystroke doesn't cancel the probe with it.
 */
class SearchCapabilityRepositoryImpl(
    private val searchApi: SearchApi,
    private val cache: ConfigCacheDataStore,
    private val activeAccountProvider: ActiveAccountProvider,
    private val scope: CoroutineScope,
) : SearchCapabilityRepository {

    private val answer = MutableStateFlow<Pair<AccountId, Boolean>?>(null)
    private val inFlightLock = Mutex()
    private var inFlight: Pair<AccountId, Deferred<Unit>>? = null

    override val serverSearchEnabled: Flow<Boolean?> =
        combine(activeAccountProvider.state, answer) { state, tagged ->
            val active = (state as? AccountState.Resolved)?.id
            tagged?.takeIf { active != null && it.first == active }?.second
        }.distinctUntilChanged()

    override suspend fun refresh() {
        val account = activeAccountProvider.currentAccountId() ?: return
        val running = inFlightLock.withLock {
            inFlight?.takeIf { (forAccount, job) -> forAccount == account && job.isActive }?.second
                ?: scope.async { probe(account) }.also { inFlight = account to it }
        }
        running.await()
    }

    private suspend fun probe(account: AccountId) {
        val cached = cache.loadSearchEnabled()
        if (cached != null) publish(account, cached)
        when (val probed = safeApiCall { searchApi.isSearchEnabled() }) {
            is Result.Success -> {
                if (activeAccountProvider.currentAccountId() != account) return
                cache.saveSearchEnabled(probed.data)
                publish(account, probed.data)
            }
            is Result.Error -> if (cached == null && !probed.isUnreachable()) publish(account, false)
            Result.Loading -> Unit
        }
    }

    /** Offline or timed out: the server said nothing, so its search isn't known to be off. */
    private fun Result.Error.isUnreachable() = kind == FailureKind.Network || kind == FailureKind.Timeout

    private fun publish(account: AccountId, enabled: Boolean) {
        if (activeAccountProvider.currentAccountId() == account) answer.value = account to enabled
    }
}

/** Probes server search availability whenever a session starts. */
class SearchCapabilitySessionTask(
    private val repository: SearchCapabilityRepository,
) : SessionTask {
    override suspend fun run() = repository.refresh()
}
