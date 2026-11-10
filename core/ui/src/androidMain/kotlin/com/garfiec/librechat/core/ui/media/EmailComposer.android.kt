package com.garfiec.librechat.core.ui.media

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Must match the `report_attachments` cache-path in the app's `file_provider_paths.xml`. */
private const val REPORT_ATTACHMENTS_DIR = "report_attachments"

@Composable
actual fun rememberEmailComposer(): (draft: EmailDraft, onResult: (EmailComposeResult) -> Unit) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(context, scope) {
        { draft, onResult -> scope.launch { onResult(composeEmail(context, draft)) } }
    }
}

private suspend fun composeEmail(context: Context, draft: EmailDraft): EmailComposeResult {
    val uri = draft.attachment?.let { writeReportAttachmentOrToast(context, it) ?: return EmailComposeResult.Cancelled }
    return try {
        context.startActivity(buildEmailIntent(draft, uri))
        EmailComposeResult.Opened
    } catch (_: ActivityNotFoundException) {
        EmailComposeResult.NoMailApp
    }
}

/** The selector limits the send to apps that take `mailto:`, while the send itself carries the file. */
internal fun buildEmailIntent(draft: EmailDraft, attachmentUri: Uri?): Intent =
    Intent(Intent.ACTION_SEND).apply {
        type = draft.attachment?.mimeType ?: "text/plain"
        putExtra(Intent.EXTRA_EMAIL, arrayOf(draft.to))
        putExtra(Intent.EXTRA_SUBJECT, draft.subject)
        putExtra(Intent.EXTRA_TEXT, draft.body)
        if (attachmentUri != null) grantStream(attachmentUri)
        selector = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
    }

internal fun Intent.grantStream(uri: Uri) {
    putExtra(Intent.EXTRA_STREAM, uri)
    clipData = ClipData.newRawUri(null, uri)
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}

/**
 * Writes [attachment] alone into its own cache folder (not `shared_files`, whose sweep would delete
 * it while a mail app may still read it) and returns its content URI, or toasts and returns null.
 */
internal suspend fun writeReportAttachmentOrToast(context: Context, attachment: EmailAttachment): Uri? = try {
    withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, REPORT_ATTACHMENTS_DIR)
        dir.listFiles()?.forEach { it.delete() }
        dir.mkdirs()
        val file = File(dir, sanitizeFilename(attachment.fileName)).apply { writeBytes(attachment.bytes) }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
} catch (e: IOException) {
    Toast.makeText(context, e.localizedMessage.orEmpty(), Toast.LENGTH_SHORT).show()
    null
}
