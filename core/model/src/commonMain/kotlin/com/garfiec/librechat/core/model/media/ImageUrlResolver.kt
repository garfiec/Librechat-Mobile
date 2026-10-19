package com.garfiec.librechat.core.model.media

import com.garfiec.librechat.core.common.identity.cleanServerUrl
import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.FileReference
import com.garfiec.librechat.core.model.content.MessageContentPart
import com.garfiec.librechat.core.model.files.CODE_DOWNLOAD_ROUTE
import com.garfiec.librechat.core.model.files.codeDownloadUrl

/**
 * Single source of truth for turning a message's image reference into a loadable URL.
 *
 * Every image surface must route through these rather than keep a per-call-site copy: the renderers
 * and the background prefetcher have to agree on the exact URL, or a warmed cache entry is a
 * near-miss under a slightly different one rather than a hit.
 */

/** Resolves a full URL for an attached [FileReference], handling relative paths. */
fun resolveFileReferenceUrl(file: FileReference, baseUrl: String): String? =
    resolveImageUrl(file.filepath, file.fileId, baseUrl, includeBareSlash = true)

/**
 * Resolves a full URL for an inline `image_file` content part.
 *
 * [includeBareSlash] is `false` here: the content-part form maps a bare absolute path (e.g.
 * `/foo/x.png`, not `/images/...`) through `/api/files/`, matching how `ContentPartRenderer` has
 * always rendered these. A [FileReference] instead serves it directly off `baseUrl`.
 */
fun resolveImageFilePartUrl(part: MessageContentPart, baseUrl: String): String? =
    resolveImageUrl(part.imageFile?.filepath, part.imageFile?.fileId, baseUrl, includeBareSlash = false)

/**
 * Resolves a full URL for an image-gen tool-call [Attachment].
 *
 * Differs from the content-part forms only in the relative-path fallback: a bare relative path is
 * served directly off `baseUrl` ([relativePathPrefix] = `/`), not through `/api/files/`, matching
 * how `parseImageGenResult` has always built generated-image URLs.
 */
fun resolveAttachmentUrl(attachment: Attachment?, baseUrl: String): String? {
    if (attachment == null) return null
    return resolveImageUrl(
        attachment.filepath,
        attachment.fileId,
        baseUrl,
        includeBareSlash = true,
        relativePathPrefix = "/",
    )
}

/**
 * Resolves a full URL for a user or account avatar.
 *
 * Avatars are served off the deployment root (`${baseUrl}/images/…`), not through `/api/files/`,
 * so a bare relative path joins with `/`. A social-login avatar arrives as an absolute URL on the
 * provider's host and is returned untouched.
 *
 * The base is cleaned like every other surface here, so a server entered as `https://host/` builds
 * `https://host/images/…` — the same cache key every other image surface derives for the same
 * bytes, not `https://host//images/…`.
 */
fun resolveAvatarUrl(avatar: String?, baseUrl: String): String? =
    resolveImageUrl(
        filepath = avatar?.takeIf { it.isNotBlank() },
        fileId = null,
        baseUrl = baseUrl,
        includeBareSlash = true,
        relativePathPrefix = "/",
    )

/**
 * Maps a [filepath]/[fileId] pair to a loadable URL. The two flags capture the only differences
 * between the call sites, so the branches can't silently drift apart:
 *  - [includeBareSlash]: route any bare absolute path (`/foo`) through `baseUrl` directly.
 *  - [relativePathPrefix]: how a bare relative path joins `baseUrl` (`/api/files/` vs `/`).
 */
private fun resolveImageUrl(
    filepath: String?,
    fileId: String?,
    baseUrl: String,
    includeBareSlash: Boolean,
    relativePathPrefix: String = "/api/files/",
): String? {
    // Byte-identical for a clean base: these URLs are cache keys shared with the prefetcher.
    @Suppress("NAME_SHADOWING")
    val baseUrl = cleanServerUrl(baseUrl)
    if (filepath != null) {
        return when {
            filepath.startsWith("http") -> filepath
            // Defence in depth, not a live path: tools that build an image artifact do hand back
            // `data:image/png;base64,…` inline, but the agent callback runs `saveBase64Image` over
            // every `image_url` part before the attachment is emitted, so what reaches the client
            // is a stored `/images/…` path. Kept because a data URI is already complete and the
            // relative-path case below would glue a server origin in front of it; upstream's own
            // `Image.tsx` carries the same guard.
            filepath.startsWith(DATA_URI_PREFIX) -> filepath
            // Already carries the base path; joined to the cleaned base it would double it.
            filepath.contains(CODE_DOWNLOAD_ROUTE) && baseUrl.isNotBlank() -> codeDownloadUrl(filepath, baseUrl)
            filepath.startsWith("/images/") && baseUrl.isNotBlank() -> "$baseUrl$filepath"
            includeBareSlash && filepath.startsWith("/") && baseUrl.isNotBlank() -> "$baseUrl$filepath"
            baseUrl.isNotBlank() -> "$baseUrl$relativePathPrefix$filepath"
            else -> filepath
        }
    }
    if (fileId != null && baseUrl.isNotBlank()) {
        return "$baseUrl/api/files/$fileId"
    }
    return null
}

/** Inline-bytes scheme. Such a reference is already complete and must never be joined to a host. */
private const val DATA_URI_PREFIX = "data:"
