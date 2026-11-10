package com.garfiec.librechat.core.ui.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.garfiec.librechat.core.ui.platform.currentTopmostViewController
import com.garfiec.librechat.core.ui.platform.presentSheet
import kotlinx.coroutines.launch
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController

@Composable
actual fun rememberShareText(): (text: String, subject: String?) -> Unit = ::shareText

@Suppress("UnusedParameter") // subject: UIKit takes it only through a UIActivityItemSource
internal fun shareText(text: String, subject: String? = null) {
    val viewController = currentTopmostViewController() ?: return
    val activityVC = UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
    presentSheet(activityVC, from = viewController)
}

@Composable
actual fun rememberShareTextWithFile(): (text: String, subject: String?, attachment: EmailAttachment) -> Unit {
    val scope = rememberCoroutineScope()
    return remember(scope) {
        { text, _, attachment ->
            scope.launch {
                val path = writeReportAttachment(attachment) ?: return@launch
                val viewController = currentTopmostViewController() ?: return@launch
                val items = listOf(text, NSURL.fileURLWithPath(path))
                presentSheet(UIActivityViewController(activityItems = items, applicationActivities = null), viewController)
            }
        }
    }
}
