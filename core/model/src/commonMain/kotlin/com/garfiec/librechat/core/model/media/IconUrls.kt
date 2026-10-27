package com.garfiec.librechat.core.model.media

import com.garfiec.librechat.core.common.EndpointConstants
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
 * Both null leaves the caller's own fallback.
 */
data class IconSource(val endpointName: String?, val imageUrl: String?)

/**
 * Resolves an icon value against the server: an image becomes a loadable URL (a rooted or bare path
 * joins the server, as avatars do; a protocol-relative one takes `https:`), a name becomes the glyph
 * to show. A blank value is unset, as web's `firstPresentIcon` treats `""`, and falls to [endpoint].
 */
fun resolveIconSource(value: String?, endpoint: String?, serverUrl: String): IconSource {
    val icon = value?.takeIf(String::isNotBlank) ?: return IconSource(endpoint, null)
    if (!isImageUrl(icon)) return IconSource(icon, null)
    val url = if (icon.startsWith("//")) "https:$icon" else resolveAvatarUrl(icon, serverUrl)
    return IconSource(endpoint, url)
}

/**
 * The icon value a spec shows, as web's `getModelSpecIconURL` (`client/src/utils/endpoints.ts`,
 * registered as `model-spec-icon-chain`): its own `iconURL`, its preset's, the avatar of the agent
 * it runs (agent specs only), then its endpoint's name unless [withEndpoint] is false. Blank values
 * are skipped.
 */
fun ModelSpec.iconValue(agentAvatar: String? = null, withEndpoint: Boolean = true): String? = listOf(
    iconURL,
    preset?.iconURL,
    agentAvatar.takeIf { preset?.endpoint == EndpointConstants.AGENTS && preset.agentId != null },
    preset?.endpoint?.takeIf { withEndpoint },
).firstOrNull { !it.isNullOrBlank() }
