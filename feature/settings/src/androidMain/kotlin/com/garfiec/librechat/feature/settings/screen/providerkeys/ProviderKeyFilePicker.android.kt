package com.garfiec.librechat.feature.settings.screen.providerkeys

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
actual fun rememberProviderKeyFilePicker(
    onFileRead: (jsonContents: String?) -> Unit,
): (() -> Unit)? {
    val context = LocalContext.current
    // Multi-MB service-account JSONs would jank the activity-result callback if read
    // synchronously on the main thread. Use a Composable-scoped coroutine to dispatch the
    // openInputStream + readBytes off-main; deliver results via the same `onFileRead` shape.
    val ioScope = rememberCoroutineScope()
    val resolver = context.contentResolver
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        // A cancelled pick is not a failed read: nothing to report.
        if (uri == null) return@rememberLauncherForActivityResult
        ioScope.launch {
            val contents = withContext(Dispatchers.IO) {
                runCatching {
                    resolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                }.getOrNull()
            }
            onFileRead(contents)
        }
    }
    return remember(launcher) { { launcher.launch(SERVICE_KEY_MIME_TYPES) } }
}

/**
 * A downloaded service-account key often isn't typed `application/json`: Drive and some file
 * managers hand it over as `application/octet-stream` or `text/plain`, and a JSON-only filter
 * greys it out. The validator decides whether the contents are a key.
 */
private val SERVICE_KEY_MIME_TYPES = arrayOf("application/json", "application/octet-stream", "text/plain")
