package com.garfiec.librechat.feature.chat.util

import com.garfiec.librechat.core.common.toByteArray
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.UIKit.UIPasteboard

/**
 * Checks whether the system clipboard currently contains an image.
 */
fun clipboardHasImage(): Boolean {
    return UIPasteboard.generalPasteboard.hasImages
}

/**
 * Reads the first image from the system clipboard and returns it as [IosImageData],
 * or null if no image is available.
 *
 * The image is converted to PNG format for consistent server-side handling. The
 * pasteboard handle is grabbed on the calling (Main) thread, but the PNG encode —
 * which is heavy for a full-resolution screenshot — runs on [Dispatchers.Default]
 * so it never blocks the UI click handler.
 */
@OptIn(ExperimentalForeignApi::class)
suspend fun readClipboardImage(): IosImageData? {
    val image: UIImage = UIPasteboard.generalPasteboard.image ?: return null

    return withContext(Dispatchers.Default) {
        val pngData: NSData = UIImagePNGRepresentation(image) ?: return@withContext null
        val bytes = pngData.toByteArray()
        if (bytes.isEmpty()) return@withContext null

        val cgImage = image.CGImage
        val width = if (cgImage != null) CGImageGetWidth(cgImage).toInt() else null
        val height = if (cgImage != null) CGImageGetHeight(cgImage).toInt() else null

        val timestamp = NSDate().timeIntervalSince1970.toLong()

        IosImageData(
            bytes = bytes,
            filename = "clipboard_$timestamp.png",
            mimeType = "image/png",
            width = width,
            height = height,
        )
    }
}
