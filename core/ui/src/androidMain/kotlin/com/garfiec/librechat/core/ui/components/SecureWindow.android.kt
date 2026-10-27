package com.garfiec.librechat.core.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import android.view.WindowManager
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.SecureFlagPolicy

@Composable
internal actual fun SecureWindowEffect(secure: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, secure) {
        // No activity (a preview): nothing to protect.
        val window = view.context.findActivity()?.window
        if (!secure || window == null) return@DisposableEffect onDispose {}
        SecureWindows.acquire(window)
        onDispose { SecureWindows.release(window) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
internal actual fun secureSheetProperties(secure: Boolean): ModalBottomSheetProperties =
    ModalBottomSheetProperties(securePolicy = if (secure) SecureFlagPolicy.SecureOn else SecureFlagPolicy.Inherit)

/** Touched on the main thread only. */
private val SecureWindows = SecureFlagRegistry<Window> { WindowSecureFlag(it) }

private class WindowSecureFlag(private val window: Window) : SecureFlagTarget {
    override var isSecure: Boolean
        get() = window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
        set(value) {
            if (value) {
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
