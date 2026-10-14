package com.garfiec.librechat.feature.conversations.platform

import androidx.compose.runtime.Composable
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.ui.platform.currentTopmostViewController
import platform.Foundation.NSTimer
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertControllerStyleAlert
import platform.UIKit.UIPasteboard

actual fun copyToClipboard(text: String, label: String) {
    UIPasteboard.generalPasteboard.string = text
}

actual fun showToast(message: String) {
    // iOS doesn't have native Toast — show a brief alert-style overlay.
    Logger.i("Toast") { message }
    // Use UIAlertController as a lightweight toast replacement
    val rootVc = currentTopmostViewController() ?: return
    val alert = UIAlertController.alertControllerWithTitle(
        title = null,
        message = message,
        preferredStyle = UIAlertControllerStyleAlert,
    )
    rootVc.presentViewController(alert, animated = true, completion = null)
    // Auto-dismiss after 1.5 seconds
    NSTimer.scheduledTimerWithTimeInterval(
        interval = 1.5,
        repeats = false,
    ) {
        alert.dismissViewControllerAnimated(true, completion = null)
    }
}

@Composable
internal actual fun RegisterPlatformActions() = Unit
