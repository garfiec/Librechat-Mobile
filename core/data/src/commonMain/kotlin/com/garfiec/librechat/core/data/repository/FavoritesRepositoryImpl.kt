package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.identity.AccountState
import com.garfiec.librechat.core.common.identity.ActiveAccountProvider
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.common.result.safeApiCall
import com.garfiec.librechat.core.model.FavoritesLimits
import com.garfiec.librechat.core.model.UserFavorite
import com.garfiec.librechat.core.network.api.FavoritesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FavoritesRepositoryImpl(
    private val favoritesApi: FavoritesApi,
    activeAccountProvider: ActiveAccountProvider,
    applicationScope: CoroutineScope,
) : FavoritesRepository {

    private val _favorites = MutableStateFlow<List<UserFavorite>>(emptyList())
    override val favorites: StateFlow<List<UserFavorite>> = _favorites.asStateFlow()

    init {
        // A process-lifetime singleton holding one server's pins. Reset when the active account
        // changes: the incoming account's refresh replaces the list only on success, so a flaky
        // incoming server would otherwise keep rendering the outgoing account's pinned models.
        applicationScope.launch {
            activeAccountProvider.state
                .mapNotNull { (it as? AccountState.Resolved)?.id }
                .distinctUntilChanged()
                .drop(1)
                .collect { _favorites.value = emptyList() }
        }
    }

    /**
     * Serializes write paths so two rapid pin toggles can't lose each other,
     * and so an error-path rollback never overwrites a parallel successful
     * write — both [setFavorites] and the rollback's refetch run inside this lock.
     */
    private val writeMutex = Mutex()

    override suspend fun refresh(): Result<List<UserFavorite>> {
        return when (val result = safeApiCall { favoritesApi.getFavorites() }) {
            is Result.Success -> {
                _favorites.value = result.data
                result
            }
            else -> result
        }
    }

    override suspend fun setFavorites(list: List<UserFavorite>): Result<List<UserFavorite>> {
        if (list.size > FavoritesLimits.MAX_FAVORITES) {
            return Result.Error(
                message = "Maximum ${FavoritesLimits.MAX_FAVORITES} favorites allowed.",
            )
        }
        for (fav in list) {
            val fields = listOf(fav.agentId, fav.model, fav.endpoint, fav.spec)
            if (fields.any { (it?.length ?: 0) > FavoritesLimits.MAX_STRING_LENGTH }) {
                return Result.Error(
                    message = "A favorite field exceeds the ${FavoritesLimits.MAX_STRING_LENGTH} character limit.",
                )
            }
        }

        return writeMutex.withLock {
            // Optimistic publish so callers' star icons toggle immediately.
            // Snapshot the prior list inside the lock so a concurrent caller queued
            // behind us starts from the post-write state, not the pre-optimistic one.
            val previous = _favorites.value
            _favorites.value = list

            when (val result = safeApiCall { favoritesApi.updateFavorites(list) }) {
                is Result.Success -> {
                    _favorites.value = result.data
                    result
                }
                is Result.Error -> {
                    // Roll back via a fresh GET so the cache reflects the server's
                    // authoritative state rather than our stale optimistic value.
                    when (val refetch = safeApiCall { favoritesApi.getFavorites() }) {
                        is Result.Success -> _favorites.value = refetch.data
                        else -> _favorites.value = previous
                    }
                    result
                }
                is Result.Loading -> result
            }
        }
    }
}
