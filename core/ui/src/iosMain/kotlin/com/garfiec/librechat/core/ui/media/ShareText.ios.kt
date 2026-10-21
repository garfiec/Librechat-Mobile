package com.garfiec.librechat.core.ui.media

import androidx.compose.runtime.Composable
import com.garfiec.librechat.core.ui.platform.currentTopmostViewController
import com.garfiec.librechat.core.ui.platform.presentSheet
import platform.UIKit.UIActivityViewController

@Composable
actual fun rememberShareText(): (text: String, subject: String?) -> Unit = ::shareText

@Suppress("UnusedParameter") // subject: UIKit takes it only through a UIActivityItemSource
internal fun shareText(text: String, subject: String? = null) {
    val viewController = currentTopmostViewController() ?: return
    val activityVC = UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
    presentSheet(activityVC, from = viewController)
}
