package com.garfiec.librechat.core.ui.media

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

@Composable
actual fun rememberShareText(): (text: String, subject: String?) -> Unit {
    val context = LocalContext.current
    return remember(context) { { text, subject -> shareText(context, text, subject) } }
}

internal fun shareText(context: Context, text: String, subject: String? = null) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        putExtra(Intent.EXTRA_TEXT, text)
        if (subject != null) putExtra(Intent.EXTRA_SUBJECT, subject)
        type = "text/plain"
    }
    context.startActivity(Intent.createChooser(sendIntent, null))
}

@Composable
actual fun rememberShareTextWithFile(): (text: String, subject: String?, attachment: EmailAttachment?) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(context, scope) {
        { text, subject, attachment ->
            if (attachment == null) {
                shareText(context, text, subject)
            } else {
                scope.launch {
                    val uri = writeMailAttachmentOrToast(context, attachment) ?: return@launch
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = attachment.mimeType
                        putExtra(Intent.EXTRA_TEXT, text)
                        if (subject != null) putExtra(Intent.EXTRA_SUBJECT, subject)
                        grantStream(uri)
                    }
                    context.startActivity(Intent.createChooser(sendIntent, null))
                }
            }
        }
    }
}
