package com.garfiec.librechat.feature.chat.components

import android.content.Context
import android.net.Uri
import android.view.inputmethod.EditorInfo
import android.webkit.MimeTypeMap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Lets the keyboard insert images into the text field inside [content]: stickers, Emoji Kitchen,
 * GIFs and Gboard's copied-image chip. The value-based text field declares no content MIME types and
 * refuses `commitContent`, so this declares them on its [EditorInfo] and takes the commit itself.
 *
 * The keyboard's read grant on its URI lasts only until the permission is released, while an
 * attachment is read again later (routing sheet, chip thumbnail, retry), so each image is copied
 * into the cache before [onImages] sees it. [scope] must outlive the composer.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun KeyboardImageInput(
    enabled: Boolean,
    onImages: (List<Any>) -> Unit,
    scope: CoroutineScope,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val currentEnabled by rememberUpdatedState(enabled)
    val currentOnImages by rememberUpdatedState(onImages)
    val interceptor = remember(context, scope) {
        fun receive(info: InputContentInfoCompat, flags: Int): Boolean {
            val mimeType = KEYBOARD_IMAGE_MIME_TYPES.firstOrNull { info.description.hasMimeType(it) }
            if (!currentEnabled || mimeType == null) return false
            if (flags and InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION != 0) {
                try {
                    info.requestPermission()
                } catch (e: SecurityException) {
                    Logger.w(e) { "Keyboard image: read permission refused" }
                    return false
                }
            }
            scope.launch {
                val copy = try {
                    withContext(Dispatchers.IO) { copyToCache(context, info.contentUri, mimeType) }
                } finally {
                    info.releasePermission()
                }
                if (copy != null) currentOnImages(listOf(copy))
            }
            return true
        }

        PlatformTextInputInterceptor { request, nextHandler ->
            nextHandler.startInputMethod(
                PlatformTextInputMethodRequest { outAttributes ->
                    val connection = request.createInputConnection(outAttributes)
                    if (!currentEnabled) return@PlatformTextInputMethodRequest connection
                    EditorInfoCompat.setContentMimeTypes(outAttributes, KEYBOARD_IMAGE_MIME_TYPES)
                    InputConnectionCompat.createWrapper(connection, outAttributes) { info, flags, _ ->
                        receive(info, flags)
                    }
                },
            )
        }
    }
    InterceptPlatformTextInput(interceptor, content)
}

private fun copyToCache(context: Context, source: Uri, mimeType: String): Uri? {
    val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "png"
    val dir = File(context.cacheDir, KEYBOARD_IMAGES_DIR).apply { mkdirs() }
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
        Logger.w(e) { "Keyboard image: copy failed" }
        file?.delete()
        null
    } catch (e: SecurityException) {
        Logger.w(e) { "Keyboard image: read refused" }
        file?.delete()
        null
    }
}

/** Image types keyboards commit, each named exactly: keyboards match these against what they offer. */
private val KEYBOARD_IMAGE_MIME_TYPES = arrayOf("image/png", "image/gif", "image/webp", "image/jpeg")

/** Cleared on logout with the other session caches (`CACHE_SUBDIRECTORIES`). */
private const val KEYBOARD_IMAGES_DIR = "keyboard_images"
