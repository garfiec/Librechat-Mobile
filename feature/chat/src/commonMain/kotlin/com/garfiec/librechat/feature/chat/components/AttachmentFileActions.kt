package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import co.touchlab.kermit.Logger
import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.ui.media.FileAction
import com.garfiec.librechat.core.ui.media.rememberOpenFileIn
import com.garfiec.librechat.core.ui.media.rememberSaveFile
import com.garfiec.librechat.core.ui.media.rememberShareFile
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.attachment_fallback
import com.garfiec.librechat.feature.chat.resources.file_download_failed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * Opens the file-actions sheet (Open in…, Save, Share) for a file no viewer opens. Provided by
 * [ChatRoot], which hosts the sheet so it outlives the chip that asked for it.
 */
val LocalOpenFileActions = staticCompositionLocalOf<(Attachment) -> Unit> {
    { attachment -> Logger.w { "LocalOpenFileActions not provided; dropping ${attachment.filename}" } }
}

/**
 * Actions that outlive the artifact sheet, provided by the screen that hosts it: the save picker's
 * result can land after the sheet closed or the Activity was recreated, and a download in flight
 * shouldn't end with it. Null where the viewer is itself a screen.
 */
internal val LocalArtifactFileActions = staticCompositionLocalOf<AttachmentFileActions?> { null }

/**
 * Runs a [FileAction] on a generated file's **original** bytes: downloads them through
 * [LocalAttachmentDownloader], then opens, saves or shares them. Never the bytes an image loader
 * decoded, which iOS re-encodes as PNG. One action per file at a time, tracked by [busyAction]. A
 * download that fails says so through [LocalFileNotice].
 */
@Stable
internal class AttachmentFileActions(
    private val download: () -> suspend (Attachment) -> ByteArray?,
    private val openIn: () -> (ByteArray, String, String?) -> Unit,
    private val save: () -> (ByteArray, String, String?) -> Unit,
    private val share: () -> (ByteArray, String, String?) -> Unit,
    private val notice: () -> (message: String) -> Unit,
    private val scope: CoroutineScope,
    private val fallbackName: String,
    private val downloadFailed: suspend (name: String) -> String = ::downloadFailedNotice,
) {
    private val inFlight = mutableStateMapOf<String, FileAction>()

    fun busyAction(attachment: Attachment): FileAction? = downloadKey(attachment)?.let { inFlight[it] }

    /** [onFinished] runs once the action is handed off or its download failed, so a sheet that
     *  asked for it can close and leave the failure notice in view. */
    fun run(attachment: Attachment, action: FileAction, onFinished: () -> Unit = {}) {
        val key = downloadKey(attachment) ?: return
        if (key in inFlight) return
        inFlight[key] = action
        scope.launch {
            try {
                val name = fileDisplayName(attachment, fallbackName)
                val bytes = download()(attachment)
                if (bytes == null) {
                    onFinished()
                    notice()(downloadFailed(name))
                    return@launch
                }
                when (action) {
                    FileAction.OPEN_IN -> openIn()(bytes, name, attachment.type)
                    FileAction.SAVE -> save()(bytes, name, attachment.type)
                    FileAction.SHARE -> share()(bytes, name, attachment.type)
                }
                onFinished()
            } finally {
                inFlight -= key
            }
        }
    }
}

internal suspend fun downloadFailedNotice(name: String): String = getString(Res.string.file_download_failed, name)

internal fun fileDisplayName(attachment: Attachment, fallbackName: String): String =
    attachmentName(attachment)?.let(::displayFilename) ?: fallbackName

@Composable
internal fun rememberAttachmentFileActions(): AttachmentFileActions {
    val download by rememberUpdatedState(LocalAttachmentDownloader.current)
    val openIn by rememberUpdatedState(rememberOpenFileIn())
    val save by rememberUpdatedState(rememberSaveFile())
    val share by rememberUpdatedState(rememberShareFile())
    val notice by rememberUpdatedState(LocalFileNotice.current)
    val scope = rememberCoroutineScope()
    val fallbackName = stringResource(Res.string.attachment_fallback)
    return remember(scope, fallbackName) {
        AttachmentFileActions(
            download = { download },
            openIn = { openIn },
            save = { save },
            share = { share },
            notice = { notice },
            scope = scope,
            fallbackName = fallbackName,
        )
    }
}
