package com.garfiec.librechat.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * True inside a dialog or M3 sheet window. Chrome that lives in the main window (native bars, portalled
 * overlays) would land beneath it, so inside one they fall back to Compose / M3.
 */
internal val LocalInSeparateWindow = staticCompositionLocalOf { false }

@Composable
internal fun InSeparateWindow(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalInSeparateWindow provides true, content = content)
}
