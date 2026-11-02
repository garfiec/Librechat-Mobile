package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.BackendVersion
import com.garfiec.librechat.core.common.identity.ActiveAccountProvider
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.common.result.safeApiCall
import com.garfiec.librechat.core.model.reasoning.ModelReasoning
import com.garfiec.librechat.core.network.api.EndpointTokenApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

/** One endpoint's per-model efforts and when they stop being trustworthy. */
data class ReasoningCatalog(
    /** model → efforts, for the one endpoint asked about. */
    val models: Map<String, ModelReasoning>,
    /** Epoch ms after which the server no longer holds this catalog; refetch then. */
    val expiresAtMs: Long,
)

/**
 * `GET /api/endpoints/reasoning-capabilities` (v0.8.9-rc1): the reasoning efforts each model of an
 * OpenRouter endpoint accepts. Only asked for an endpoint whose efforts come from that catalog
 * (`ReasoningSettingResolver.usesCatalog`).
 */
interface ReasoningCapabilitiesRepository {
    /**
     * The endpoint's catalog, from memory while the server's lifetime for it has not run out. A
     * failure (offline, the 503 the server answers while the catalog cannot be read) is returned and
     * never remembered: the efforts are unknown then, and the next call asks again. Null on a server
     * older than v0.8.9-rc1 or one whose version is not known, which have no such route.
     */
    suspend fun catalog(endpoint: String): Result<ReasoningCatalog>?
}

class ReasoningCapabilitiesRepositoryImpl(
    private val endpointTokenApi: EndpointTokenApi,
    private val configRepository: ConfigRepository,
    private val activeAccountProvider: ActiveAccountProvider,
    private val nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : ReasoningCapabilitiesRepository {

    private val mutex = Mutex()

    // Keyed by account too: the server lists only the models the caller's role may use.
    private val cache = mutableMapOf<Pair<String, String>, ReasoningCatalog>()

    override suspend fun catalog(endpoint: String): Result<ReasoningCatalog>? {
        val version = configRepository.detectedBackendVersion.value ?: return null
        if (!BackendVersion.isCompatibleOrNewer(version, MIN_VERSION)) return null
        val key = activeAccountProvider.awaitResolvedAccount().toString() to endpoint
        return mutex.withLock {
            cache[key]?.takeIf { nowMs() < it.expiresAtMs }?.let { return@withLock Result.Success(it) }
            cache.remove(key)
            val requestedAt = nowMs()
            when (val result = safeApiCall { endpointTokenApi.getReasoningCapabilities(endpoint) }) {
                is Result.Success -> {
                    val catalog = ReasoningCatalog(
                        models = result.data.capabilities[endpoint].orEmpty(),
                        expiresAtMs = requestedAt + result.data.expiresInMs.coerceAtLeast(0),
                    )
                    cache[key] = catalog
                    Result.Success(catalog)
                }
                is Result.Error -> result
                is Result.Loading -> result
            }
        }
    }

    private companion object {
        const val MIN_VERSION = "0.8.9-rc1"
    }
}
