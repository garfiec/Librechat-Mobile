package com.garfiec.librechat.feature.settings.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

private class AndroidClipboardWriter(private val context: Context) : ClipboardWriter {
    override fun copy(text: String, label: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    }
}

@Composable
actual fun rememberClipboardWriter(): ClipboardWriter {
    val context = LocalContext.current.applicationContext
    return remember(context) { AndroidClipboardWriter(context) }
}
