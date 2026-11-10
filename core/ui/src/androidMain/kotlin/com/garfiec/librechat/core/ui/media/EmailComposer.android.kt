package com.garfiec.librechat.core.ui.media

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
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

/** Must match the `mail_attachments` cache-path in the app's `file_provider_paths.xml`. */
private const val MAIL_ATTACHMENTS_DIR = "mail_attachments"

@Composable
actual fun rememberEmailComposer(): (draft: EmailDraft, onResult: (EmailComposeResult) -> Unit) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(context, scope) {
        { draft, onResult -> scope.launch { onResult(composeEmail(context, draft)) } }
    }
}

/**
 * Mail apps are the ones that take `mailto:`; each gets an `ACTION_SEND`, which carries the file,
 * aimed at its `mailto:` activity when that activity takes the send too (so Gmail opens its composer,
 * not a picker that also offers Chat). A `mailto:` selector on the send doesn't work: since
 * Android 13 the send must also match the filters of the activity the selector picked.
 */
private suspend fun composeEmail(context: Context, draft: EmailDraft): EmailComposeResult {
    val mailActivities = mailActivities(context)
    if (mailActivities.isEmpty()) return EmailComposeResult.NoMailApp
    val uri = draft.attachment?.let { writeMailAttachmentOrToast(context, it) ?: return EmailComposeResult.Cancelled }
    val sends = mailActivities.map { mail -> mailSend(context, buildEmailIntent(draft, uri), mail) }
    val launch = sends.singleOrNull()
        ?: Intent.createChooser(sends.first(), null).putExtra(Intent.EXTRA_INITIAL_INTENTS, sends.drop(1).toTypedArray())
    return try {
        context.startActivity(launch)
        EmailComposeResult.Opened
    } catch (_: ActivityNotFoundException) {
        EmailComposeResult.NoMailApp
    }
}

/** One `mailto:` activity per app. Needs the `SENDTO mailto:` entry in `<queries>` (feature/settings). */
private fun mailActivities(context: Context): List<ActivityInfo> =
    context.packageManager
        .queryIntentActivities(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")), 0)
        .map { it.activityInfo }
        .distinctBy { it.packageName }

private fun mailSend(context: Context, send: Intent, mail: ActivityInfo): Intent {
    send.setPackage(mail.packageName)
    val takesSend = context.packageManager.queryIntentActivities(send, 0).any { it.activityInfo.name == mail.name }
    return if (takesSend) send.setClassName(mail.packageName, mail.name) else send
}

internal fun buildEmailIntent(draft: EmailDraft, attachmentUri: Uri?): Intent =
    Intent(Intent.ACTION_SEND).apply {
        type = draft.attachment?.mimeType ?: "text/plain"
        putExtra(Intent.EXTRA_EMAIL, arrayOf(draft.to))
        putExtra(Intent.EXTRA_SUBJECT, draft.subject)
        putExtra(Intent.EXTRA_TEXT, draft.body)
        if (attachmentUri != null) grantStream(attachmentUri)
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
internal suspend fun writeMailAttachmentOrToast(context: Context, attachment: EmailAttachment): Uri? = try {
    withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, MAIL_ATTACHMENTS_DIR)
        dir.listFiles()?.forEach { it.delete() }
        dir.mkdirs()
        val file = File(dir, sanitizeFilename(attachment.fileName)).apply { writeBytes(attachment.bytes) }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
} catch (e: IOException) {
    Toast.makeText(context, e.localizedMessage.orEmpty(), Toast.LENGTH_SHORT).show()
    null
}
