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

@OptIn(ExperimentalForeignApi::class)
internal actual fun preparePlatformAvatarImage(bytes: ByteArray): PreparedImage? {
    val image = UIImage.imageWithData(bytes.toNSData()) ?: return null
    // UIImage.size already accounts for imageOrientation, and drawing renders the pixels upright.
    val (srcWidth, srcHeight) = image.size.useContents { width.toInt() to height.toInt() }
    if (srcWidth <= 0 || srcHeight <= 0) return null

    val hasAlpha = when (CGImageGetAlphaInfo(image.CGImage)) {
        CGImageAlphaInfo.kCGImageAlphaNone,
        CGImageAlphaInfo.kCGImageAlphaNoneSkipFirst,
        CGImageAlphaInfo.kCGImageAlphaNoneSkipLast,
        -> false
        else -> true
    }
    val maxEdge = if (hasAlpha) AVATAR_MAX_EDGE_PX_WITH_ALPHA else AVATAR_MAX_EDGE_PX
    val (width, height) = fitWithin(srcWidth, srcHeight, maxEdge)
    val format = UIGraphicsImageRendererFormat().apply {
        scale = 1.0
        opaque = !hasAlpha
    }
    val renderer = UIGraphicsImageRenderer(size = CGSizeMake(width.toDouble(), height.toDouble()), format = format)
    val draw: (Any?) -> Unit = { image.drawInRect(CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble())) }
    return if (hasAlpha) {
        PreparedImage.png(renderer.PNGDataWithActions(draw).toByteArray())
    } else {
        PreparedImage.jpeg(renderer.JPEGDataWithCompressionQuality(AVATAR_JPEG_QUALITY / 100.0, draw).toByteArray())
    }
}
