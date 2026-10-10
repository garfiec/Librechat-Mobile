package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.request.ContextProjectionRequest
import com.garfiec.librechat.core.model.usage.ContextUsage
import com.garfiec.librechat.core.model.usage.ModelTokenomics

/**
 * Token/context endpoints for the context-usage gauge (v0.8.7). The token-config map is
 * memoized for the lifetime of a session (it's static per backend), so [clear] must be
 * invoked on logout/server-switch to drop the previous server's config.
 */
interface EndpointTokenRepository {
    /**
     * The per-endpoint, per-model context windows. Memoized for the session; on a network failure
     * it falls back to the copy last fetched from this server, so the gauge's estimate still has a
     * denominator offline.
     */
    suspend fun getTokenConfig(): Result<Map<String, Map<String, ModelTokenomics>>>
    suspend fun getContextProjection(request: ContextProjectionRequest): Result<ContextUsage?>

    /**
     * Records the fixed instruction + tool-schema overhead a live `on_context_usage` reported for
     * one agent/model config ([key], see [contextOverheadKey]). Session-wide, in memory only: a
     * branch with no saved snapshot reuses it so its estimate counts the overhead the client can't
     * otherwise know.
     */
    fun recordContextOverhead(key: String, tokens: Int)

    /** The overhead last recorded for [key], or 0 when that config hasn't run this session. */
    fun contextOverhead(key: String): Int

    /** Drops the cached token-config and overheads so the next caller re-fetches for the new session. */
    suspend fun clear()
}

/**
 * Cache key for [EndpointTokenRepository.recordContextOverhead], built identically by the writer
 * (the live reading) and the reader (the estimate). An agent keys by its id, because its real
 * provider/model only resolves after its detail loads; anything else keys by endpoint and model.
 * Mirrors upstream `overheadKey` (client/src/store/usage.ts).
 */
fun contextOverheadKey(endpoint: String?, model: String?, agentId: String?): String =
    if (!agentId.isNullOrEmpty()) "agent:$agentId" else "${endpoint.orEmpty()}::${model.orEmpty()}"
