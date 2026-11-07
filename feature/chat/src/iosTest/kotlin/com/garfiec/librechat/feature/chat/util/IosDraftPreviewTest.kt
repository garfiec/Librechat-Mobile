package com.garfiec.librechat.feature.chat.util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIColor
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIRectFill
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A picked image's draft thumbnail is a small JPEG or PNG, whatever format the pick came in. */
@OptIn(ExperimentalForeignApi::class)
class IosDraftPreviewTest {

    private fun image(width: Double, height: Double, opaque: Boolean): UIImage {
        val format = UIGraphicsImageRendererFormat().apply {
            scale = 1.0
            this.opaque = opaque
        }
        return UIGraphicsImageRenderer(size = CGSizeMake(width, height), format = format).imageWithActions {
            UIColor.redColor.setFill()
            UIRectFill(CGRectMake(0.0, 0.0, width / 2, height / 2))
        }
    }

    private fun decoded(bytes: ByteArray): UIImage? = bytes.usePinned {
        UIImage(data = NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()))
    }

    @Test
    fun anOpaquePickBecomesASmallJpeg() {
        val preview = assertNotNull(draftPreviewOf(image(width = 4032.0, height = 3024.0, opaque = true)))

        // JPEG's start-of-image marker.
        assertEquals(0xFF.toByte(), preview[0])
        assertEquals(0xD8.toByte(), preview[1])
        val (width, height) = assertNotNull(decoded(preview)).size.useContents { width to height }
        assertEquals(DRAFT_PREVIEW_MAX_EDGE_PX.toDouble(), width)
        assertEquals(120.0, height)
    }

    @Test
    fun aTransparentPickKeepsItsAlphaAsPng() {
        val preview = assertNotNull(draftPreviewOf(image(width = 64.0, height = 64.0, opaque = false)))

        // PNG's signature, and a small image is never scaled up.
        assertEquals(0x89.toByte(), preview[0])
        assertEquals('P'.code.toByte(), preview[1])
        val width = assertNotNull(decoded(preview)).size.useContents { width }
        assertTrue(width <= 64.0)
    }

    @Test
    fun nothingToDrawGivesNoPreview() {
        assertNull(draftPreviewOf(null))
    }
}
