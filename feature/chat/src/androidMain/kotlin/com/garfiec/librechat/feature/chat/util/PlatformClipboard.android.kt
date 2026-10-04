package com.garfiec.librechat.feature.chat.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import org.koin.core.context.GlobalContext

@Suppress("NoGlobalContextAccess") // top-level actual with no injection point for the app Context
actual fun copyToClipboard(text: String, label: String) {
    val context = GlobalContext.get().get<Context>()
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}
