package com.garfiec.librechat.core.ui.media

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Turns a picked photo into something the avatar upload routes accept. The server rejects files over
 * `fileConfig.avatarSizeLimit` (2 MB by default), and a camera photo is usually larger. The server
 * also crops to a centered square (150 px; 250 px for a GIF) without applying EXIF orientation, so
 * the image has to be rotated upright before it is sent.
 */
fun interface AvatarImagePreparer {
    /**
     * Returns the image upright and no larger than [AVATAR_MAX_EDGE_PX] on its longest edge.
     * Opaque images are encoded as JPEG; images with transparency are encoded as PNG so the
     * transparency survives, capped at [AVATAR_MAX_EDGE_PX_WITH_ALPHA]. Returns null if [bytes]
     * is not a decodable image.
     */
    fun prepare(bytes: ByteArray): PreparedImage?

    companion object {
        val Platform: AvatarImagePreparer = AvatarImagePreparer(::preparePlatformAvatarImage)
    }
}

class PreparedImage(
    val bytes: ByteArray,
    val mimeType: String,
    val fileName: String,
) {
    companion object {
        fun jpeg(bytes: ByteArray) = PreparedImage(bytes, "image/jpeg", "avatar.jpg")
        fun png(bytes: ByteArray) = PreparedImage(bytes, "image/png", "avatar.png")
    }
}

/** The server keeps at most 250 px; this leaves room for its center crop without approaching 2 MB. */
internal const val AVATAR_MAX_EDGE_PX = 1024
internal const val AVATAR_JPEG_QUALITY = 85

/**
 * The cap for the PNG path. Platforms report an alpha channel, not actual transparency, so an opaque
 * RGBA photo takes this path too; at 1024 px its PNG can pass 2 MB. At 512 px even incompressible
 * RGBA (512 * 512 * 4 = 1 MiB) stays under the limit.
 */
internal const val AVATAR_MAX_EDGE_PX_WITH_ALPHA = 512

internal expect fun preparePlatformAvatarImage(bytes: ByteArray): PreparedImage?

/** Scales [width]×[height] down so neither edge exceeds [maxEdge]. Never scales up. */
internal fun fitWithin(width: Int, height: Int, maxEdge: Int = AVATAR_MAX_EDGE_PX): Pair<Int, Int> {
    val longest = max(width, height)
    if (longest <= maxEdge) return width to height
    val scale = maxEdge.toFloat() / longest
    return max(1, (width * scale).roundToInt()) to max(1, (height * scale).roundToInt())
}
