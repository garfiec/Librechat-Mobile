package com.garfiec.librechat.feature.files.platform

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.garfiec.librechat.feature.files.FilePreviewDisplayData
import com.garfiec.librechat.feature.files.resources.Res
import com.garfiec.librechat.feature.files.resources.pdf_preview_unavailable_ios
import com.garfiec.librechat.feature.files.screen.FileInfoFallback
import org.jetbrains.compose.resources.stringResource

@Composable
actual fun PdfPreview(
    file: FilePreviewDisplayData,
    onDownloadFile: (suspend (fileId: String, userId: String?) -> ByteArray?)?,
    modifier: Modifier,
) {
    // iOS PDF preview — placeholder for now.
    // A proper implementation would use PDFKit via UIKitView.
    FileInfoFallback(
        file = file,
        icon = Icons.Default.PictureAsPdf,
        iconTint = MaterialTheme.colorScheme.primary,
        modifier = modifier,
        showDetails = false,
    ) {
        Text(
            text = stringResource(Res.string.pdf_preview_unavailable_ios),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
