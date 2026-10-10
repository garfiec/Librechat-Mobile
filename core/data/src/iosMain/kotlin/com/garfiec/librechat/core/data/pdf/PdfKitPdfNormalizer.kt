package com.garfiec.librechat.core.data.pdf

import com.garfiec.librechat.core.common.toByteArray
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create
import platform.PDFKit.PDFDocument
import platform.PDFKit.PDFPage

/**
 * Copies the unlocked pages into a fresh [PDFDocument] and writes that.
 *
 * Writing the unlocked original is NOT enough: `dataRepresentation()` re-applies an RC4 security
 * handler on the way out (AES-256 happens to be dropped), and RC4 is what the copy-protected
 * publisher PDFs this exists for use. A new document has no security handler to carry over.
 *
 * PDFKit writes through CoreGraphics, so this re-emits page content rather than copying objects:
 * text and fonts survive (fonts come back as embedded subsets), while document-level structure
 * such as tags or the outline may not. That is still a text PDF, never a page image.
 */
class PdfKitPdfNormalizer : PdfNormalizer {

    @Suppress("TooGenericExceptionCaught")
    override fun normalize(bytes: ByteArray, password: String?): PdfNormalizeResult = try {
        // A failable initializer: Kotlin/Native turns its nil (an unparseable file) into an NPE.
        val document = runCatching { PDFDocument(data = bytes.toNSData()) }.getOrNull()
        when {
            document == null -> PdfNormalizeResult.Failed(null)
            !document.isEncrypted() -> PdfNormalizeResult.Unchanged
            // A copy-protected file opens unlocked; only a real password leaves it locked.
            document.isLocked() && !document.unlockWithPassword(password ?: "") -> PdfNormalizeResult.NeedsPassword
            else -> {
                val fresh = PDFDocument()
                for (index in 0 until document.pageCount.toLong()) {
                    val page = document.pageAtIndex(index.toULong())?.copy() as? PDFPage
                        ?: return PdfNormalizeResult.Failed(IllegalStateException("page $index unreadable"))
                    fresh.insertPage(page, atIndex = index.toULong())
                }
                fresh.dataRepresentation()?.toByteArray()?.let { PdfNormalizeResult.Decrypted(it) }
                    ?: PdfNormalizeResult.Failed(IllegalStateException("PDFKit wrote no data"))
            }
        }
    } catch (e: Exception) {
        PdfNormalizeResult.Failed(e)
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData()
    return usePinned { pinned -> NSData.create(bytes = pinned.addressOf(0), length = size.toULong()) }
}
