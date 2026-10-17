package com.garfiec.librechat.core.data.repository

import com.garfiec.librechat.core.common.DetectedBackend
import com.garfiec.librechat.core.common.result.Result
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.config.StartupConfig
import com.garfiec.librechat.core.model.response.Category
import kotlinx.coroutines.flow.StateFlow

/**
 * Result of a backend version compatibility check.
 */
data class VersionCheckResult(
    /** The version detected on the backend, or null if it could not be determined. */
    val backendVersion: String?,
    /**
     * The version this app was built for, as a plain release line for display. Build metadata is
     * stripped, so a partial-sync target (`0.8.7+dev.6c97a7f4`) is published as `0.8.7`; the full
     * pinned string stays in `BackendVersion.SUPPORTED_BACKEND_VERSION` and the Diag record.
     */
    val supportedVersion: String,
    /** Whether the versions are compatible (same major.minor). */
    val isCompatible: Boolean,
)

/** Outcome of [ConfigRepository.resolveServer]. */
sealed interface ServerResolution {
    /** [url] is the candidate that served LibreChat, exactly as it was passed in. */
    data class Resolved(val url: String, val config: StartupConfig) : ServerResolution

    data class Failed(val error: Result.Error) : ServerResolution
}

interface ConfigRepository {
    val startupConfig: StateFlow<StartupConfig?>
    val endpointConfigs: StateFlow<Map<String, EndpointConfig>>
    val availableModels: StateFlow<Map<String, List<String>>>

    /**
     * The backend version detected from `/api/config` (via the `version` field
     * or the build commit). `null` until [checkBackendVersion] runs
     * or if the backend does not expose its version.
     *
     * UI code should consult [com.garfiec.librechat.core.common.BackendVersion.isCompatible]
     * when branching on this value and consult `VERSION_GATES.md` at the repo root
     * when adding new gates.
     */
    val detectedBackendVersion: StateFlow<String?>

    /**
     * The full resolved identity of the detected backend — version plus build classification
     * and build-commit date. Same lifecycle as [detectedBackendVersion] (which stays as the
     * plain-version convenience view). Consult
     * [com.garfiec.librechat.core.common.BackendVersion.supportsFeature] for gates that must
     * also recognize servers built from untagged upstream dev commits (whose reported version
     * understates their features).
     */
    val detectedBackend: StateFlow<DetectedBackend?>

    /**
     * Finds which of [candidates] — base URLs, most specific first — serves LibreChat, by fetching
     * `/api/config` from each in turn with [headers] attached. Publishes nothing and writes no cache:
     * every probe runs under a [com.garfiec.librechat.core.network.client.PendingRequestIdentity] for
     * its candidate, so neither the live server's state nor any account's identity is touched, and
     * the caller stores the winner and then calls [publishStartupConfig].
     *
     * The next candidate is tried only when this one *answered* and was not LibreChat (a 4xx, or a
     * body that isn't LibreChat's config): the host is there, the deployment root isn't. A timeout,
     * an unreachable host, a TLS rejection, an access gateway or a 5xx stops the search, since the
     * same host fails the same way at another path and the user needs that error, not a second wait.
     */
    suspend fun resolveServer(candidates: List<String>, headers: Map<String, String>): ServerResolution

    /**
     * Makes [config] the active server's startup config: in-memory state, its srv:-keyed cache
     * entry, and the config snapshot log. Call after the server URL it came from has been stored,
     * since the cache key derives from the live URL.
     */
    suspend fun publishStartupConfig(config: StartupConfig)

    /**
     * Replaces the in-memory config state with the active server's cached entries (empty when
     * none), leaving every server's disk cache untouched. Called after an account switch: the
     * in-memory StateFlows are server-blind, so once the URL flips they still hold the outgoing
     * server's config until this reseeds them from the incoming server's srv:-keyed cache.
     */
    suspend fun reloadForActiveServer()

    suspend fun fetchStartupConfig(): Result<StartupConfig>
    suspend fun fetchEndpoints(): Result<Map<String, EndpointConfig>>
    suspend fun fetchModels(): Result<Map<String, List<String>>>

    /**
     * Checks the backend version against this app's supported version.
     * Returns a [VersionCheckResult] on success, or an error if the check fails.
     *
     * The check is best-effort: if the backend does not expose its version,
     * the result will have [VersionCheckResult.backendVersion] = null and
     * [VersionCheckResult.isCompatible] = true (fail-open).
     */
    suspend fun checkBackendVersion(): Result<VersionCheckResult>

    /**
     * Fetches the shared category list (`GET /api/categories`) used by prompts and
     * the skill builder — `{label, value}` pairs (idea, code, write, …). `label` is
     * an i18n key; `value` is what's persisted as the skill/prompt category.
     */
    suspend fun getCategories(): Result<List<Category>>

    /** Clear all cached config state (in-memory + disk). Called on logout. */
    suspend fun clear()
}
