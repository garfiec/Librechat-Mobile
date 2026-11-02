package com.garfiec.librechat.feature.chat.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.garfiec.librechat.core.model.Attachment
import com.garfiec.librechat.core.ui.components.topbar.CoversNativeBars

/**
 * Provides [LocalOpenPdf] to [content] and hosts the [PdfPreviewOverlay] it opens, above
 * [content]. Hosted at the screen root rather than by the tapped row, so the row can scroll away
 * without dismissing the viewer. The open request is saveable so the preview survives rotation;
 * the overlay re-downloads through [onDownload].
 */
@Composable
internal fun PdfOverlayHost(
    onDownload: suspend (Attachment) -> ByteArray?,
    content: @Composable () -> Unit,
) {
    var pdfRequest by rememberSaveable(stateSaver = PdfRequestSaver) { mutableStateOf<PdfRequest?>(null) }
    val openPdf = remember { { fileId: String, filename: String -> pdfRequest = PdfRequest(fileId, filename) } }

    // Drawn in the Compose canvas, under a native glass bar.
    CoversNativeBars(active = pdfRequest != null)

    CompositionLocalProvider(LocalOpenPdf provides openPdf, content = content)

    pdfRequest?.let { req ->
        PdfPreviewOverlay(
            fileId = req.fileId,
            filename = req.filename,
            onDownload = { fileId -> onDownload(Attachment(fileId = fileId, filename = req.filename)) },
            onDismiss = { pdfRequest = null },
        )
    }
}

private data class PdfRequest(val fileId: String, val filename: String)

/** Saves [PdfRequest] across configuration changes as a `[fileId, filename]` pair (empty = null). */
private val PdfRequestSaver = listSaver<PdfRequest?, String>(
    save = { req -> req?.let { listOf(it.fileId, it.filename) } ?: emptyList() },
    restore = { list -> list.takeIf { it.size == 2 }?.let { PdfRequest(it[0], it[1]) } },
)
