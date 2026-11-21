package com.garfiec.librechat.core.ui.platform

import androidx.compose.runtime.Composable

interface ClipboardWriter {
    fun copy(text: String, label: String)
}

/** The platform [ClipboardWriter], bound to the composition's context rather than a global one. */
@Composable
expect fun rememberClipboardWriter(): ClipboardWriter
