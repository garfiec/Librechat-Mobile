package com.garfiec.librechat.feature.conversations.platform

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

@Composable
actual fun rememberProjectFilePicker(onPick: (List<PickedProjectFile>) -> Unit): ProjectFilePicker {
    val resolver = LocalContext.current.applicationContext.contentResolver
    val callback = rememberUpdatedState(onPick)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) callback.value(uris.map { it.toPicked(resolver) })
    }
    return remember(launcher) { ProjectFilePicker { launcher.launch(arrayOf("*/*")) } }
}

actual class ProjectFilePicker(private val launchAction: () -> Unit) {
    actual fun launch() = launchAction()
}

private fun Uri.toPicked(resolver: ContentResolver): PickedProjectFile {
    val name = displayName(resolver) ?: lastPathSegment ?: "file"
    val mime = resolver.getType(this) ?: FALLBACK_MIME_TYPE
    return PickedProjectFile(name = name, mimeType = mime) {
        withContext(Dispatchers.IO) {
            try {
                resolver.openInputStream(this@toPicked)?.use { it.readBytes() }
            } catch (e: IOException) {
                Logger.w(e) { "Couldn't read a picked project file" }
                null
            } catch (e: SecurityException) {
                Logger.w(e) { "Lost access to a picked project file" }
                null
            } catch (e: OutOfMemoryError) {
                Logger.w(e) { "A picked project file is too large to hold" }
                null
            }
        }
    }
}

private fun Uri.displayName(resolver: ContentResolver): String? = try {
    resolver.query(this, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
} catch (e: SecurityException) {
    Logger.w(e) { "Couldn't read a picked file's name" }
    null
}
