package com.garfiec.librechat.feature.chat.util

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

/** Longest edge of a draft thumbnail, in pixels: about twice the list row's 40pt tile at 2x. */
internal const val DRAFT_PREVIEW_MAX_EDGE_PX = 160

private const val DRAFT_PREVIEW_JPEG_QUALITY = 0.8

/**
 * A small copy of a picked image for the attachment strip ([IosImageData.preview]), in a
 * format the thumbnail's decoder reads. The picked bytes are often HEIC (a library photo shot on
 * "High Efficiency" comes over as its original data, whatever type it is labelled with), and
 * Coil's Skia decoder on iOS reads PNG, JPEG, WebP, GIF and BMP only. JPEG, or PNG when the image
 * has transparency, which JPEG would fill with black. Null when there is nothing to draw; the
 * thumbnail then waits for the server's copy.
 *
 * Run it where the picker already decodes the image, off Main.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun draftPreviewOf(image: UIImage?): ByteArray? {
    if (image == null) return null
    // UIImage.size already accounts for imageOrientation, and drawing renders the pixels upright.
    val (srcWidth, srcHeight) = image.size.useContents { width to height }
    if (srcWidth <= 0.0 || srcHeight <= 0.0) return null
    val ratio = minOf(1.0, DRAFT_PREVIEW_MAX_EDGE_PX / max(srcWidth, srcHeight))
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
        renderer.JPEGDataWithCompressionQuality(DRAFT_PREVIEW_JPEG_QUALITY, draw)
    }
    return data.toByteArray().takeIf { it.isNotEmpty() }
}
