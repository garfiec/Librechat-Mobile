package com.garfiec.librechat.feature.files.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.garfiec.librechat.feature.files.FilePreviewDisplayData
import com.garfiec.librechat.feature.files.screen.PdfPreviewFallback

@Composable
actual fun PdfPreview(
    file: FilePreviewDisplayData,
    onDownloadFile: (suspend (fileId: String, userId: String?) -> ByteArray?)?,
    modifier: Modifier,
) {
    // iOS PDF preview — placeholder for now.
    // A proper implementation would use PDFKit via UIKitView.
    PdfPreviewFallback(file = file, modifier = modifier)
}
