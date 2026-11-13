package com.garfiec.librechat.shared

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.window.ComposeUIViewController
import com.garfiec.librechat.core.ui.platform.LocalWindowFeatures
import com.garfiec.librechat.shared.app.LibreChatApp
import com.garfiec.librechat.shared.navigation.AppShortcuts
import platform.UIKit.UIViewController

/**
 * CMP entry point for iOS.
 * Called from Swift to create a UIViewController that renders
 * the full Compose Multiplatform UI.
 */
@Suppress("FunctionNaming") // Swift calls it like a type; the CMP iOS entry-point convention
fun MainViewController(): UIViewController = ComposeUIViewController {
    val windowFeatures by IosWindowFeatures.features.collectAsState()
    CompositionLocalProvider(LocalWindowFeatures provides windowFeatures) {
        LibreChatApp()
    }
}

/** The Search shortcut (⌘/Ctrl+Shift+F) for the Swift host's key command; see [AppShortcuts]. Returns whether handled. */
fun openSearchShortcut(): Boolean = AppShortcuts.openSearch?.invoke() ?: false
