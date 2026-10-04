package com.garfiec.librechat.feature.conversations.platform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext

private lateinit var appContext: Context

actual fun copyToClipboard(text: String, label: String) {
    val ctx = appContext
    val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

actual fun showToast(message: String) {
    Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
}

@Composable
actual fun FileSaver(
    triggerFileName: String?,
    content: String?,
    onComplete: (success: Boolean, message: String?) -> Unit,
    onReset: () -> Unit,
) {
    val context = LocalContext.current
    // Initialize app context for clipboard/toast
    if (!::appContext.isInitialized) {
        appContext = context.applicationContext
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*"),
    ) { uri ->
        if (uri != null && content != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(content.toByteArray())
                }
                onComplete(true, null)
            } catch (e: Exception) {
                onComplete(false, e.message)
            }
        }
        onReset()
    }

    LaunchedEffect(triggerFileName) {
        if (triggerFileName != null) {
            launcher.launch(triggerFileName)
        }
    }
}
