package com.garfiec.librechat.core.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

private const val KB = 1000.0

actual fun formatFileSize(bytes: Long): String {
    if (bytes < KB) return "$bytes B"
    var value = bytes / KB
    for (unit in listOf("KB", "MB", "GB")) {
        if (value < KB || unit == "GB") {
            val tenths = kotlin.math.round(value * 10).toLong()
            return if (tenths % 10 == 0L) "${tenths / 10} $unit" else "${tenths / 10}.${tenths % 10} $unit"
        }
        value /= KB
    }
    return "$bytes B"
}

@Composable
actual fun rememberReduceMotion(): Boolean = false

@Composable
actual fun TextFileSaver(
    triggerFileName: String?,
    content: String?,
    onComplete: (success: Boolean, message: String?) -> Unit,
    onReset: () -> Unit,
    mimeType: String,
) {
    val currentOnComplete by rememberUpdatedState(onComplete)
    val currentOnReset by rememberUpdatedState(onReset)
    LaunchedEffect(triggerFileName) {
        if (triggerFileName == null) return@LaunchedEffect
        currentOnReset()
        currentOnComplete(false, "Saving files isn't available in the browser")
    }
}

private object BrowserUriOpener : UriOpener {
    override fun open(uri: String): Boolean = windowOpen(uri)
}

@Suppress("UnusedParameter")
private fun windowOpen(uri: String): Boolean = js("window.open(uri, '_blank') !== null")

@Composable
actual fun rememberUriOpener(): UriOpener = BrowserUriOpener

private object BrowserClipboardWriter : ClipboardWriter {
    override fun copy(text: String, label: String) = clipboardWriteText(text)
}

@Suppress("UnusedParameter")
private fun clipboardWriteText(text: String): Unit = js("{ navigator.clipboard.writeText(text).catch(() => {}); }")

@Composable
actual fun rememberClipboardWriter(): ClipboardWriter = BrowserClipboardWriter
