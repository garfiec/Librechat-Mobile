package com.garfiec.librechat.feature.chat.util

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
