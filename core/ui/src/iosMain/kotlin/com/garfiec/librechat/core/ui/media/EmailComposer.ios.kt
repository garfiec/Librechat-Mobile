package com.garfiec.librechat.core.ui.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.garfiec.librechat.core.ui.platform.currentTopmostViewController
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.writeToFile
import platform.MessageUI.MFMailComposeResult
import platform.MessageUI.MFMailComposeViewController
import platform.MessageUI.MFMailComposeViewControllerDelegateProtocol
import platform.darwin.NSObject

private const val REPORT_ATTACHMENTS_DIR = "report_attachments"

/**
 * The open composer's delegate. UIKit holds `mailComposeDelegate` weakly, so without this the
 * delegate is freed while the composer is up and its result never arrives.
 */
private var mailComposeDelegate: MailComposeDelegate? = null

@Composable
actual fun rememberEmailComposer(): (draft: EmailDraft, onResult: (EmailComposeResult) -> Unit) -> Unit {
    val scope = rememberCoroutineScope()
    return remember(scope) {
        { draft, onResult -> scope.launch { composeEmail(draft, onResult) } }
    }
}

private suspend fun composeEmail(draft: EmailDraft, onResult: (EmailComposeResult) -> Unit) {
    if (!MFMailComposeViewController.canSendMail()) {
        onResult(EmailComposeResult.NoMailApp)
        return
    }
    val attachment = draft.attachment
    val data = attachment?.let { withContext(Dispatchers.Default) { it.bytes.toNSData() } }
    val from = currentTopmostViewController()
    if (from == null) {
        onResult(EmailComposeResult.Cancelled)
        return
    }
    val composer = MFMailComposeViewController()
    composer.setToRecipients(listOf(draft.to))
    composer.setSubject(draft.subject)
    composer.setMessageBody(draft.body, isHTML = false)
    if (attachment != null && data != null) {
        composer.addAttachmentData(data, mimeType = attachment.mimeType, fileName = attachment.fileName)
    }
    val delegate = MailComposeDelegate(onResult)
    mailComposeDelegate = delegate
    composer.mailComposeDelegate = delegate
    from.presentViewController(composer, animated = true, completion = null)
}

private class MailComposeDelegate(
    private val onResult: (EmailComposeResult) -> Unit,
) : NSObject(), MFMailComposeViewControllerDelegateProtocol {
    override fun mailComposeController(
        controller: MFMailComposeViewController,
        didFinishWithResult: MFMailComposeResult,
        error: NSError?,
    ) {
        controller.dismissViewControllerAnimated(true, completion = null)
        if (mailComposeDelegate === this) mailComposeDelegate = null
        onResult(
            if (didFinishWithResult == MFMailComposeResult.MFMailComposeResultSent) {
                EmailComposeResult.Sent
            } else {
                EmailComposeResult.Cancelled
            },
        )
    }
}

/** Writes [attachment] alone into its own temp folder and returns its path, or null on failure. */
@OptIn(ExperimentalForeignApi::class)
internal suspend fun writeReportAttachment(attachment: EmailAttachment): String? = withContext(Dispatchers.Default) {
    val dir = NSTemporaryDirectory() + REPORT_ATTACHMENTS_DIR
    val fm = NSFileManager.defaultManager
    fm.removeItemAtPath(dir, null)
    fm.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
    val path = "$dir/${sanitizeFilename(attachment.fileName)}"
    path.takeIf { attachment.bytes.toNSData().writeToFile(path, atomically = true) }
}
