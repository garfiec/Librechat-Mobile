package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.model.files.isExpired
import com.garfiec.librechat.core.model.media.resolveAttachmentUrl
import com.garfiec.librechat.core.ui.media.rememberShareFile
import com.garfiec.librechat.feature.chat.components.artifact.Artifact
import com.garfiec.librechat.feature.chat.components.artifact.LocalOpenArtifact
import com.garfiec.librechat.feature.chat.resources.Res
import com.garfiec.librechat.feature.chat.resources.attachment_fallback
import com.garfiec.librechat.feature.chat.resources.expired_explainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * What a tap on a file a tool produced does, in one place for every surface that lists one (tool
 * call chips, pending-preview rows, workspace changes). [openToolFile] opens it in its viewer when
 * it has one: a text file in the artifact panel, a PDF in the native preview, an image in the
 * media viewer. Anything else — a file whose preview is pending or failed included — opens the
 * file-actions sheet ([LocalOpenFileActions]). [downloadToolFile] always downloads and shares, and
 * says so through [LocalFileNotice] when the download fails. A file the sandbox has dropped opens
 * nothing and fetches nothing: a tap explains why through the same notice.
 *
 * Downloads go through [LocalAttachmentDownloader], which routes a stored file by its id and a
 * code-output fallback by its sandbox path, so in-flight state is keyed on [downloadKey].
 */
@Stable
internal class ToolFileActions(
    private val baseUrl: String,
    private val openArtifact: () -> ((Artifact, List<Artifact>) -> Unit)?,
    private val openPdf: () -> (fileId: String, filename: String) -> Unit,
    private val openMedia: () -> (url: String) -> Unit,
    private val openFileActions: () -> (Attachment) -> Unit,
    private val download: () -> suspend (Attachment) -> ByteArray?,
    private val share: () -> (bytes: ByteArray, filename: String, mime: String?) -> Unit,
    private val notice: () -> (message: String) -> Unit,
    private val scope: CoroutineScope,
    private val fallbackName: String,
    private val expiredExplainer: String,
    private val nowMs: () -> Long = ::currentTimeMs,
    private val downloadFailed: suspend (name: String) -> String = ::downloadFailedNotice,
) {
    private val inFlight = mutableStateSetOf<String>()

    fun isExpired(attachment: Attachment): Boolean = attachment.isExpired(nowMs())

    fun canDownload(attachment: Attachment): Boolean = isDownloadable(attachment, nowMs())

    fun isDownloading(attachment: Attachment): Boolean = downloadKey(attachment)?.let { it in inFlight } == true

    /** False when a tap would do nothing: no viewer for it, and nothing to download it by. An
     *  expired file stays tappable, to say why it can't be opened. */
    fun canOpen(attachment: Attachment): Boolean =
        isExpired(attachment) || canDownload(attachment) || when (classify(attachment)) {
            // Classified only when there is nothing to download: it builds the artifact's content.
            is ToolAttachment.ArtifactContent -> openArtifact() != null
            is ToolAttachment.Pdf -> true
            is ToolAttachment.Image -> resolveAttachmentUrl(attachment, baseUrl) != null
            is ToolAttachment.File, is ToolAttachment.Pending -> false
        }

    fun openToolFile(attachment: Attachment) {
        if (isExpired(attachment)) {
            notice()(expiredExplainer)
            return
        }
        when (val item = classify(attachment)) {
            is ToolAttachment.ArtifactContent -> {
                val open = openArtifact()
                if (open != null) open(item.artifact, listOf(item.artifact)) else showFileActions(attachment)
            }
            is ToolAttachment.Pdf -> item.attachment.fileId?.let { openPdf()(it, displayName(attachment)) }
            is ToolAttachment.Image -> {
                val url = resolveAttachmentUrl(attachment, baseUrl)
                if (url != null) openMedia()(url) else showFileActions(attachment)
            }
            is ToolAttachment.File, is ToolAttachment.Pending -> showFileActions(attachment)
        }
    }

    fun downloadToolFile(attachment: Attachment) {
        if (isExpired(attachment)) {
            notice()(expiredExplainer)
            return
        }
        if (!canDownload(attachment)) return
        val key = downloadKey(attachment) ?: return
        if (key in inFlight) return
        inFlight += key
        scope.launch {
            try {
                val bytes = download()(attachment)
                if (bytes != null) {
                    share()(bytes, displayName(attachment), attachment.type)
                } else {
                    notice()(downloadFailed(displayName(attachment)))
                }
            } finally {
                inFlight -= key
            }
        }
    }

    private fun showFileActions(attachment: Attachment) {
        if (canDownload(attachment)) openFileActions()(attachment)
    }

    private fun classify(attachment: Attachment): ToolAttachment = classifyToolAttachment(attachment, nowMs())

    private fun displayName(attachment: Attachment): String =
        attachmentName(attachment)?.let { displayFilename(it) } ?: fallbackName
}

@Composable
internal fun rememberToolFileActions(baseUrl: String): ToolFileActions {
    val openArtifact by rememberUpdatedState(LocalOpenArtifact.current)
    val openPdf by rememberUpdatedState(LocalOpenPdf.current)
    val openMedia by rememberUpdatedState(LocalChatMediaViewer.current)
    val openFileActions by rememberUpdatedState(LocalOpenFileActions.current)
    val download by rememberUpdatedState(LocalAttachmentDownloader.current)
    val share by rememberUpdatedState(rememberShareFile())
    val notice by rememberUpdatedState(LocalFileNotice.current)
    val scope = rememberCoroutineScope()
    val fallbackName = stringResource(Res.string.attachment_fallback)
    val expiredExplainer = stringResource(Res.string.expired_explainer)
    return remember(baseUrl, scope, fallbackName, expiredExplainer) {
        ToolFileActions(
            baseUrl = baseUrl,
            openArtifact = { openArtifact },
            openPdf = { openPdf },
            openMedia = { openMedia },
            openFileActions = { openFileActions },
            download = { download },
            share = { share },
            notice = { notice },
            scope = scope,
            fallbackName = fallbackName,
            expiredExplainer = expiredExplainer,
        )
    }
}
