package com.garfiec.librechat.core.ui.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import co.touchlab.kermit.Logger
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

internal actual fun preparePlatformAvatarImage(bytes: ByteArray): PreparedImage? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    // Sample at decode time so a 50 MP photo never sits in memory at full size.
    val options = BitmapFactory.Options().apply {
        inSampleSize = powerOfTwoSampleSize(bounds.outWidth, bounds.outHeight, AVATAR_MAX_EDGE_PX)
    }
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null

    val hasAlpha = decoded.hasAlpha()
    val maxEdge = if (hasAlpha) AVATAR_MAX_EDGE_PX_WITH_ALPHA else AVATAR_MAX_EDGE_PX
    val (width, height) = fitWithin(decoded.width, decoded.height, maxEdge)
    val matrix = Matrix().apply {
        setScale(width.toFloat() / decoded.width, height.toFloat() / decoded.height)
        applyExifOrientation(readExifOrientation(bytes))
    }
    val upright = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    if (upright !== decoded) decoded.recycle()

    val out = ByteArrayOutputStream()
    if (hasAlpha) {
        upright.compress(Bitmap.CompressFormat.PNG, 100, out)
    } else {
        upright.compress(Bitmap.CompressFormat.JPEG, AVATAR_JPEG_QUALITY, out)
    }
    upright.recycle()
    return if (hasAlpha) PreparedImage.png(out.toByteArray()) else PreparedImage.jpeg(out.toByteArray())
}

/**
 * The largest power of two that keeps the longest edge at or above [maxEdge], so the decode is never
 * smaller than the target and the exact downscale happens afterwards. BitmapFactory only honors powers of two.
 */
internal fun powerOfTwoSampleSize(width: Int, height: Int, maxEdge: Int): Int {
    val longest = maxOf(width, height)
    var sampleSize = 1
    while (longest / (sampleSize * 2) >= maxEdge) {
        sampleSize *= 2
    }
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
