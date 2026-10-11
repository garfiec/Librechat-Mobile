package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.common.result.safeApiCall
import com.garfiec.librechat.core.model.tchat.TchatModelCatalog
import com.garfiec.librechat.core.network.api.TchatCatalogApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

/**
 * TensorGrid's model catalog (free models, model categories) for the Tchat model picker. The
 * catalog is the same for every account, so one in-memory copy serves the whole process.
 */
interface TchatModelCatalogRepository {
    /** Null until the first successful fetch; a failed fetch leaves the last value in place. */
    val catalog: StateFlow<TchatModelCatalog?>

    /** Fetches the catalog unless the held copy is younger than the refresh interval. */
    suspend fun refreshIfStale()
}

class TchatModelCatalogRepositoryImpl(
    private val api: TchatCatalogApi,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : TchatModelCatalogRepository {

    private val _catalog = MutableStateFlow<TchatModelCatalog?>(null)
    override val catalog: StateFlow<TchatModelCatalog?> = _catalog.asStateFlow()

    private val mutex = Mutex()
    private var fetchedAtMillis: Long? = null

    override suspend fun refreshIfStale() {
        mutex.withLock {
            val fetchedAt = fetchedAtMillis
            if (fetchedAt != null && nowMillis() - fetchedAt < REFRESH_INTERVAL_MS) return
            when (val result = safeApiCall { api.getModelCatalog() }) {
                is Result.Success -> {
                    _catalog.value = result.data
                    fetchedAtMillis = nowMillis()
                }
                // Nothing is known on a failure; the picker falls back to id patterns and the
                // next surfacing retries.
                else -> Unit
            }
        }
    }

    companion object {
        /** Matches web's `staleTime` for the same query. */
        const val REFRESH_INTERVAL_MS: Long = 5 * 60_000L
    }
}
