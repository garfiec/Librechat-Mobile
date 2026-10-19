package com.garfiec.librechat.core.ui.platform

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.common.result.suspendRunCatching
import com.garfiec.librechat.core.ui.resources.Res
import com.garfiec.librechat.core.ui.resources.saver_data_lost
import com.garfiec.librechat.core.ui.resources.saver_open_failed
import com.garfiec.librechat.core.ui.resources.saver_prepare_failed
import com.garfiec.librechat.core.ui.resources.saver_write_failed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import java.io.File
import java.io.IOException

private const val STAGING_DIR = "text-file-saver"

// A fixed name, never the caller's: a conversation title can contain '/'.
private const val STAGED_FILE = "pending"

/**
 * Android saver: the Storage Access Framework `CreateDocument` picker.
 *
 * The content is staged to a cache file before the picker opens, and only that file's path is kept,
 * in saved state. The Activity can be recreated while the picker is up (rotation, a fold, process
 * death), which drops the caller's state, and a multi-megabyte log can't go in a Bundle. Staging and
 * the copy into the picked document both run on the IO dispatcher; the copy is not cancellable, so
 * leaving the screen mid-write can't leave a truncated file.
 */
@Composable
actual fun TextFileSaver(
    triggerFileName: String?,
    content: String?,
    onComplete: (success: Boolean, message: String?) -> Unit,
    onReset: () -> Unit,
    mimeType: String,
) {
    val appContext = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val currentOnComplete by rememberUpdatedState(onComplete)
    val currentOnReset by rememberUpdatedState(onReset)
    var stagedPath by rememberSaveable { mutableStateOf<String?>(null) }
    val contract = remember(mimeType) { ActivityResultContracts.CreateDocument(mimeType) }
    // Resolved here so a failure reaches the caller already in the user's language.
    val dataLostMessage = stringResource(Res.string.saver_data_lost)
    val prepareFailedMessage = stringResource(Res.string.saver_prepare_failed)
    val writeFailedMessage = stringResource(Res.string.saver_write_failed)
    val openFailedMessage = stringResource(Res.string.saver_open_failed)

    val launcher = rememberLauncherForActivityResult(contract) { uri ->
        val staged = stagedPath?.let(::File)
        stagedPath = null
        scope.launch {
            val written: Result<Unit>? = withContext(Dispatchers.IO + NonCancellable) {
                try {
                    when {
                        uri == null -> null
                        staged == null || !staged.exists() -> Result.failure(SaverFailure(dataLostMessage))
                        else -> copyInto(appContext, staged, uri, openFailedMessage)
                    }
                } finally {
                    staged?.delete()
                }
            }
            when {
                written == null -> currentOnComplete(false, null)
                written.isSuccess -> currentOnComplete(true, null)
                else -> {
                    val error = written.exceptionOrNull()
                    Logger.w(error) { "Saving a file failed" }
                    currentOnComplete(false, (error as? SaverFailure)?.message ?: writeFailedMessage)
                }
            }
            currentOnReset()
        }
    }

    LaunchedEffect(triggerFileName) {
        val fileName = triggerFileName ?: return@LaunchedEffect
        val text = content
        if (text == null) {
            currentOnComplete(false, dataLostMessage)
            currentOnReset()
            return@LaunchedEffect
        }
        suspendRunCatching { withContext(Dispatchers.IO) { stage(appContext, text) } }
            .onSuccess { file ->
                stagedPath = file.path
                launcher.launch(fileName)
            }
            .onFailure { e ->
                Logger.w(e) { "Staging a file to save failed" }
                currentOnComplete(false, prepareFailedMessage)
                currentOnReset()
            }
    }
}

/** Replaces whatever an abandoned save left behind: only one picker is ever open. */
private fun stage(context: Context, text: String): File {
    val dir = File(context.cacheDir, STAGING_DIR)
    dir.deleteRecursively()
    dir.mkdirs()
    return File(dir, STAGED_FILE).apply { writeText(text) }
}

@Suppress("TooGenericExceptionCaught") // ContentResolver I/O throws undocumented exceptions; reported, not thrown
private fun copyInto(context: Context, source: File, target: Uri, openFailedMessage: String): Result<Unit> = try {
    val output = context.contentResolver.openOutputStream(target)
    if (output == null) {
        Result.failure(SaverFailure(openFailedMessage))
    } else {
        output.use { out -> source.inputStream().use { it.copyTo(out) } }
        Result.success(Unit)
    }
} catch (e: Exception) {
    Result.failure(e)
}

/**
 * A failure this saver words itself. Anything else the copy throws carries the platform's message,
 * which is not in the user's language, so the caller is given the generic write failure instead.
 */
private class SaverFailure(message: String) : IOException(message)
