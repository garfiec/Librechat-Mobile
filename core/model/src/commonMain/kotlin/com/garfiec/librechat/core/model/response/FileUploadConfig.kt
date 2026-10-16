package com.garfiec.librechat.core.model.response

import com.garfiec.librechat.core.model.config.normalizeEndpointName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The `/api/files/config` response. The route serves the admin's `librechat.yaml` `fileConfig`
 * as written, so every size field is in **megabytes**, possibly fractional (`0.5` = 512 KiB). The
 * web client converts it with data-provider `mergeFileConfig`; the `*Bytes` accessors here are
 * that conversion. `fileLimit` is a count and is not converted.
 *
 * Callers should not read an endpoint's entry directly: which entry governs an endpoint is decided
 * by [resolveEndpointFileConfig].
 */
@Serializable
data class FileUploadConfig(
    val fileLimit: Int? = null,
    @SerialName("fileSizeLimit") val fileSizeLimitMb: Double? = null,
    @SerialName("totalSizeLimit") val totalSizeLimitMb: Double? = null,
    /** Server-wide per-file cap multer enforces on every upload route, regardless of endpoint. */
    @SerialName("serverFileSizeLimit") val serverFileSizeLimitMb: Double? = null,
    val supportedMimeTypes: List<String> = emptyList(),
    val disabled: Boolean = false,
    /**
     * Per-endpoint overrides keyed by endpoint name (plus a `"default"` entry). Absent on older
     * backends — callers fail open.
     */
    val endpoints: Map<String, EndpointFileConfig> = emptyMap(),
) {
    val fileSizeLimitBytes: Long? get() = fileSizeLimitMb?.mbToBytes()
    val serverFileSizeLimitBytes: Long? get() = serverFileSizeLimitMb?.mbToBytes()
}

/**
 * Per-endpoint slice of the file-upload config, as the admin wrote it. Sizes are megabytes on the
 * wire. [disabled] is nullable because an absent flag inherits `"default"`'s.
 */
@Serializable
data class EndpointFileConfig(
    val disabled: Boolean? = null,
    val fileLimit: Int? = null,
    @SerialName("fileSizeLimit") val fileSizeLimitMb: Double? = null,
    @SerialName("totalSizeLimit") val totalSizeLimitMb: Double? = null,
    val supportedMimeTypes: List<String> = emptyList(),
) {
    /** Like `mergeFileConfig`, a disabled endpoint's limits read as 0 whatever the admin wrote. */
    val totalSizeLimitBytes: Long? get() = if (disabled == true) 0L else totalSizeLimitMb?.mbToBytes()
}

/**
 * The file config that governs uploads to one endpoint.
 *
 * @property fileSizeLimitBytes the endpoint's per-file limit; null when only the server's built-in
 *   512 MB applies, 0 when the endpoint is disabled. Not yet capped by `serverFileSizeLimit`; see
 *   [effectiveFileSizeLimit].
 * @property supportedMimeTypes the regex allowlist; empty when the server's built-in list applies.
 */
data class ResolvedEndpointFileConfig(
    val disabled: Boolean,
    val fileSizeLimitBytes: Long?,
    val supportedMimeTypes: List<String>,
)

/**
 * Resolves the config governing [endpoint], mirroring data-provider `getEndpointFileConfig` over a
 * `mergeFileConfig`-merged config. The web client and the server's upload filter both resolve it
 * this way, keyed by name: mobile's upload sends no `endpointType`, and the server drops it for an
 * agent upload, so the server resolves by name too.
 *
 * For a saved agent pass the agent's **provider**, not `agents`: the server validates an agent's
 * upload under its provider (`resolveUploadEndpoint`), and so does the web attach menu.
 *
 * Which entry applies:
 * - **agents, assistants, azureAssistants, anthropic** always have an entry once merged — the base
 *   config ships one for each — so an unset field reads as the built-in value, **never** as the
 *   admin's `"default"`.
 * - **A custom endpoint** (any name outside upstream's standard set) resolves its own entry, then
 *   `"custom"`, then `"agents"`.
 * - **Every other endpoint** resolves its own entry, then `"default"`.
 *
 * A matched entry fills its unset fields from `"default"` (`mergeWithDefault`), except a
 * document-capable provider's unset MIME list, which is the built-in full list. The top-level
 * `fileSizeLimit` / `supportedMimeTypes` / `disabled` are never served by the stock backend and
 * stand in for an absent `"default"` only.
 */
fun FileUploadConfig.resolveEndpointFileConfig(endpoint: String?): ResolvedEndpointFileConfig {
    val adminDefault = endpoints[DEFAULT_ENDPOINT_KEY]
    val default = ResolvedEndpointFileConfig(
        disabled = adminDefault?.disabled ?: disabled,
        fileSizeLimitBytes = adminDefault?.fileSizeLimitMb?.mbToBytes() ?: fileSizeLimitBytes,
        supportedMimeTypes = adminDefault?.supportedMimeTypes?.ifEmpty { null } ?: supportedMimeTypes,
    ).zeroedIfDisabled()

    if (endpoint.isNullOrEmpty()) return default
    val name = normalizeEndpointName(endpoint)

    fun merge(key: String, entry: EndpointFileConfig?, docKey: String) =
        mergeWithDefault(key, entry, default, docKey)

    if (name !in STANDARD_ENDPOINTS) {
        endpoints[endpoint]?.let { return merge(endpoint, it, endpoint) }
        endpoints.entries
            .firstOrNull { (key, _) -> key !in STANDARD_ENDPOINTS && normalizeEndpointName(key) == name }
            ?.let { (key, entry) -> return merge(key, entry, key) }
        endpoints[CUSTOM_ENDPOINT_KEY]?.let { return merge(CUSTOM_ENDPOINT_KEY, it, endpoint) }
        return merge(AGENTS_ENDPOINT_KEY, endpoints[AGENTS_ENDPOINT_KEY], endpoint)
    }
    if (name in endpoints || name in BASE_ENTRY_ENDPOINTS) return merge(name, endpoints[name], name)
    return default
}

/**
 * Resolves the per-file size limit (in **bytes**) for [endpoint] — see [resolveEndpointFileConfig]
 * for which name to pass — capped by [FileUploadConfig.serverFileSizeLimitBytes]: multer rejects
 * anything over it on every upload route (as an opaque 500), whatever the endpoint allows.
 *
 * Returns null when no limit is configured so callers can fail open. A limit of 0 — a disabled
 * endpoint — is returned as-is and not capped; enforcement callers should treat non-positive
 * limits as "no check" since disabled uploads are gated elsewhere.
 */
fun FileUploadConfig.effectiveFileSizeLimit(endpoint: String?): Long? {
    val perFile = resolveEndpointFileConfig(endpoint).fileSizeLimitBytes
    val serverCap = serverFileSizeLimitBytes?.takeIf { it > 0 }
    return when {
        perFile == null -> serverCap
        serverCap == null || perFile <= 0 -> perFile
        else -> minOf(perFile, serverCap)
    }
}

/**
 * data-provider `mergeWithDefault` over the merged entry for [key]. A base-entry endpoint's merged
 * entry already defines every field, so nothing it leaves unset is taken from [default].
 */
private fun mergeWithDefault(
    key: String,
    entry: EndpointFileConfig?,
    default: ResolvedEndpointFileConfig,
    docKey: String,
): ResolvedEndpointFileConfig {
    val isBase = key in BASE_ENTRY_ENDPOINTS
    val ownMimeTypes = entry?.supportedMimeTypes?.ifEmpty { null }
    return ResolvedEndpointFileConfig(
        disabled = entry?.disabled ?: if (isBase) false else default.disabled,
        fileSizeLimitBytes = entry?.fileSizeLimitMb?.mbToBytes()
            ?: if (isBase) null else default.fileSizeLimitBytes,
        supportedMimeTypes = ownMimeTypes ?: when {
            isBase || isDocumentSupportedProvider(docKey) -> emptyList()
            else -> default.supportedMimeTypes
        },
    ).zeroedIfDisabled()
}

private fun ResolvedEndpointFileConfig.zeroedIfDisabled() =
    if (disabled) copy(fileSizeLimitBytes = 0L) else this

private const val AGENTS_ENDPOINT_KEY = "agents"
private const val CUSTOM_ENDPOINT_KEY = "custom"

/** data-provider base `fileConfig.endpoints` entries (besides `"default"`), each with every field set. */
private val BASE_ENTRY_ENDPOINTS = setOf(AGENTS_ENDPOINT_KEY, "assistants", "azureAssistants", "anthropic")

/** `getEndpointFileConfig`'s `standardEndpoints`: any other name resolves as a custom endpoint. */
private val STANDARD_ENDPOINTS = BASE_ENTRY_ENDPOINTS +
    setOf(DEFAULT_ENDPOINT_KEY, "openAI", "azureOpenAI", "google", "bedrock")

/** data-provider `mbToBytes`. */
private fun Double.mbToBytes(): Long = (this * BYTES_PER_MB).toLong()

private const val BYTES_PER_MB = 1024L * 1024L

/** Catch-all key in [FileUploadConfig.endpoints] the server uses for endpoints without an override. */
internal const val DEFAULT_ENDPOINT_KEY = "default"
