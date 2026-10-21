package com.garfiec.librechat.feature.agents.util

import androidx.compose.runtime.Composable

/** The same seam as feature/chat's and feature/settings': feature modules can't share code. */
interface ClipboardWriter {
    fun copy(text: String, label: String)
}

/** The platform [ClipboardWriter], bound to the composition's context rather than a global one. */
@Composable
expect fun rememberClipboardWriter(): ClipboardWriter
