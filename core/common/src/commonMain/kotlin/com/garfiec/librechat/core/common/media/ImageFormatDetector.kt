package com.garfiec.librechat.core.common.media

/**
 * Detects an image's MIME type from its magic bytes (file signature).
 *
 * File extensions and declared content types lie; the bytes don't. This is the single canonical
 * sniffer shared by image upload (`feature:chat`) and the media viewer's save/share
 * (`core:ui`), so both recognize the same format set and a saved/shared file always gets the
 * right extension + MIME.
 *
 * Supported binary formats: JPEG, PNG, GIF, WebP, BMP, TIFF, HEIF/HEIC, AVIF, ICO.
 * (Text-based formats like SVG are not magic-byte detectable and are handled by the caller.)
 *
 * @return the detected MIME type, or `null` if the bytes match no recognized image signature.
 */
fun detectImageMimeType(bytes: ByteArray): String? =
    if (bytes.size < MIN_SIGNATURE_BYTES) null else SIGNATURES.firstOrNull { it.matches(bytes) }?.mimeTypeOf(bytes)

/** Every signature below reads within the first 12 bytes. */
private const val MIN_SIGNATURE_BYTES = 12

private class Magic(val offset: Int, val bytes: ByteArray) {
    fun matches(data: ByteArray): Boolean = bytes.indices.all { data[offset + it] == bytes[it] }
}

private class Signature(val magics: List<Magic>, val mimeTypeOf: (ByteArray) -> String?) {
    fun matches(data: ByteArray): Boolean = magics.all { it.matches(data) }
}

private fun at(offset: Int, vararg bytes: Int) = Magic(offset, ByteArray(bytes.size) { bytes[it].toByte() })

private fun at(offset: Int, ascii: String) = Magic(offset, ascii.encodeToByteArray())

private fun format(mimeType: String, vararg magics: Magic) = Signature(magics.toList()) { mimeType }

private val HEIC_BRANDS = setOf("heic", "heix", "heim", "heis", "mif1")
private val AVIF_BRANDS = setOf("avif", "avis")

/**
 * First match wins, so order matters: an ISO-BMFF `ftyp` box is claimed before ICO, and one with
 * a brand that is not an image (MP4, QuickTime) is `null` rather than falling through — a box of
 * exactly 256 bytes begins `00 00 01 00`, which is ICO's signature.
 */
private val SIGNATURES = listOf(
    format("image/jpeg", at(0, 0xFF, 0xD8, 0xFF)),
    format("image/png", at(0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)),
    format("image/gif", at(0, "GIF87a")),
    format("image/gif", at(0, "GIF89a")),
    format("image/webp", at(0, "RIFF"), at(8, "WEBP")),
    format("image/bmp", at(0, "BM")),
    format("image/tiff", at(0, 0x49, 0x49, 0x2A, 0x00)), // "II", little-endian 42
    format("image/tiff", at(0, 0x4D, 0x4D, 0x00, 0x2A)), // "MM", big-endian 42
    Signature(listOf(at(4, "ftyp"))) { bytes ->
        when (bytes.copyOfRange(8, 12).decodeToString()) {
            in HEIC_BRANDS -> "image/heic"
            in AVIF_BRANDS -> "image/avif"
            else -> null
        }
    },
    format("image/x-icon", at(0, 0x00, 0x00, 0x01, 0x00)),
)

/**
 * The canonical file extension (no leading dot) for an image MIME type, or `null` if unknown.
 *
 * Covers a few MIME types [detectImageMimeType] never returns itself (e.g. `image/heif`,
 * `image/svg+xml`) because callers also pass server- or OS-declared MIME types here, not only
 * magic-byte-sniffed ones.
 */
fun imageExtensionForMimeType(mimeType: String): String? = when (mimeType) {
    "image/jpeg" -> "jpg"
    "image/png" -> "png"
    "image/gif" -> "gif"
    "image/webp" -> "webp"
    "image/bmp" -> "bmp"
    "image/tiff" -> "tiff"
    "image/heic" -> "heic"
    "image/heif" -> "heif"
    "image/avif" -> "avif"
    "image/x-icon" -> "ico"
    "image/svg+xml" -> "svg"
    else -> null
}
