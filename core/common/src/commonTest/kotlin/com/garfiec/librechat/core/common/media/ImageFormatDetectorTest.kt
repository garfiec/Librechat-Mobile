package com.garfiec.librechat.core.common.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The per-format positives live in feature:chat's `DetectMimeTypeTest`; these cover what the table's order decides. */
class ImageFormatDetectorTest {

    @Test
    fun everyHeicBrandIsHeic() {
        for (brand in listOf("heic", "heix", "heim", "heis", "mif1")) {
            assertEquals("image/heic", detectImageMimeType(ftyp(brand)), brand)
        }
    }

    @Test
    fun everyAvifBrandIsAvif() {
        for (brand in listOf("avif", "avis")) {
            assertEquals("image/avif", detectImageMimeType(ftyp(brand)), brand)
        }
    }

    /** A 256-byte ftyp box begins `00 00 01 00`, which is also ICO's signature. */
    @Test
    fun aVideoFtypBoxIsNotMistakenForAnIcon() {
        val mp4 = ftyp("isom", boxSize = byteArrayOf(0x00, 0x00, 0x01, 0x00))
        assertNull(detectImageMimeType(mp4))
    }

    @Test
    fun anIconIsStillAnIcon() {
        val ico = byteArrayOf(0x00, 0x00, 0x01, 0x00) + ByteArray(8)
        assertEquals("image/x-icon", detectImageMimeType(ico))
    }

    @Test
    fun riffWithoutAWebpFormIsNotWebp() {
        val wav = "RIFF".encodeToByteArray() + ByteArray(4) + "WAVE".encodeToByteArray()
        assertNull(detectImageMimeType(wav))
    }

    @Test
    fun elevenBytesOfJpegAreTooShort() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) + ByteArray(8)
        assertNull(detectImageMimeType(jpeg))
        assertEquals("image/jpeg", detectImageMimeType(jpeg + 0.toByte()))
    }

    private fun ftyp(brand: String, boxSize: ByteArray = byteArrayOf(0x00, 0x00, 0x00, 0x18)): ByteArray =
        boxSize + "ftyp".encodeToByteArray() + brand.encodeToByteArray()
}
