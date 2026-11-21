package com.garfiec.librechat.feature.files.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.garfiec.librechat.core.model.ui.UiStyle
import com.garfiec.librechat.core.ui.preview.PreviewNarrowScreens
import com.garfiec.librechat.core.ui.theme.LibreChatTheme
import com.garfiec.librechat.feature.files.FilePreviewDisplayData

// The three cards a preview falls back to: a type with no viewer, and a text file or a PDF whose
// download failed. A phone on its side leaves them about 300dp.

@PreviewNarrowScreens
@Preview(name = "landscape · 640x360", widthDp = 640, heightDp = 360, showBackground = true)
@Composable
private fun FileInfoCardPreview() = PreviewSurface(UiStyle.MATERIAL) { FilePreviewHost(previewFile("application/zip")) }

@PreviewNarrowScreens
@Preview(name = "landscape · 640x360", widthDp = 640, heightDp = 360, showBackground = true)
@Composable
private fun TextErrorFallbackPreview() = PreviewSurface(UiStyle.MATERIAL) { FilePreviewHost(previewFile("text/plain")) }

@PreviewNarrowScreens
@Preview(name = "landscape · 640x360", widthDp = 640, heightDp = 360, showBackground = true)
@Composable
private fun PdfErrorFallbackPreview() = PreviewSurface(UiStyle.MATERIAL) { FilePreviewHost(previewFile("application/pdf")) }

@Composable
private fun PreviewSurface(style: UiStyle, content: @Composable () -> Unit) {
    LibreChatTheme(darkTheme = true, uiStyle = style, content = content)
}

/** The download always fails, so the text and PDF previews land on their error cards. */
@Composable
private fun FilePreviewHost(file: FilePreviewDisplayData) {
    FilePreviewDialog(file = file, onDismiss = {}, onDownloadFile = { _, _ -> null })
}

private fun previewFile(type: String) = FilePreviewDisplayData(
    fileId = "preview",
    filename = "quarterly-report-final-v3.${type.substringAfter('/')}",
    type = type,
    formattedSize = "2.4 MB",
    createdAt = "2026-10-14T09:30:00.000Z",
    source = "local",
    userId = null,
)
