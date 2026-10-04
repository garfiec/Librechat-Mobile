package com.garfiec.librechat.feature.settings.util

import androidx.compose.runtime.Composable
import platform.UIKit.UIPasteboard

private object IosClipboardWriter : ClipboardWriter {
    override fun copy(text: String, label: String) {
        UIPasteboard.generalPasteboard.string = text
    }
}

@Composable
actual fun rememberClipboardWriter(): ClipboardWriter = IosClipboardWriter
