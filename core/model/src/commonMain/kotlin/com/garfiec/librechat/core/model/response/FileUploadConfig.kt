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
    /** The admin's `fileConfig.clientImageResize` (v0.8.9-rc1); see [clientImageResize]. */
    val clientImageResize: ClientImageResizeConfig? = null,
    /**
     * `fileConfig.legacyFileUploadUX`: the deployment keeps web's destination chooser (upload to the
     * provider, as text, to file search or to the code environment) instead of routing by file.
     * An endpoint entry overrides it; see [ResolvedEndpointFileConfig.legacyFileUploadUX].
     */
    val legacyFileUploadUX: Boolean? = null,
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
    val legacyFileUploadUX: Boolean? = null,
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
 * @property legacyFileUploadUX the matched entry's flag, else `"default"`'s, else the top-level one
 *   (`mergeWithDefault`, and `getEndpointFileConfig`'s global default); off when none is set.
 */
data class ResolvedEndpointFileConfig(
    val disabled: Boolean,
    val fileSizeLimitBytes: Long?,
    val supportedMimeTypes: List<String>,
    val legacyFileUploadUX: Boolean = false,
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
        legacyFileUploadUX = (adminDefault?.legacyFileUploadUX ?: legacyFileUploadUX) == true,
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
        // Inherited by every entry, base entries included: upstream's base config sets none.
        legacyFileUploadUX = entry?.legacyFileUploadUX ?: default.legacyFileUploadUX,
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

/**
 * `fileConfig.clientImageResize` as the admin wrote it (upstream `file-config.ts`). Any field may be
 * absent; [enabled] being present at all is what makes the choice the admin's ([ClientImageResize.enforced]).
 */
@Serializable
data class ClientImageResizeConfig(
    val enabled: Boolean? = null,
    val maxWidth: Double? = null,
    val maxHeight: Double? = null,
    val quality: Double? = null,
)

/** The box an image is scaled down to fit (never up) before upload, and the encoder quality (0..1). */
data class ImageResizeLimits(val maxWidth: Int, val maxHeight: Int, val quality: Double)

/**
 * Whether images are resized before upload and to what. [enforced]: the admin set `enabled`, so the
 * user's setting is ignored (and shown locked). [limits] is null when nothing is resized.
 */
data class ClientImageResize(val enforced: Boolean, val enabledByAdmin: Boolean?, val limits: ImageResizeLimits?) {
    val isEnabled: Boolean get() = limits != null
}

/**
 * Upstream `useClientResize` + `mergeFileConfig`: admin config, then the user's setting, then off.
 * Off until the file config is known (a null receiver), as web waits for it. Dimensions and quality
 * fall back to upstream's defaults (1900 x 1900, 0.92); a non-positive dimension turns it off.
 */
fun FileUploadConfig?.clientImageResize(userPreference: Boolean): ClientImageResize {
    val admin = this?.clientImageResize
    val enforced = admin?.enabled != null
    if (this == null) return ClientImageResize(enforced = false, enabledByAdmin = null, limits = null)
    val maxWidth = admin?.maxWidth ?: CLIENT_RESIZE_DEFAULT_MAX_EDGE
    val maxHeight = admin?.maxHeight ?: CLIENT_RESIZE_DEFAULT_MAX_EDGE
    val quality = (admin?.quality ?: CLIENT_RESIZE_DEFAULT_QUALITY).coerceIn(0.0, 1.0)
    val validDimensions = maxWidth.isFinite() && maxHeight.isFinite() && maxWidth >= 1 && maxHeight >= 1
    val on = validDimensions && if (enforced) admin.enabled == true else userPreference
    return ClientImageResize(
        enforced = enforced,
        enabledByAdmin = admin?.enabled,
        limits = if (on) ImageResizeLimits(maxWidth.toInt(), maxHeight.toInt(), quality) else null,
    )
}

/** MIRRORED from upstream `defaultFileConfig.clientImageResize` (`file-config.ts`). */
const val CLIENT_RESIZE_DEFAULT_MAX_EDGE: Double = 1900.0
const val CLIENT_RESIZE_DEFAULT_QUALITY: Double = 0.92
