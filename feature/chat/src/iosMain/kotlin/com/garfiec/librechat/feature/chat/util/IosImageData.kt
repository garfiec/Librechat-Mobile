package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.ui.media.imagePreviewOf
import platform.UIKit.UIImage

/**
 * Wrapper for image data from iOS sources (clipboard, photo picker, etc.).
 * Passed as the platform reference through [PlatformFileHandler.onFilesSelected].
 */
class IosImageData(
    override val bytes: ByteArray,
    override val filename: String,
    override val mimeType: String,
    val width: Int? = null,
    val height: Int? = null,
    /** The draft thumbnail's image ([draftPreviewOf]); null when it couldn't be drawn. Never [bytes] (often HEIC). */
    val preview: ByteArray? = null,
) : IosPickedData

/** Longest edge of a draft thumbnail, in pixels: about twice the list row's 40pt tile at 2x. */
internal const val DRAFT_PREVIEW_MAX_EDGE_PX = 160

/** The draft strip's thumbnail of a picked image. Run it off Main, where the pickers decode. */
internal fun draftPreviewOf(image: UIImage?): ByteArray? = imagePreviewOf(image, DRAFT_PREVIEW_MAX_EDGE_PX)
