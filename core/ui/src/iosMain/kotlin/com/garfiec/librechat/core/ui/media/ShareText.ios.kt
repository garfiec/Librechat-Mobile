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

internal fun shareText(text: String, subject: String? = null) {
    val viewController = currentTopmostViewController() ?: return
    val items = listOf(withSubject(text, subject))
    val activityVC = UIActivityViewController(activityItems = items, applicationActivities = null)
    presentSheet(activityVC, from = viewController)
}

@Composable
actual fun rememberShareTextWithFile(): (text: String, subject: String?, attachment: EmailAttachment) -> Unit {
    val scope = rememberCoroutineScope()
    return remember(scope) {
        { text, subject, attachment ->
            scope.launch {
                val path = writeReportAttachment(attachment) ?: return@launch
                val viewController = currentTopmostViewController() ?: return@launch
                val items = listOf(withSubject(text, subject), NSURL.fileURLWithPath(path))
                presentSheet(UIActivityViewController(activityItems = items, applicationActivities = null), viewController)
            }
        }
    }
}

// UIKit sets a subject only through a UIActivityItemSource, so it leads the text instead.
private fun withSubject(text: String, subject: String?): String =
    if (subject.isNullOrEmpty()) text else subject + "\n\n" + text
