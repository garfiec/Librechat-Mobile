package com.garfiec.librechat.feature.conversations.platform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private lateinit var appContext: Context

actual fun copyToClipboard(text: String, label: String) {
    val ctx = appContext
    val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

actual fun showToast(message: String) {
    Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
}

@Composable
internal actual fun RegisterPlatformActions() {
    val context = LocalContext.current
    if (!::appContext.isInitialized) {
        appContext = context.applicationContext
    }
}
