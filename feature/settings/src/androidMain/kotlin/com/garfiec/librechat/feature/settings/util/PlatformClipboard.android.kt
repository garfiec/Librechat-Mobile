package com.garfiec.librechat.feature.settings.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import org.koin.mp.KoinPlatformTools

@Suppress("NoGlobalContextAccess") // top-level actual with no injection point for the app Context
actual fun copyToClipboard(text: String, label: String) {
    val context = KoinPlatformTools.defaultContext().get().get<Context>()
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}
