package com.garfiec.librechat.core.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.garfiec.librechat.core.ui.media.toNSData
import com.garfiec.librechat.core.ui.resources.Res
import com.garfiec.librechat.core.ui.resources.saver_share_sheet_failed
import com.garfiec.librechat.core.ui.resources.saver_write_failed
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController

/**
 * iOS saver. Writes [content] to a temp file under [NSTemporaryDirectory], then
 * presents a `UIActivityViewController` share sheet from the top-most view controller so the user
 * can Save to Files / AirDrop / Mail the export.
 *
 * cinterop notes (see project iOS gotchas + `core:logging` LogFile.ios.kt):
 * - `NSData` comes from the shared `ByteArray.toNSData()` (empty-array guard included).
 * - No `String as NSString` casts.
 * - The presenting VC comes from the shared [currentTopmostViewController] (foreground-active scene).
 * - Presentation + iPad popover anchoring go through the shared [presentSheet] helper.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@Composable
actual fun TextFileSaver(
    triggerFileName: String?,
    content: String?,
    onComplete: (success: Boolean, message: String?) -> Unit,
    onReset: () -> Unit,
    @Suppress("UNUSED_PARAMETER") mimeType: String,
) {
    val currentOnComplete by rememberUpdatedState(onComplete)
    val currentOnReset by rememberUpdatedState(onReset)
    val shareSheetFailedMessage = stringResource(Res.string.saver_share_sheet_failed)
    val writeFailedMessage = stringResource(Res.string.saver_write_failed)
    LaunchedEffect(triggerFileName) {
        val fileName = triggerFileName
        val text = content
        if (fileName == null || text == null) return@LaunchedEffect

        val rootVc = currentTopmostViewController()
        if (rootVc == null) {
            currentOnComplete(false, shareSheetFailedMessage)
            currentOnReset()
            return@LaunchedEffect
        }

        val tempPath = NSTemporaryDirectory() + fileName
        // The whole log buffer can run to megabytes, so encode and write it off the main thread.
        val wrote = withContext(Dispatchers.Default) {
            text.encodeToByteArray().toNSData().writeToFile(tempPath, atomically = true)
        }
        if (!wrote) {
            currentOnComplete(false, writeFailedMessage)
            currentOnReset()
            return@LaunchedEffect
        }

        val url = NSURL.fileURLWithPath(tempPath)
        val activityVc = UIActivityViewController(
            activityItems = listOf(url),
            applicationActivities = null,
        )

        // Report the outcome from the share-sheet result (not from presentation): the temp file is
        // cleaned up and success is only signalled if the user actually completed the share. A cancel
        // resets without claiming success — matching the Android SAF actual. A failed activity
        // carries an error, which must reach the caller as a message: (false, null) means cancelled.
        activityVc.completionWithItemsHandler = { _, completed, _, activityError ->
            runCatching { NSFileManager.defaultManager.removeItemAtPath(tempPath, null) }
            val failure = if (completed) null else activityError?.localizedDescription
            currentOnComplete(completed, failure)
            currentOnReset()
        }

        presentSheet(activityVc, from = rootVc)
    }
}
