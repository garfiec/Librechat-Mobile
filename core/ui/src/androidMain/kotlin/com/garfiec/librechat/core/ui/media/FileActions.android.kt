package com.garfiec.librechat.core.ui.media

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.garfiec.librechat.core.ui.resources.Res
import com.garfiec.librechat.core.ui.resources.file_save_failed
import com.garfiec.librechat.core.ui.resources.file_saved
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import java.io.File
import java.io.IOException
import java.util.UUID

private const val STALE_THRESHOLD_MS = 5 * 60_000L
private const val SAVE_STAGING_DIR = "file-saver"

// Long enough to outlast any picker still open on another screen.
private const val STAGED_STALE_THRESHOLD_MS = 60 * 60_000L

// The picker appends the extension of any other type to a name that lacks it; octet-stream keeps
// the name exactly as given (see TextFileSaver).
private const val SAVE_MIME = "application/octet-stream"

@Suppress("TooGenericExceptionCaught") // user action: any failure becomes a toast
@Composable
actual fun rememberOpenFileIn(): (bytes: ByteArray, filename: String, mime: String?) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(context, scope) {
        { bytes, filename, mime ->
            scope.launch {
                try {
                    val file = withContext(Dispatchers.IO) { writeSharedFile(context, bytes, filename) }
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    val view = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, mime?.takeIf { it.isNotBlank() } ?: SAVE_MIME)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    // The chooser says so itself when no app can open the type.
                    context.startActivity(Intent.createChooser(view, null))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Toast.makeText(context, e.localizedMessage.orEmpty(), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

/**
 * The bytes are staged to a cache file before the picker opens and only its path is kept, in saved
 * state, because the Activity can be recreated while the picker is up and a large file can't go in
 * a Bundle. The copy into the picked document is not cancellable, so leaving the screen mid-write
 * can't leave a truncated file. A save requested while this instance's picker is open is ignored:
 * the launcher's results carry nothing that says which request they answer.
 */
@Composable
actual fun rememberSaveFile(): (bytes: ByteArray, filename: String, mime: String?) -> Unit {
    val appContext = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val saved by rememberUpdatedState(stringResource(Res.string.file_saved))
    val failed by rememberUpdatedState(stringResource(Res.string.file_save_failed))
    var stagedPath by rememberSaveable { mutableStateOf<String?>(null) }
    var staging by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(CreateDocumentAs) { uri ->
        val staged = stagedPath?.let(::File)
        stagedPath = null
        scope.launch {
            val ok: Boolean? = withContext(Dispatchers.IO + NonCancellable) {
                try {
                    when {
                        uri == null -> null
                        staged == null || !staged.exists() -> false
                        else -> copyInto(appContext, staged, uri)
                    }
                } finally {
                    staged?.delete()
                }
            }
            if (ok != null) Toast.makeText(appContext, if (ok) saved else failed, Toast.LENGTH_SHORT).show()
        }
    }

    return remember(appContext, scope, launcher) {
        save@{ bytes, filename, _ ->
            if (staging || stagedPath != null) return@save
            staging = true
            scope.launch {
                val staged = try {
                    runCatching { withContext(Dispatchers.IO) { stage(appContext, bytes) } }.getOrNull()
                } finally {
                    staging = false
                }
                if (staged == null) {
                    Toast.makeText(appContext, failed, Toast.LENGTH_SHORT).show()
                    return@launch
                }
                stagedPath = staged.path
                try {
                    launcher.launch(sanitizeFilename(filename))
                } catch (_: ActivityNotFoundException) {
                    stagedPath = null
                    withContext(Dispatchers.IO + NonCancellable) { staged.delete() }
                    Toast.makeText(appContext, failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

/** `CreateDocument` with the suggested name as input and [SAVE_MIME] as the document type. */
private object CreateDocumentAs : ActivityResultContract<String, Uri?>() {
    override fun createIntent(context: Context, input: String): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(SAVE_MIME)
            .putExtra(Intent.EXTRA_TITLE, input)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? = intent?.data
}

/**
 * A fresh name per save, never the file's (a server-supplied name can contain '/'), so two screens
 * saving at once can't write into each other's staged bytes. Sweeps what abandoned saves left.
 */
private fun stage(context: Context, bytes: ByteArray): File {
    val dir = File(context.cacheDir, SAVE_STAGING_DIR)
    dir.mkdirs()
    sweepStaleFiles(dir, STAGED_STALE_THRESHOLD_MS)
    return File(dir, "pending-${UUID.randomUUID()}").apply { writeBytes(bytes) }
}

@Suppress("TooGenericExceptionCaught") // ContentResolver I/O throws undocumented exceptions; reported, not thrown
private fun copyInto(context: Context, source: File, target: Uri): Boolean = try {
    val output = context.contentResolver.openOutputStream(target) ?: throw IOException("No output stream")
    output.use { out -> source.inputStream().use { it.copyTo(out) } }
    true
} catch (_: Exception) {
    false
}

/** Writes [bytes] under the FileProvider's `shared_files` path, sweeping earlier files first. */
internal fun writeSharedFile(context: Context, bytes: ByteArray, filename: String): File {
    val dir = File(context.cacheDir, "shared_files")
    dir.mkdirs()
    // Sweep prior files safely past any in-flight read, rather than deleting this one on a timer.
    sweepStaleFiles(dir, STALE_THRESHOLD_MS)
    return File(dir, sanitizeFilename(filename)).apply { writeBytes(bytes) }
}
