package com.garfiec.librechat.core.ui.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.garfiec.librechat.core.ui.platform.currentTopmostViewController
import com.garfiec.librechat.core.ui.platform.presentSheet
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIDocumentInteractionController
import platform.UIKit.UIDocumentPickerViewController

/**
 * The open "Open in…" controller. UIKit holds it only weakly while its menu is up, so without a
 * strong reference here the menu is dismissed as soon as it appears. Replaced by the next one.
 */
private var openInController: UIDocumentInteractionController? = null

@Composable
actual fun rememberOpenFileIn(): (bytes: ByteArray, filename: String, mime: String?) -> Unit {
    val scope = rememberCoroutineScope()
    return remember(scope) {
        { bytes, filename, mime -> scope.launch { openIn(bytes, filename, mime) } }
    }
}

@Composable
actual fun rememberSaveFile(): (bytes: ByteArray, filename: String, mime: String?) -> Unit {
    val scope = rememberCoroutineScope()
    return remember(scope) {
        { bytes, filename, mime -> scope.launch { exportToFiles(bytes, filename, mime) } }
    }
}

/** Presents the "Open in…" menu; with no app able to open the type, the share sheet instead. */
@OptIn(ExperimentalForeignApi::class)
private suspend fun openIn(bytes: ByteArray, filename: String, mime: String?) {
    val path = writeTempFile(bytes, filename, mime) ?: return
    withContext(Dispatchers.Main) {
        val from = currentTopmostViewController() ?: return@withContext
        val view = from.view ?: return@withContext
        val url = NSURL.fileURLWithPath(path)
        val controller = UIDocumentInteractionController.interactionControllerWithURL(url)
        openInController = controller
        val rect = view.bounds.useContents { CGRectMake(size.width / 2.0, size.height / 2.0, 0.0, 0.0) }
        if (!controller.presentOpenInMenuFromRect(rect, inView = view, animated = true)) {
            openInController = null
            presentSheet(UIActivityViewController(activityItems = listOf(url), applicationActivities = null), from)
        }
    }
}

/** Exports a copy into Files, wherever the user picks. */
private suspend fun exportToFiles(bytes: ByteArray, filename: String, mime: String?) {
    val path = writeTempFile(bytes, filename, mime) ?: return
    withContext(Dispatchers.Main) {
        val from = currentTopmostViewController() ?: return@withContext
        val picker = UIDocumentPickerViewController(
            forExportingURLs = listOf(NSURL.fileURLWithPath(path)),
            asCopy = true,
        )
        presentSheet(picker, from)
    }
}
