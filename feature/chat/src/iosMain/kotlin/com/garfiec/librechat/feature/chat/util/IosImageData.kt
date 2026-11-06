package com.garfiec.librechat.feature.chat.util

/**
 * Wrapper for image data from iOS sources (clipboard, photo picker, etc.).
 * Passed as the platform reference through [PlatformFileHandler.onFilesSelected].
 */
class IosImageData(
    val bytes: ByteArray,
    val filename: String,
    val mimeType: String,
    val width: Int? = null,
    val height: Int? = null,
    /**
     * A small JPEG or PNG of the image for the draft's thumbnail ([draftPreviewOf]), made where the
     * picker decodes it; null when it couldn't be drawn. Never [bytes] themselves, which may be
     * HEIC the thumbnail can't decode.
     */
    val preview: ByteArray? = null,
)
