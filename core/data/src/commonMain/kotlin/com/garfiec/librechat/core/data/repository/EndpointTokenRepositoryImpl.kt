package com.garfiec.librechat.core.data.repository

import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.BackendVersion
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.common.result.safeApiCall
import com.garfiec.librechat.core.data.datastore.ConfigCacheDataStore
import com.garfiec.librechat.core.model.request.ContextProjectionRequest
import com.garfiec.librechat.core.model.usage.ContextUsage
import com.garfiec.librechat.core.model.usage.ModelTokenomics
import com.garfiec.librechat.core.network.api.EndpointTokenApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class EndpointTokenRepositoryImpl(
    private val endpointTokenApi: EndpointTokenApi,
    private val configRepository: ConfigRepository,
    private val configCache: ConfigCacheDataStore,
) : EndpointTokenRepository {

    // token-config is static within a session, so memoize it on this singleton: every
    // per-conversation ChatViewModel shares the same fetch instead of hitting the network
    // on each chat open. The mutex collapses concurrent first-callers into one request.
    private val tokenConfigMutex = Mutex()
    private var cachedTokenConfig: Map<String, Map<String, ModelTokenomics>>? = null

    override suspend fun getTokenConfig(): Result<Map<String, Map<String, ModelTokenomics>>> {
        cachedTokenConfig?.let { return Result.Success(it) }
        return tokenConfigMutex.withLock {
            cachedTokenConfig?.let { return@withLock Result.Success(it) }
            when (val result = safeApiCall { endpointTokenApi.getTokenConfig() }) {
                is Result.Success -> {
                    cachedTokenConfig = result.data
                    configCache.saveTokenConfig(result.data)
                    result
                }
                // Deliberately not memoized: the next caller retries the network, so the session
                // picks up the live config once the connection is back.
                is Result.Error -> configCache.loadTokenConfig()
                    ?.let { saved ->
                        Logger.d { "token-config fetch failed; using the copy saved for this server" }
                        Result.Success(saved)
                    }
                    ?: result
                is Result.Loading -> result
            }
        }
    }

    // Written from live stream events and read by the estimate, possibly from different
    // ViewModels' scopes; the StateFlow's atomic update keeps concurrent writes from losing keys.
    private val contextOverheads = MutableStateFlow<Map<String, Int>>(emptyMap())

    override fun recordContextOverhead(key: String, tokens: Int) {
        if (tokens > 0) contextOverheads.update { it + (key to tokens) }
    }

    override fun contextOverhead(key: String): Int = contextOverheads.value[key] ?: 0

    override suspend fun getContextProjection(
        request: ContextProjectionRequest,
    ): Result<ContextUsage?> {
        // Upstream #13953 (v0.8.8-rc1) REMOVED POST /api/endpoints/context-projection and
        // moved the gauge to a client-side / SSE-seeded computation. On such a server the POST 404s,
        // so skip it there and let the live `on_context_usage` SSE + token-config seed own the gauge
        // (a null result leaves any existing reading in place). This inverts the earlier gate that
        // enabled the projection at >= 0.8.7. Plain version compare since the rc1 tag shipped; on
        // an unresolved server the POST is still issued and its 404 is discarded by the caller.
        if (BackendVersion.supportsFeature(
                configRepository.detectedBackend.value,
                minVersion = "0.8.8-rc1",
            )
        ) {
            return Result.Success(null)
        }
        return safeApiCall { endpointTokenApi.getContextProjection(request) }
    }

    override suspend fun clear() {
        tokenConfigMutex.withLock { cachedTokenConfig = null }
        contextOverheads.value = emptyMap()
    }
}
