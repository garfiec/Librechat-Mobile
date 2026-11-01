package com.garfiec.librechat.core.model.media

import com.garfiec.librechat.core.common.EndpointConstants
import com.garfiec.librechat.core.model.EndpointConfig
import com.garfiec.librechat.core.model.config.ModelSpec

/**
 * Whether an `iconURL` names an image rather than an endpoint or provider whose glyph to borrow.
 *
 * MIRRORED from web's `isImageURL` (`client/src/utils/icons.ts`, registered in `scripts/mirrors.json`
 * as `icon-url-image-rule`): an absolute or protocol-relative URL, a `data:image/` URI, a rooted path,
 * or a bare file name with an image extension. Anything else (`openAI`, `groq`) is a name.
 */
fun isImageUrl(value: String?): Boolean {
    if (value.isNullOrBlank()) return false
    if (ABSOLUTE.containsMatchIn(value)) return true
    if (value.startsWith("//")) return value.length > 2 && value[2] != '/'
    return value.startsWith("/") || IMAGE_FILE.containsMatchIn(value)
}

private val ABSOLUTE = Regex("""^(https?://|data:image/[a-z0-9.+-]+)""", RegexOption.IGNORE_CASE)

private val IMAGE_FILE = Regex(
    """\.(apng|avif|bmp|cur|gif|ico|jfif|jpe?g|pjp|pjpeg|png|svg|webp)([?#].*)?$""",
    RegexOption.IGNORE_CASE,
)

/**
 * What to draw for an icon value: an image to load, or the endpoint whose glyph to show.
 * [provider], when set, is the brand art to draw instead of [endpointName]'s glyph, and what an image
 * that fails to load falls back to. All null leaves the caller's own fallback.
 */
data class IconSource(val endpointName: String?, val imageUrl: String?, val provider: ProviderId? = null)

/**
 * Resolves an icon value against the server: an image becomes a loadable URL (a rooted or bare path
 * joins the server, as avatars do; a protocol-relative one takes `https:`), a name becomes the glyph
 * to show. A blank value is unset, as web's `firstPresentIcon` treats `""`, and falls to [endpoint].
 *
 * The [IconSource.provider] follows web's `SpecIcon`: a value naming a brand (`groq`), else
 * [endpoint]'s brand ([endpointBrand]: its configured brand `iconURL`, served `providerId`, first-class
 * brand, then name). A value equal to [endpoint] counts as the endpoint's name, not a value of its own.
 * For an image, [endpoint]'s brand alone. Without [endpointConfigs] the served `providerId` is skipped.
 */
fun resolveIconSource(
    value: String?,
    endpoint: String?,
    serverUrl: String,
    endpointConfigs: Map<String, EndpointConfig>? = null,
): IconSource {
    val icon = value?.takeIf(String::isNotBlank)
    if (icon == null || !isImageUrl(icon)) {
        // A value that is just the endpoint's own name (iconValue's last fallback) is no icon of its
        // own: it must not outrank the served providerId, which the server resolves ahead of the name.
        val named = icon?.takeUnless { it == endpoint }?.let(::resolveProviderId)
        return IconSource(icon ?: endpoint, null, named ?: endpointBrand(endpoint, endpointConfigs))
    }
    val url = if (icon.startsWith("//")) "https:$icon" else resolveAvatarUrl(icon, serverUrl)
    return IconSource(endpoint, url, endpointBrand(endpoint, endpointConfigs))
}

/**
 * The icon value a spec shows, as web's `getModelSpecIconURL` (`client/src/utils/endpoints.ts`,
 * registered as `model-spec-icon-chain`): its own `iconURL`, its preset's, the avatar of the agent
 * it runs (agent specs only), then its endpoint's name. Blank values are skipped.
 */
fun ModelSpec.iconValue(agentAvatar: String? = null): String? = listOf(
    iconURL,
    preset?.iconURL,
    agentAvatar.takeIf { preset?.endpoint == EndpointConstants.AGENTS && preset.agentId != null },
    preset?.endpoint,
).firstOrNull { !it.isNullOrBlank() }
