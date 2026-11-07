package com.garfiec.librechat.feature.chat.components

import android.content.ClipDescription
import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.content.MediaType
import androidx.compose.foundation.content.ReceiveContentListener
import androidx.compose.foundation.content.TransferableContent
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.content.hasMediaType
import androidx.compose.ui.Modifier
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Takes images pasted (menu, Ctrl+V), dropped or inserted by the keyboard (stickers, Emoji Kitchen,
 * GIFs, Gboard's copied-image chip) into a `TextFieldState` text field, and hands copies to
 * [onImages]. Everything else in the clip, such as text, is left for the field to insert.
 *
 * The source URI's read grant doesn't outlast the clip, while an attachment is read again later
 * (routing sheet, chip thumbnail, retry), so each image is copied into the cache first. [scope]
 * must outlive the composer.
 */
@OptIn(ExperimentalFoundationApi::class)
internal fun Modifier.imageContentReceiver(
    context: Context,
    scope: CoroutineScope,
    onImages: (List<Any>) -> Unit,
): Modifier = contentReceiver(ImageContentListener(context.applicationContext, scope, onImages))

@OptIn(ExperimentalFoundationApi::class)
private class ImageContentListener(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onImages: (List<Any>) -> Unit,
) : ReceiveContentListener {

    // A keyboard's read grant is revoked once Compose's record of the commit is collected, so the
    // content is held here until its images are copied.
    private val inFlight = mutableSetOf<TransferableContent>()

    override fun onReceive(transferableContent: TransferableContent): TransferableContent? {
        if (!transferableContent.hasMediaType(MediaType.Image)) return transferableContent
        val description = transferableContent.clipMetadata.clipDescription
        val images = mutableListOf<Pair<Uri, String>>()
        val remaining = transferableContent.consume { item ->
            val uri = item.uri ?: return@consume false
            val mimeType = imageType(uri, description) ?: return@consume false
            images += uri to mimeType
            true
        }
        if (images.isNotEmpty()) {
            inFlight += transferableContent
            scope.launch {
                val copies = try {
                    withContext(Dispatchers.IO) { images.mapNotNull { (uri, type) -> copyToCache(context, uri, type) } }
                } finally {
                    inFlight -= transferableContent
                }
                if (copies.isNotEmpty()) onImages(copies)
            }
        }
        return remaining
    }

    private fun imageType(uri: Uri, description: ClipDescription): String? {
        val resolved = try {
            context.contentResolver.getType(uri)
        } catch (e: SecurityException) {
            Logger.w(e) { "Received image: type lookup refused" }
            null
        }
        return RECEIVED_IMAGE_MIME_TYPES.firstOrNull { it == resolved }
            ?: RECEIVED_IMAGE_MIME_TYPES.firstOrNull { resolved == null && description.hasMimeType(it) }
    }
}

private fun copyToCache(context: Context, source: Uri, mimeType: String): Uri? {
    val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "png"
    val dir = File(context.cacheDir, RECEIVED_IMAGES_DIR).apply { mkdirs() }
    var file: File? = null
    return try {
        val target = File.createTempFile("keyboard_", ".$extension", dir).also { file = it }
        val copied = context.contentResolver.openInputStream(source)?.use { input ->
            target.outputStream().use { input.copyTo(it) }
        }
        if (copied == null) {
            target.delete()
            null
        } else {
            Uri.fromFile(target)
        }
    } catch (e: IOException) {
        Logger.w(e) { "Received image: copy failed" }
        file?.delete()
        null
    } catch (e: SecurityException) {
        Logger.w(e) { "Received image: read refused" }
        file?.delete()
        null
    }
}

/** Image types the composer takes, each named exactly. */
private val RECEIVED_IMAGE_MIME_TYPES = arrayOf("image/png", "image/gif", "image/webp", "image/jpeg")

/** Cleared on logout with the other session caches (`CACHE_SUBDIRECTORIES`). */
private const val RECEIVED_IMAGES_DIR = "keyboard_images"
