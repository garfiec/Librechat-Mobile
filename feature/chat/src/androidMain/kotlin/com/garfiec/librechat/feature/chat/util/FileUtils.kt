package com.garfiec.librechat.feature.chat.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.media.detectImageMimeType
import com.garfiec.librechat.core.common.media.imageExtensionForMimeType
import com.garfiec.librechat.core.model.response.ImageResizeLimits
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Resolves a human-readable filename from a content URI using the ContentResolver.
 * Falls back to the last path segment if DISPLAY_NAME is not available.
 */
internal fun resolveFileName(context: Context, uri: Uri): String? {
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                return cursor.getString(nameIndex)
            }
        }
    }
    return uri.lastPathSegment
}

/**
 * Guesses MIME type from file extension. Defaults to application/octet-stream.
 */
internal fun guessMimeType(filename: String): String {
    val extension = filename.substringAfterLast('.', "").lowercase()
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        ?: "application/octet-stream"
}

/**
 * Detects the actual MIME type of image data by inspecting magic bytes (file signature).
 *
 * This is critical because Android's ContentResolver.getType() and file extension-based
 * guessing can return incorrect MIME types. For example, a PNG screenshot might have a
 * .jpg extension, or a WebP image shared from another app may be reported as image/jpeg.
 * When the declared MIME type doesn't match the actual data, AI providers (e.g. Anthropic)
 * reject the image with a 400 error.
 *
 * Returns the detected MIME type for known image formats, or null if the bytes don't
 * match any recognized image signature.
 *
 * Delegates to the canonical [detectImageMimeType] in `core:common` (shared with the media
 * viewer's save/share path) so both surfaces recognize the same format set.
 */
internal fun detectMimeTypeFromBytes(bytes: ByteArray): String? = detectImageMimeType(bytes)

/**
 * Returns the canonical file extension for a given MIME type.
 * Used to ensure the filename extension matches the actual content type.
 */
internal fun extensionForMimeType(mimeType: String): String? = imageExtensionForMimeType(mimeType)

/**
 * Fixes the filename extension to match the detected MIME type.
 *
 * The LibreChat server uses the filename extension (via multer) to determine
 * whether image conversion is needed. If the extension doesn't match the
 * server's configured imageOutputType, the image gets converted but the
 * stored MIME type (from our Content-Type header) does not get updated,
 * causing a mismatch that AI providers reject.
 *
 * By ensuring the extension matches our detected MIME type, we maximize the
 * chance that extension == imageOutputType, avoiding unnecessary conversion.
 */
internal fun fixFilenameExtension(filename: String, mimeType: String): String {
    val expectedExtension = extensionForMimeType(mimeType) ?: return filename

    val dotIndex = filename.lastIndexOf('.')
    val currentExtension = if (dotIndex >= 0) {
        filename.substring(dotIndex + 1).lowercase()
    } else {
        ""
    }

    // Check if current extension already matches (accounting for jpeg/jpg equivalence)
    val normalizedCurrent = when (currentExtension) {
        "jpeg" -> "jpg"
        "tif" -> "tiff"
        else -> currentExtension
    }
    val normalizedExpected = when (expectedExtension) {
        "jpeg" -> "jpg"
        "tif" -> "tiff"
        else -> expectedExtension
    }

    if (normalizedCurrent == normalizedExpected) {
        return filename
    }

    // Replace the extension
    return if (dotIndex >= 0) {
        "${filename.substring(0, dotIndex)}.$expectedExtension"
    } else {
        "$filename.$expectedExtension"
    }
}

/**
 * The bytes, MIME type, and pixel dimensions to upload for an image, after any PNG re-encode /
 * downsample. [reEncoded] is false when the original bytes are used unchanged (already a PNG within
 * budget), true when [bytes]/[mimeType] were produced by re-encoding. [width]/[height] are the
 * decoded pixel dimensions of whatever [bytes] represents — always populated, since a result is
 * only produced once valid bounds are read.
 */
data class ProcessedImage(
    val bytes: ByteArray,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val reEncoded: Boolean,
)

/**
 * Longest-edge cap (px) applied when re-encoding an image. Bitmaps larger than this are
 * downsampled before PNG encoding, which keeps the re-encoded upload from inflating to tens
 * of MB and bounds peak memory (a full-res decode of a modern phone photo is a large ARGB_8888
 * allocation). 2048 preserves ample detail — the server and AI providers resize past this
 * anyway — while keeping the PNG small enough to upload reliably.
 */
private const val MAX_IMAGE_DIMENSION = 2048

/**
 * Floor for the longest edge when downsampling further to fit a size limit: below this the image
 * is too degraded to be useful, so we stop shrinking and let the caller's size pre-check reject it
 * rather than upload something unreadable.
 */
private const val MIN_IMAGE_DIMENSION = 512

/**
 * Prepares an image for upload: re-encodes it to PNG (unless it is already a PNG that needs no
 * change) and reports the bytes, MIME type, and dimensions to send.
 *
 * **Why re-encode to PNG at all.** The LibreChat server has a bug in processAgentFileUpload: it
 * converts uploaded images to its configured imageOutputType (default: PNG) using sharp, but stores
 * the *original* MIME type from the upload Content-Type header. When the AI provider (e.g.
 * Anthropic) later receives the base64 image, it gets converted bytes (e.g. PNG) with the original
 * media_type (e.g. image/jpeg), causing a 400 "Image does not match the provided media type" error.
 * The only reliable client-side fix is to ensure NO conversion happens on the server: the server
 * skips conversion when the file extension matches its imageOutputType, so we always send PNG bytes
 * with a `.png` extension and the server leaves them untouched.
 *
 * **Orientation.** A camera JPEG is usually stored sideways with an EXIF orientation tag, which a
 * re-encode drops; the pixels are rotated upright before encoding, as a browser decodes them.
 *
 * **Client resize ([resize], `fileConfig.clientImageResize`).** When on, a JPEG, PNG or WebP larger
 * than the box is scaled down to fit it, keeping its aspect ratio and never scaling up (upstream
 * `calculateDimensions`). Other formats are left to the rules below, as web leaves them. Web keeps
 * the source format and applies `quality`; this pipeline always writes lossless PNG (above), so
 * `quality` has no effect here.
 *
 * **Sizing.** An image that is already PNG, within [maxEncodedBytes] and within the resize box is
 * uploaded untouched at full resolution. Otherwise the re-encoded bitmap is capped at
 * [MAX_IMAGE_DIMENSION] on its longest edge (a lossless PNG of a 12 MP photo would be tens of MB),
 * and, when [maxEncodedBytes] is given and the PNG still exceeds it, halved each pass until it fits
 * or the longest edge would drop below [MIN_IMAGE_DIMENSION]. If even the floor doesn't fit, the
 * smallest result is returned and the caller's size pre-check rejects it.
 *
 * Returns null only when the bytes cannot be decoded as an image at all (caller should upload the
 * raw bytes, e.g. for a non-image file or a format BitmapFactory can't read).
 */
internal fun processImageForUpload(
    bytes: ByteArray,
    mimeType: String,
    maxEncodedBytes: Long? = null,
    resize: ImageResizeLimits? = null,
): ProcessedImage? {
    return try {
        // Read dimensions first without allocating the full bitmap so we can size the decode.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val srcWidth = bounds.outWidth
        val srcHeight = bounds.outHeight
        if (srcWidth <= 0 || srcHeight <= 0) {
            null // Not a decodable image — caller uploads the raw bytes.
        } else {
            val orientation = readExifOrientation(bytes)
            val swapsAxes = orientation in AXIS_SWAPPING_ORIENTATIONS
            val uprightWidth = if (swapsAxes) srcHeight else srcWidth
            val uprightHeight = if (swapsAxes) srcWidth else srcHeight
            val box = resize?.takeIf { detectMimeTypeFromBytes(bytes) in CLIENT_RESIZABLE_TYPES }
            val fitted = box?.let { fitWithin(uprightWidth, uprightHeight, it.maxWidth, it.maxHeight) }
                ?: (uprightWidth to uprightHeight)
            val needsScale = fitted != (uprightWidth to uprightHeight)
            val alreadyPng = mimeType == "image/png" && detectMimeTypeFromBytes(bytes) == "image/png"
            val fitsLimit = maxEncodedBytes == null || bytes.size <= maxEncodedBytes
            if (alreadyPng && fitsLimit && !needsScale && orientation == ExifInterface.ORIENTATION_NORMAL) {
                ProcessedImage(bytes, mimeType, srcWidth, srcHeight, reEncoded = false)
            } else {
                // The resize box, then the PNG cap on the longest edge.
                val capped = fitWithin(fitted.first, fitted.second, MAX_IMAGE_DIMENSION, MAX_IMAGE_DIMENSION)
                encodeToPngUnderLimit(bytes, mimeType, srcWidth, srcHeight, swapsAxes, orientation, capped, maxEncodedBytes)
            }
        }
    } catch (e: IllegalArgumentException) {
        // BitmapFactory and Bitmap.compress reject what they can't handle with this; bytes that
        // simply aren't an image come back as a null bitmap instead, handled above.
        Logger.w(e) { "processImageForUpload: failed to process $mimeType, using original bytes" }
        null
    } catch (e: OutOfMemoryError) {
        // A large photo's decode plus its scaled copy can exceed a small heap. Losing the re-encode
        // is better than crashing the app: the original bytes go up as they are.
        Logger.w(e) { "processImageForUpload: out of memory processing $mimeType, using original bytes" }
        null
    }
}

/**
 * Upstream `calculateDimensions`: [width] x [height] scaled down to fit [maxWidth] x [maxHeight],
 * aspect kept, never scaled up, each edge at least 1.
 */
internal fun fitWithin(width: Int, height: Int, maxWidth: Int, maxHeight: Int): Pair<Int, Int> {
    if (width <= maxWidth && height <= maxHeight) return width to height
    val factor = minOf(maxWidth.toDouble() / width, maxHeight.toDouble() / height)
    return maxOf(1, kotlin.math.round(width * factor).toInt()) to maxOf(1, kotlin.math.round(height * factor).toInt())
}

/**
 * Decodes [bytes], turns it upright per [orientation], scales it to [target] (upright pixels) and
 * PNG-encodes it, halving the target each pass until the result is within [maxEncodedBytes] (if set)
 * or the longest edge would fall below [MIN_IMAGE_DIMENSION]. Returns the original bytes (unchanged,
 * [ProcessedImage.reEncoded] = false) if the pixels can't be decoded or the PNG can't be verified.
 */
@Suppress("LongParameterList") // one image's facts, read once by the caller
private fun encodeToPngUnderLimit(
    bytes: ByteArray,
    mimeType: String,
    srcWidth: Int,
    srcHeight: Int,
    swapsAxes: Boolean,
    orientation: Int,
    target: Pair<Int, Int>,
    maxEncodedBytes: Long?,
): ProcessedImage {
    var (targetWidth, targetHeight) = target
    while (true) {
        // Decode no smaller than the target (in stored axes), then scale exactly.
        val storedLongest = if (swapsAxes) maxOf(targetHeight, targetWidth) else maxOf(targetWidth, targetHeight)
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeKeepingAtLeast(srcWidth, srcHeight, storedLongest)
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
            // Header decoded (we have valid srcWidth/srcHeight) but the full pixel decode failed —
            // upload the original bytes, but still report the dimensions read from the bounds pass.
            ?: return ProcessedImage(bytes, mimeType, srcWidth, srcHeight, reEncoded = false)
        val uprightDecodedWidth = if (swapsAxes) decoded.height else decoded.width
        val uprightDecodedHeight = if (swapsAxes) decoded.width else decoded.height
        val matrix = Matrix().apply {
            postScale(targetWidth.toFloat() / uprightDecodedWidth, targetHeight.toFloat() / uprightDecodedHeight)
        }
        // Scale in stored axes first (the factors are the same either way), then orient.
        matrix.applyExifOrientation(orientation)
        // Each bitmap is released as soon as the next step has what it needs, and on a failure
        // (an OutOfMemoryError in createBitmap or compress) too, so a retry starts from nothing.
        val upright = try {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        } catch (e: OutOfMemoryError) {
            decoded.recycle()
            throw e
        }
        if (upright !== decoded) decoded.recycle()
        val outWidth = upright.width
        val outHeight = upright.height

        val outputStream = ByteArrayOutputStream()
        try {
            upright.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        } finally {
            upright.recycle()
        }
        val encodedBytes = outputStream.toByteArray()

        // Verify the re-encoded bytes are valid PNG; if not, fall back to the original bytes but
        // keep the source dimensions (so the caller still reports width/height).
        val verifiedType = detectMimeTypeFromBytes(encodedBytes)
        if (verifiedType != "image/png") {
            Logger.w { "encodeToPngUnderLimit: re-encoded bytes are $verifiedType, not image/png; using original" }
            return ProcessedImage(bytes, mimeType, srcWidth, srcHeight, reEncoded = false)
        }

        // Stop when it fits, or when halving again would push the longest edge below the floor —
        // checked against the *next* pass (longestEdge / 2), not the current edge, so we never
        // overshoot and return an image well under MIN_IMAGE_DIMENSION.
        val longestEdge = maxOf(outWidth, outHeight)
        val underLimit = maxEncodedBytes == null || encodedBytes.size <= maxEncodedBytes
        if (underLimit || longestEdge / 2 < MIN_IMAGE_DIMENSION) {
            Logger.d {
                "encodeToPngUnderLimit: $mimeType -> image/png (${bytes.size} -> ${encodedBytes.size} bytes, " +
                    "${outWidth}x$outHeight)"
            }
            return ProcessedImage(encodedBytes, "image/png", outWidth, outHeight, reEncoded = true)
        }

        // Overshot the limit and there's still room to shrink — halve dimensions and retry.
        targetWidth = maxOf(1, targetWidth / 2)
        targetHeight = maxOf(1, targetHeight / 2)
    }
}

/** The largest power-of-two sample size that keeps the decoded longest edge at or above [minLongest]. */
internal fun sampleSizeKeepingAtLeast(srcWidth: Int, srcHeight: Int, minLongest: Int): Int {
    val longest = maxOf(srcWidth, srcHeight)
    if (longest <= 0 || minLongest <= 0) return 1
    var sampleSize = 1
    while (longest / (sampleSize * 2) >= minLongest) sampleSize *= 2
    return sampleSize
}

private fun readExifOrientation(bytes: ByteArray): Int =
    try {
        ExifInterface(ByteArrayInputStream(bytes))
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } catch (e: IOException) {
        Logger.w(e) { "Could not read EXIF orientation; uploading as decoded" }
        ExifInterface.ORIENTATION_NORMAL
    }

private fun Matrix.applyExifOrientation(orientation: Int) {
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            postRotate(90f)
            postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            postRotate(-90f)
            postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(-90f)
    }
}

private val AXIS_SWAPPING_ORIENTATIONS = setOf(
    ExifInterface.ORIENTATION_TRANSPOSE,
    ExifInterface.ORIENTATION_ROTATE_90,
    ExifInterface.ORIENTATION_TRANSVERSE,
    ExifInterface.ORIENTATION_ROTATE_270,
)

/** MIRRORED from upstream `RESIZE_FORMAT_BY_MIME_TYPE` (`client/src/utils/imageResize.ts`). */
private val CLIENT_RESIZABLE_TYPES = setOf("image/jpeg", "image/png", "image/webp")
