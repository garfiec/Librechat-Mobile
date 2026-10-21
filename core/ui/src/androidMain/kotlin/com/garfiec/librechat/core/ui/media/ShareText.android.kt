package com.garfiec.librechat.core.ui.media

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

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
