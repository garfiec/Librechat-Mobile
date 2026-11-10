package com.garfiec.librechat.core.ui.media

import com.garfiec.librechat.core.common.toByteArray
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageGetAlphaInfo
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import kotlin.math.max
import kotlin.math.roundToInt

private const val PREVIEW_JPEG_QUALITY = 0.8

/**
 * A JPEG (PNG when the image has alpha, which JPEG fills with black) of a picked image, no larger
 * than [maxEdgePx] on its longest edge, for Coil to draw; null when there is nothing to draw. Never
 * show the picked bytes themselves: they are often HEIC, which Coil's Skia decoder on iOS can't
 * read. Run it off Main, where the picker decodes.
 */
@OptIn(ExperimentalForeignApi::class)
fun imagePreviewOf(image: UIImage?, maxEdgePx: Int): ByteArray? {
    if (image == null) return null
    // UIImage.size already accounts for imageOrientation, and drawing renders the pixels upright.
    val (srcWidth, srcHeight) = image.size.useContents { width to height }
    if (srcWidth <= 0.0 || srcHeight <= 0.0) return null
    val ratio = minOf(1.0, maxEdgePx / max(srcWidth, srcHeight))
    val width = max(1, (srcWidth * ratio).roundToInt()).toDouble()
    val height = max(1, (srcHeight * ratio).roundToInt()).toDouble()

    val hasAlpha = when (CGImageGetAlphaInfo(image.CGImage)) {
        CGImageAlphaInfo.kCGImageAlphaNone,
        CGImageAlphaInfo.kCGImageAlphaNoneSkipFirst,
        CGImageAlphaInfo.kCGImageAlphaNoneSkipLast,
        -> false
        else -> true
    }
    val format = UIGraphicsImageRendererFormat().apply {
        scale = 1.0
        opaque = !hasAlpha
    }
    val renderer = UIGraphicsImageRenderer(size = CGSizeMake(width, height), format = format)
    val draw: (Any?) -> Unit = { image.drawInRect(CGRectMake(0.0, 0.0, width, height)) }
    val data = if (hasAlpha) {
        renderer.PNGDataWithActions(draw)
    } else {
        renderer.JPEGDataWithCompressionQuality(PREVIEW_JPEG_QUALITY, draw)
    }
    return data.toByteArray().takeIf { it.isNotEmpty() }
}
