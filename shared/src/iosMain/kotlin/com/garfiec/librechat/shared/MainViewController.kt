package com.garfiec.librechat.shared

import androidx.compose.ui.window.ComposeUIViewController
import com.garfiec.librechat.shared.app.LibreChatApp
import platform.UIKit.UIViewController

/**
 * CMP entry point for iOS.
 * Called from Swift to create a UIViewController that renders
 * the full Compose Multiplatform UI.
 */
@Suppress("FunctionNaming") // Swift calls it like a type; the CMP iOS entry-point convention
fun MainViewController(): UIViewController = ComposeUIViewController {
    LibreChatApp()
}
