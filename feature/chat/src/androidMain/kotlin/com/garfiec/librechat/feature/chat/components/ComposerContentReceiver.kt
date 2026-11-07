package com.garfiec.librechat.feature.chat.components

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.content.ReceiveContentListener
import androidx.compose.foundation.content.TransferableContent
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.core.view.inputmethod.EditorInfoCompat
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

/**
 * Offers the keyboard of the text field in [content] exactly the image types the composer takes.
 * Under [imageContentReceiver] Compose offers any type, video included, so a keyboard may send a GIF
 * as video (refused) or, matching types exactly, offer no stickers. A field without a receiver is
 * left offering none.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun OfferImageTypesToKeyboard(content: @Composable () -> Unit) {
    val interceptor = remember {
        PlatformTextInputInterceptor { request, nextHandler ->
            nextHandler.startInputMethod(
                PlatformTextInputMethodRequest { outAttributes ->
                    request.createInputConnection(outAttributes).also {
                        if (EditorInfoCompat.getContentMimeTypes(outAttributes).isNotEmpty()) {
                            EditorInfoCompat.setContentMimeTypes(outAttributes, RECEIVED_IMAGE_MIME_TYPES)
                        }
                    }
                },
            )
        }
    }
    InterceptPlatformTextInput(interceptor, content)
}

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
        val description = transferableContent.clipMetadata.clipDescription
        // Refusing here, not after the copy, lets a keyboard fall back to a type the composer takes.
        val declared = RECEIVED_IMAGE_MIME_TYPES.firstOrNull { description.hasMimeType(it) }
            ?: return transferableContent
        val uris = mutableListOf<Uri>()
        // getType is a call into the source app's provider, so each item's type is resolved off the main thread.
        val remaining = transferableContent.consume { item -> item.uri?.also { uris += it } != null }
        if (uris.isNotEmpty()) {
            inFlight += transferableContent
            scope.launch {
                val copies = try {
                    withContext(Dispatchers.IO) {
                        uris.mapNotNull { uri -> imageType(uri, declared)?.let { copyToCache(context, uri, it) } }
                    }
                } finally {
                    inFlight -= transferableContent
                }
                if (copies.isNotEmpty()) onImages(copies)
            }
        }
        return remaining
    }

    /** [uri]'s type if the composer takes it; [declared], the clip's, when the source can't say. */
    private fun imageType(uri: Uri, declared: String): String? {
        val resolved = try {
            context.contentResolver.getType(uri)
        } catch (e: SecurityException) {
            Logger.w(e) { "Received image: type lookup refused" }
            null
        }
        return if (resolved == null) declared else resolved.takeIf { it in RECEIVED_IMAGE_MIME_TYPES }
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

/** Image types the composer takes, no wildcards: a resolved type must equal one, and it names the copy's extension. */
private val RECEIVED_IMAGE_MIME_TYPES = arrayOf("image/png", "image/gif", "image/webp", "image/jpeg")

/** Cleared on logout with the other session caches (`CACHE_SUBDIRECTORIES`). */
private const val RECEIVED_IMAGES_DIR = "keyboard_images"
